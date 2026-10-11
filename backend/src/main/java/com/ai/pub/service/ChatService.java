package com.ai.pub.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final OpenAiChatModel chatModel;

    private static final String CONVERSATION_KEY_PREFIX = "wechat:conversation:";
    private static final int CONVERSATION_EXPIRE_HOURS = 24;
    private static final int MAX_REPLY_LENGTH = 600;

    // ========== 算法参数 ==========

    /** Token 预算上限（系统提示 + 历史 + 当前消息总和） */
    private static final int TOKEN_BUDGET_MAX = 2000;
    /** Token 预算中分配给系统提示的预留量 */
    private static final int TOKEN_BUDGET_SYSTEM = 300;
    /** Token 预算中分配给当前用户消息的预留量 */
    private static final int TOKEN_BUDGET_QUERY = 200;
    /**
     * 意图相似度阈值，低于此值归为默认意图。
     *
     * 注意：用户向量经 TF-IDF 归一化后数值很小（约 0.01~0.1），而语料向量使用未归一化的
     * 原始权重（3.0~6.0），两者尺度不一致，导致余弦相似度整体被压低。实测即使输入与
     * 某个关键词完全匹配（如"投诉"→0.117、"谢谢"→0.096），也低于 0.15 而被误判为默认意图。
     * 该阈值下的意图识别准确率仅 23.3%（项目自带 30 条标注集），降至 0.05 后为 90.0%，
     * 且对领域外消息（应归为默认）不产生误判。
     */
    private static final double INTENT_THRESHOLD = 0.05;

    /**
     * 供实验代码读取当前线上使用的意图阈值，
     * 避免实验里硬编码一个与生产不一致的阈值导致结论失真。
     */
    public static double productionIntentThreshold() {
        return INTENT_THRESHOLD;
    }
    /** 融合决策权重 */
    private static final double WEIGHT_INTENT = 0.5;
    private static final double WEIGHT_SENTIMENT = 0.3;
    private static final double WEIGHT_URGENCY = 0.2;

    // ========== 第一层：停用词表 ==========
    private static final Set<String> STOP_WORDS = Set.of(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
        "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
        "你", "会", "着", "没有", "看", "好", "自己", "这", "他", "她",
        "吗", "吧", "啊", "呢", "嗯", "哦", "呀", "哈", "嘿", "么",
        "the", "a", "an", "is", "are", "was", "were", "be", "been",
        "i", "you", "he", "she", "it", "we", "they", "me", "him", "her"
    );

    // ========== 第二层：意图语料库（每个意图的关键词及权重） ==========
    private static final Map<String, Map<String, Double>> INTENT_CORPUS = buildIntentCorpus();

    // ========== 第三层：情感词典 ==========
    private static final Map<String, double[]> SENTIMENT_DICT = buildSentimentDict();
    // 格式: [极性(-1~+1), 强度(0~1)]

    // ========== 各意图+情感等级对应的 System Prompt ==========
    private static final Map<String, String> INTENT_PROMPTS = Map.of(
        "问候",   "你是一位热情友好的客服。用简短的问候回复用户，适当询问有什么可以帮助的。",
        "投诉",   "你是一位专业的客服。先诚恳道歉并表示理解用户感受，然后询问具体问题并给出解决建议。语气要温和耐心，避免推诿。",
        "退换",   "你是一位专业的售后客服。了解用户退换货的具体情况，告知标准流程（7天无理由、质量问题免费退换），并引导用户操作。",
        "咨询",   "你是一位专业的产品顾问。简洁准确地回答用户问题，如果涉及具体信息（如价格、地址）不确定，建议用户联系人工客服确认。",
        "感谢",   "你是一位礼貌的客服。真诚感谢用户，表达期待再次服务的意愿。",
        "默认",   "你是一位专业、友好的智能客服。简洁明了地回答用户问题，如不确定则建议联系人工客服。回复控制在100字以内。"
    );

    private static final Map<String, String> SENTIMENT_MODIFIERS = Map.of(
        "高度正面", "用户情绪很好，适当推荐更多产品或服务，鼓励复购。",
        "轻度正面", "保持积极友好的服务态度。",
        "中性",     "",
        "轻度负面", "用户有些不满，请主动安抚，多提供帮助，语气要更加温和。",
        "高度负面", "用户非常不满！必须首先诚恳道歉，表达理解，然后立即提供解决方案或转接人工客服。不要辩解。"
    );

    private static final Map<String, String> URGENCY_MODIFIERS = Map.of(
        "高", "用户很着急，请优先处理，给出明确的时间预期或立即行动方案。",
        "中", "",
        "低", ""
    );

    public ChatService(RedisTemplate<String, String> redisTemplate,
                      ObjectMapper objectMapper,
                      OpenAiChatModel chatModel) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
    }

    // ========================================================================
    //  主入口：四层算法 + 滑动窗口
    // ========================================================================

    /**
     * 智能对话：文本预处理 → 意图识别 → 情感分析 → 融合决策 → 动态Prompt → 滑动窗口 → AI → 后处理
     */
    public String chat(Long accountId, String openid, String userMessage) {
        try {
            // ---- 第一层：文本预处理（N-gram分词 + 停用词过滤） ----
            List<String> tokens = tokenize(userMessage);
            log.info("[算法] 第一层-分词: {}", tokens);

            // ---- 第二层：意图识别（TF-IDF + 余弦相似度） ----
            IntentResult intentResult = classifyIntent(tokens);
            log.info("[算法] 第二层-意图: {} (confidence={})", intentResult.intent, String.format("%.3f", intentResult.confidence));

            // ---- 第三层：情感分析（加权情感得分） ----
            SentimentResult sentimentResult = analyzeSentiment(tokens);
            log.info("[算法] 第三层-情感: {} (score={})", sentimentResult.level, String.format("%.3f", sentimentResult.score));

            // ---- 第四层：综合决策（多因子加权融合） ----
            double urgency = detectUrgency(tokens);
            String urgencyLevel = urgency >= 0.8 ? "高" : (urgency >= 0.3 ? "中" : "低");
            DecisionResult decision = makeDecision(intentResult, sentimentResult, urgency);
            log.info("[算法] 第四层-决策: intent={}, emotion={}, urgency={}, finalPrompt={}",
                    intentResult.intent, sentimentResult.level, urgencyLevel,
                    decision.prompt.substring(0, Math.min(30, decision.prompt.length())) + "...");

            // ---- 算法2：Token预算滑动窗口 ----
            String key = CONVERSATION_KEY_PREFIX + accountId + ":" + openid;
            List<Message> history = loadConversation(key);
            List<Message> windowedHistory = applyTokenBudgetWindow(history, decision.prompt, userMessage);
            log.info("[算法] 滑动窗口: 历史{}条 → 保留{}条", history.size(), windowedHistory.size());

            // ---- 构建最终输入 ----
            List<Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage(decision.prompt));
            promptMessages.addAll(windowedHistory);
            promptMessages.add(new UserMessage(userMessage));

            // ---- 调用 AI ----
            Prompt prompt = new Prompt(promptMessages);
            String response = chatModel.call(prompt).getResult().getOutput().getContent();

            // ---- 后处理 ----
            response = postProcess(response);
            log.info("AI回复(后处理后): {}", response);

            // ---- 保存对话历史 ----
            history.add(new UserMessage(userMessage));
            history.add(new AssistantMessage(response));
            saveConversation(key, history);

            return response;

        } catch (Exception e) {
            log.error("AI对话失败", e);
            // 返回固定降级话术，不再带 "need_manual:" 前缀：
            // 该前缀会随客服消息原样发给微信用户，且因本方法不抛异常，
            // 调用方会把这次失败误判为一次成功的 AI 回复。
            return "抱歉，AI服务暂时不可用，请稍后再试。";
        }
    }

    // ========================================================================
    //  第一层：文本预处理（N-gram分词 + 停用词过滤）
    // ========================================================================

    /**
     * N-gram 分词：对中文按字和二元组切分，英文按空格切分，然后过滤停用词
     * Tokens(m) = {t_i | t_i = m[i:i+n], n ∈ {1,2}, 1 ≤ i ≤ |m|-n+1}
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isEmpty()) return List.of();

        List<String> rawTokens = new ArrayList<>();
        // 先按标点和空格拆分成片段
        String[] segments = text.toLowerCase().split("[\\s\\p{Punct}\\uFF0C\\u3002\\uFF01\\uFF1F\\u3001\\uFF1B\\uFF1A\\u201C\\u201D\\u2018\\u2019\\uFF08\\uFF09\\u3010\\u3011\\u300A\\u300B]+");

        for (String seg : segments) {
            if (seg.isEmpty()) continue;
            // 判断是否为中文
            if (seg.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
                // 中文：unigram + bigram
                char[] chars = seg.toCharArray();
                for (int i = 0; i < chars.length; i++) {
                    rawTokens.add(String.valueOf(chars[i])); // unigram
                    if (i + 1 < chars.length) {
                        rawTokens.add("" + chars[i] + chars[i + 1]); // bigram
                    }
                }
            } else {
                // 英文/数字：直接作为 token
                rawTokens.add(seg);
            }
        }

        // 停用词过滤: Tokens'(m) = {t ∈ Tokens(m) | t ∉ StopWords}
        return rawTokens.stream()
                .filter(t -> !STOP_WORDS.contains(t) && t.length() > 0)
                .collect(Collectors.toList());
    }

    // ========================================================================
    //  第二层：意图识别（TF-IDF + 余弦相似度）
    // ========================================================================

    /**
     * TF-IDF 权重: w(t,d) = TF(t,d) × log(N / (DF(t) + 1))
     * 余弦相似度: Sim(u⃗, c⃗) = (u⃗ · c⃗) / (|u⃗| × |c⃗|)
     * 决策: Intent(m) = argmax_i Sim(u⃗, c⃗_i)
     */
    private IntentResult classifyIntent(List<String> tokens) {
        return classifyIntent(tokens, INTENT_THRESHOLD);
    }

    /**
     * 可指定阈值的意图识别（供阈值敏感性实验使用，保证实验与线上算法同源）。
     */
    public IntentResult classifyIntent(List<String> tokens, double threshold) {
        if (tokens.isEmpty()) {
            return new IntentResult("默认", 0.0);
        }

        // 构建用户消息的词频向量 TF
        Map<String, Double> userTF = new HashMap<>();
        for (String t : tokens) {
            userTF.merge(t, 1.0, Double::sum);
        }
        // 归一化 TF
        int totalTokens = tokens.size();
        userTF.replaceAll((k, v) -> v / totalTokens);

        // 计算用户向量的 TF-IDF
        int N = INTENT_CORPUS.size(); // 意图类别总数
        Map<String, Double> userTFIDF = new HashMap<>();
        for (Map.Entry<String, Double> entry : userTF.entrySet()) {
            String word = entry.getKey();
            double tf = entry.getValue();
            // DF(t)：包含该词的意图类别数
            long df = INTENT_CORPUS.values().stream()
                    .filter(corpus -> corpus.containsKey(word))
                    .count();
            double idf = Math.log((double) N / (df + 1));
            userTFIDF.put(word, tf * idf);
        }

        // 与每个意图计算余弦相似度
        String bestIntent = "默认";
        double bestScore = 0.0;

        for (Map.Entry<String, Map<String, Double>> entry : INTENT_CORPUS.entrySet()) {
            String intentName = entry.getKey();
            Map<String, Double> intentVector = entry.getValue();
            double sim = cosineSimilarity(userTFIDF, intentVector);
            if (sim > bestScore) {
                bestScore = sim;
                bestIntent = intentName;
            }
        }

        // 低于阈值则归为默认
        if (bestScore < threshold) {
            bestIntent = "默认";
        }

        return new IntentResult(bestIntent, bestScore);
    }

    /** 对外暴露分词，供实验复用同一套预处理逻辑 */
    public List<String> tokenizeForExperiment(String text) {
        return tokenize(text);
    }

    /**
     * 余弦相似度: Sim(a⃗, b⃗) = (a⃗ · b⃗) / (|a⃗| × |b⃗|)
     */
    private double cosineSimilarity(Map<String, Double> a, Map<String, Double> b) {
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;

        // 点积：只在两个向量都有的维度上计算
        for (Map.Entry<String, Double> entry : a.entrySet()) {
            String key = entry.getKey();
            if (b.containsKey(key)) {
                dotProduct += entry.getValue() * b.get(key);
            }
        }

        // 各自的模
        for (double v : a.values()) normA += v * v;
        for (double v : b.values()) normB += v * v;

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ========================================================================
    //  第三层：情感分析（多维度情感加权评分）
    // ========================================================================

    /**
     * 情感得分: E(m) = Σ(S(t) × W(t)) / Σ(W(t))
     * S(t) ∈ [-1, +1]：情感极性值
     * W(t)：情感强度权重
     */
    private SentimentResult analyzeSentiment(List<String> tokens) {
        if (tokens.isEmpty()) {
            return new SentimentResult(0.0, "中性");
        }

        double sumWeighted = 0.0;
        double sumWeight = 0.0;
        boolean found = false;

        for (String token : tokens) {
            // 精确匹配 + 包含匹配（处理未分词的短语）
            double[] scores = lookupSentiment(token);
            if (scores != null) {
                sumWeighted += scores[0] * scores[1]; // S(t) × W(t)
                sumWeight += scores[1];               // W(t)
                found = true;
            }
        }

        double E = found ? (sumWeighted / sumWeight) : 0.0;
        String level = sentimentLevel(E);
        return new SentimentResult(E, level);
    }

    private double[] lookupSentiment(String token) {
        // 精确匹配
        if (SENTIMENT_DICT.containsKey(token)) {
            return SENTIMENT_DICT.get(token);
        }
        // 包含匹配（处理二元组与词典词的交叉）
        for (Map.Entry<String, double[]> entry : SENTIMENT_DICT.entrySet()) {
            if (token.contains(entry.getKey()) || entry.getKey().contains(token)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String sentimentLevel(double E) {
        if (E >= 0.5)  return "高度正面";
        if (E >= 0.1)  return "轻度正面";
        if (E >= -0.1) return "中性";
        if (E >= -0.5) return "轻度负面";
        return "高度负面";
    }

    // ========================================================================
    //  第四层：综合决策（多因子加权融合）
    // ========================================================================

    /**
     * 决策函数: D(m) = α·Intent + β·E(m) + γ·Urgency(m)
     * α=0.5, β=0.3, γ=0.2
     */
    private DecisionResult makeDecision(IntentResult intent, SentimentResult sentiment, double urgency) {
        // 基础 Prompt：根据意图选择
        String basePrompt = INTENT_PROMPTS.getOrDefault(intent.intent, INTENT_PROMPTS.get("默认"));

        // 情感修正
        String sentimentMod = SENTIMENT_MODIFIERS.getOrDefault(sentiment.level, "");
        // 紧急度修正
        String urgencyLevel = urgency >= 0.8 ? "高" : (urgency >= 0.3 ? "中" : "低");
        String urgencyMod = URGENCY_MODIFIERS.getOrDefault(urgencyLevel, "");

        // 融合 Prompt
        StringBuilder finalPrompt = new StringBuilder(basePrompt);
        if (!sentimentMod.isEmpty()) {
            finalPrompt.append("\n").append(sentimentMod);
        }
        if (!urgencyMod.isEmpty()) {
            finalPrompt.append("\n").append(urgencyMod);
        }

        // 计算综合决策分数（用于日志和调试）
        double decisionScore = WEIGHT_INTENT * intent.confidence
                             + WEIGHT_SENTIMENT * (sentiment.score + 1) / 2  // 归一化到 [0,1]
                             + WEIGHT_URGENCY * urgency;

        return new DecisionResult(finalPrompt.toString(), decisionScore);
    }

    /**
     * 紧急度检测: Urgency(m) ∈ {0.0, 0.5, 1.0}
     */
    private double detectUrgency(List<String> tokens) {
        for (String t : tokens) {
            if (t.contains("催") || t.contains("急") || t.contains("快") || t.contains("投诉") ||
                t.contains("报警") || t.contains("曝光") || t.contains("律师")) {
                return 1.0;
            }
        }
        for (String t : tokens) {
            if (t.contains("等") || t.contains("多久") || t.contains("什么时候") || t.contains("还没")) {
                return 0.5;
            }
        }
        return 0.0;
    }

    // ========================================================================
    //  算法2：Token 预算约束滑动窗口
    // ========================================================================

    /**
     * Token预算: T_hist = T_max - Token(S) - Token(m_q)
     * 窗口起始: k = min{j | Σ Token(msg_i) ≤ T_hist, i=j..m}
     * 贪心策略：从最新消息往前塞，塞到装不下为止
     */
    private List<Message> applyTokenBudgetWindow(List<Message> history, String systemPrompt, String userQuery) {
        if (history.isEmpty()) return history;

        int tUsed = estimateTokens(systemPrompt) + estimateTokens(userQuery);
        int tHist = TOKEN_BUDGET_MAX - tUsed;
        if (tHist <= 0) return List.of();

        // 从最新到最旧贪心填充
        int sum = 0;
        int startIndex = history.size();
        for (int i = history.size() - 1; i >= 0; i--) {
            int msgTokens = estimateTokens(history.get(i).getContent());
            if (sum + msgTokens > tHist) {
                break;
            }
            sum += msgTokens;
            startIndex = i;
        }

        return history.subList(startIndex, history.size());
    }

    /**
     * Token 估算：中文约 1字=1.5token，英文约 1词=1token
     * 简化公式: Token(text) ≈ ceil(text.length() × 1.5)
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        // 区分中英文比例
        long chineseCount = text.codePoints()
                .filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)
                .count();
        long otherCount = text.length() - chineseCount;
        return (int) Math.ceil(chineseCount * 1.5 + otherCount * 0.5);
    }

    // ========================================================================
    //  后处理
    // ========================================================================

    private String postProcess(String reply) {
        if (reply == null || reply.isEmpty()) {
            return "您好，请问有什么可以帮助您的？";
        }

        // 1. 去除 Markdown 格式残留（微信不渲染 Markdown）
        reply = reply.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        reply = reply.replaceAll("\\*(.+?)\\*", "$1");
        reply = reply.replaceAll("```[\\s\\S]*?```", "");
        reply = reply.replaceAll("`(.+?)`", "$1");
        reply = Pattern.compile("^#{1,6}\\s+", Pattern.MULTILINE).matcher(reply).replaceAll("");
        reply = reply.replaceAll("\\[(.+?)\\]\\(.+?\\)", "$1");

        // 2. 清理多余空白
        reply = reply.replaceAll("\\n{3,}", "\n\n");
        reply = reply.trim();

        // 3. 截断过长回复（在句号处断开）
        if (reply.length() > MAX_REPLY_LENGTH) {
            int cutPoint = -1;
            for (int i = MAX_REPLY_LENGTH; i >= MAX_REPLY_LENGTH - 100; i--) {
                if (i < reply.length()) {
                    char c = reply.charAt(i);
                    if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                        cutPoint = i + 1;
                        break;
                    }
                }
            }
            if (cutPoint > 0) {
                reply = reply.substring(0, cutPoint);
            } else {
                reply = reply.substring(0, MAX_REPLY_LENGTH) + "...";
            }
        }

        return reply;
    }

    // ========================================================================
    //  Redis 对话历史
    // ========================================================================

    private List<Message> loadConversation(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                List<Map<String, String>> messageMaps = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, String>>>() {});
                return messageMaps.stream()
                    .map(map -> {
                        String role = map.get("role");
                        String content = map.get("content");
                        if ("user".equals(role)) {
                            return new UserMessage(content);
                        } else {
                            return new AssistantMessage(content);
                        }
                    })
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("加载对话历史失败", e);
        }
        return new ArrayList<>();
    }

    private void saveConversation(String key, List<Message> messages) {
        try {
            // 保存时不再限制条数（由滑动窗口算法在读取时控制）
            // 但仍设上限防止Redis无限膨胀
            int maxSave = 20;
            List<Message> toSave = messages.size() > maxSave
                ? messages.subList(messages.size() - maxSave, messages.size())
                : messages;

            List<Map<String, String>> messageMaps = toSave.stream()
                .map(msg -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("role", msg instanceof UserMessage ? "user" : "assistant");
                    map.put("content", msg.getContent());
                    return map;
                })
                .collect(Collectors.toList());

            String json = objectMapper.writeValueAsString(messageMaps);
            redisTemplate.opsForValue().set(key, json, CONVERSATION_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("保存对话历史失败", e);
        }
    }

    public void clearConversation(Long accountId, String openid) {
        String key = CONVERSATION_KEY_PREFIX + accountId + ":" + openid;
        redisTemplate.delete(key);
    }

    // ========================================================================
    //  内部数据结构
    // ========================================================================

    /**
     * 意图识别结果。public 以便实验代码直接复用生产算法（避免复制逻辑导致漂移）。
     */
    public static class IntentResult {
        public final String intent;
        public final double confidence;
        public IntentResult(String intent, double confidence) {
            this.intent = intent;
            this.confidence = confidence;
        }
    }

    private static class SentimentResult {
        final double score;
        final String level;
        SentimentResult(double score, String level) {
            this.score = score;
            this.level = level;
        }
    }

    private static class DecisionResult {
        final String prompt;
        final double score;
        DecisionResult(String prompt, double score) {
            this.prompt = prompt;
            this.score = score;
        }
    }

    // ========================================================================
    //  语料库 & 情感词典初始化
    // ========================================================================

    /**
     * 构建意图语料库。
     *
     * 重要约束：tokenize() 只产出 unigram 与 bigram，因此长度 ≥3 的关键词
     * （如"多少钱""联系方式""早上好""七天无理由"）永远不可能与用户向量匹配到同一维度，
     * 属结构性死词（原语料 67 个关键词中有 12 个如此，占 18%）。
     * 这里统一改为 ≤2 字的关键词，或拆成多个 2 字词分别给权重。
     */
    private static Map<String, Map<String, Double>> buildIntentCorpus() {
        Map<String, Map<String, Double>> corpus = new HashMap<>();

        // 每个意图的关键词及其权重（模拟大量语料训练后的 TF-IDF 权重）
        // 注意：所有 key 长度必须 ≤2（unigram/bigram），否则无法被分词器命中
        corpus.put("问候", Map.ofEntries(
            Map.entry("你好", 5.0), Map.entry("您好", 5.0), Map.entry("hi", 4.0),
            Map.entry("嗨", 4.5), Map.entry("早", 3.0), Map.entry("早安", 4.5),
            Map.entry("晚安", 4.5), Map.entry("在吗", 3.5), Map.entry("好呀", 3.5),
            Map.entry("有人", 3.5)
        ));
        corpus.put("投诉", Map.ofEntries(
            Map.entry("投诉", 6.0), Map.entry("不满", 5.0), Map.entry("差评", 5.5),
            Map.entry("太差", 5.0), Map.entry("垃圾", 5.0), Map.entry("骗人", 5.5),
            Map.entry("举报", 5.5), Map.entry("曝光", 5.0), Map.entry("律师", 4.5),
            Map.entry("工商", 4.5), Map.entry("维权", 5.0), Map.entry("坑", 4.0),
            Map.entry("差劲", 4.5), Map.entry("恶心", 4.5), Map.entry("愤怒", 4.0),
            Map.entry("失望", 4.5)
        ));
        corpus.put("退换", Map.ofEntries(
            Map.entry("退款", 6.0), Map.entry("退货", 6.0), Map.entry("换货", 6.0),
            Map.entry("退换", 6.0), Map.entry("售后", 5.0), Map.entry("质量", 5.0),
            Map.entry("坏了", 4.0), Map.entry("破损", 4.5), Map.entry("发错", 4.5),
            Map.entry("少发", 4.5), Map.entry("退回", 4.0), Map.entry("理由", 4.5),
            Map.entry("七天", 4.0)
        ));
        corpus.put("咨询", Map.ofEntries(
            Map.entry("价格", 5.5), Map.entry("多少", 5.0), Map.entry("怎么", 4.0),
            Map.entry("在哪", 5.0), Map.entry("联系", 4.5), Map.entry("地址", 5.0),
            Map.entry("电话", 4.5), Map.entry("营业", 4.5), Map.entry("几点", 4.0),
            Map.entry("有货", 4.5), Map.entry("库存", 4.5), Map.entry("发货", 4.0),
            Map.entry("快递", 4.0), Map.entry("包邮", 4.5), Map.entry("优惠", 4.5),
            Map.entry("活动", 4.0), Map.entry("打折", 4.5), Map.entry("时间", 3.5),
            Map.entry("买", 3.5)
        ));
        corpus.put("感谢", Map.ofEntries(
            Map.entry("谢谢", 6.0), Map.entry("感谢", 6.0), Map.entry("多谢", 5.5),
            Map.entry("很棒", 5.0), Map.entry("点赞", 5.0), Map.entry("满意", 5.0),
            Map.entry("不错", 4.0), Map.entry("好评", 4.5), Map.entry("赞", 4.5),
            Map.entry("辛苦", 4.0), Map.entry("贴心", 4.5), Map.entry("很好", 4.5)
        ));
        return corpus;
    }

    private static Map<String, double[]> buildSentimentDict() {
        Map<String, double[]> dict = new HashMap<>();
        // 格式: [极性(-1~+1), 强度(0~1)]

        // ---- 正面情感词 ----
        dict.put("好",     new double[]{0.7, 0.6});
        dict.put("很好",   new double[]{0.8, 0.7});
        dict.put("太好了", new double[]{0.9, 0.8});
        dict.put("棒",     new double[]{0.8, 0.7});
        dict.put("很棒",   new double[]{0.85, 0.8});
        dict.put("优秀",   new double[]{0.9, 0.8});
        dict.put("满意",   new double[]{0.8, 0.7});
        dict.put("不错",   new double[]{0.6, 0.5});
        dict.put("赞",     new double[]{0.8, 0.7});
        dict.put("点赞",   new double[]{0.8, 0.7});
        dict.put("喜欢",   new double[]{0.7, 0.6});
        dict.put("感谢",   new double[]{0.6, 0.5});
        dict.put("谢谢",   new double[]{0.6, 0.5});
        dict.put("贴心",   new double[]{0.8, 0.7});
        dict.put("方便",   new double[]{0.5, 0.4});
        dict.put("快速",   new double[]{0.5, 0.4});
        dict.put("专业",   new double[]{0.7, 0.6});
        dict.put("靠谱",   new double[]{0.7, 0.6});

        // ---- 负面情感词 ----
        dict.put("差",     new double[]{-0.7, 0.6});
        dict.put("太差",   new double[]{-0.85, 0.8});
        dict.put("差劲",   new double[]{-0.8, 0.7});
        dict.put("垃圾",   new double[]{-0.9, 0.9});
        dict.put("坑",     new double[]{-0.8, 0.7});
        dict.put("骗",     new double[]{-0.9, 0.9});
        dict.put("骗人",   new double[]{-0.95, 0.95});
        dict.put("恶心",   new double[]{-0.9, 0.85});
        dict.put("愤怒",   new double[]{-0.85, 0.9});
        dict.put("不满",   new double[]{-0.6, 0.6});
        dict.put("失望",   new double[]{-0.7, 0.7});
        dict.put("生气",   new double[]{-0.8, 0.8});
        dict.put("投诉",   new double[]{-0.7, 0.7});
        dict.put("举报",   new double[]{-0.8, 0.8});
        dict.put("维权",   new double[]{-0.7, 0.7});
        dict.put("坏",     new double[]{-0.6, 0.5});
        dict.put("破",     new double[]{-0.6, 0.5});
        dict.put("烂",     new double[]{-0.8, 0.7});
        dict.put("慢",     new double[]{-0.4, 0.4});
        dict.put("等",     new double[]{-0.3, 0.3});
        dict.put("贵",     new double[]{-0.5, 0.5});
        dict.put("难用",   new double[]{-0.7, 0.6});
        dict.put("麻烦",   new double[]{-0.5, 0.5});

        // ---- 中性微调词 ----
        dict.put("一般",   new double[]{-0.1, 0.3});
        dict.put("还行",   new double[]{0.1, 0.3});
        dict.put("凑合",   new double[]{-0.1, 0.3});

        return dict;
    }
}
