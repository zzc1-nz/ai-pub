package com.ai.pub.experiment;

import com.ai.pub.service.ChatService;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

/**
 * 对比实验：关键词匹配 vs 单轮AI vs 本系统完整算法
 * 数据集: 200条标注消息
 * 评价指标: 语义理解准确率、多轮对话连贯性、无法回复率
 *
 * 运行方式: mvn test -Pexperiment -Dtest="ComparisonExperimentTest"
 *
 * 注意："本系统"方案的意图识别直接调用生产 ChatService，
 * 不再复制算法逻辑，避免实验结论与线上实现不一致。
 */
class ComparisonExperimentTest {

    /**
     * 生产 ChatService 实例，仅使用不依赖 Redis / 大模型的纯算法方法。
     */
    private final ChatService productionChatService =
            new ChatService(null, new com.fasterxml.jackson.databind.ObjectMapper(), null);

    // ================================================================
    //  数据集定义
    // ================================================================

    /** 单轮问题集: {消息, 期望意图} */
    private static final String[][] SINGLE_TURN_DATA = {
        // 问候 (16条)
        {"你好", "问候"}, {"您好，在吗", "问候"}, {"hi", "问候"}, {"hello", "问候"},
        {"嗨，有人吗", "问候"}, {"早上好", "问候"}, {"晚上好", "问候"}, {"下午好", "问候"},
        {"在不在", "问候"}, {"有人吗", "问候"}, {"你好呀", "问候"}, {"哈喽", "问候"},
        {"你好，请问", "问候"}, {"您好，我想问", "问候"}, {"嗨喽", "问候"}, {"喂", "问候"},

        // 投诉 (16条)
        {"你们这个太差了", "投诉"}, {"垃圾产品", "投诉"}, {"我要投诉", "投诉"},
        {"服务态度太差", "投诉"}, {"差评", "投诉"}, {"骗人的", "投诉"},
        {"太坑了", "投诉"}, {"不满", "投诉"}, {"要举报你们", "投诉"},
        {"恶心", "投诉"}, {"差劲", "投诉"}, {"愤怒", "投诉"},
        {"工商投诉", "投诉"}, {"维权", "投诉"}, {"曝光你们", "投诉"},
        {"找律师", "投诉"},

        // 退换 (16条)
        {"我要退款", "退换"}, {"退货", "退换"}, {"换货", "退换"},
        {"东西坏了", "退换"}, {"质量问题", "退换"}, {"发错了", "退换"},
        {"七天无理由", "退换"}, {"售后在哪", "退换"}, {"退换怎么操作", "退换"},
        {"破损了", "退换"}, {"少发了", "退换"}, {"退回地址", "退换"},
        {"可以退吗", "退换"}, {"质量不行要退", "退换"}, {"换个新的", "退换"},
        {"退款多久到账", "退换"},

        // 咨询 (16条)
        {"多少钱", "咨询"}, {"怎么买", "咨询"}, {"在哪下单", "咨询"},
        {"地址在哪", "咨询"}, {"电话多少", "咨询"}, {"营业时间", "咨询"},
        {"有优惠吗", "咨询"}, {"有货吗", "咨询"}, {"发货要多久", "咨询"},
        {"包邮吗", "咨询"}, {"有什么颜色", "咨询"}, {"库存多少", "咨询"},
        {"快递几天到", "咨询"}, {"打折吗", "咨询"}, {"联系方式", "咨询"},
        {"怎么联系客服", "咨询"},

        // 感谢 (16条)
        {"谢谢", "感谢"}, {"感谢你", "感谢"}, {"太感谢了", "感谢"},
        {"服务很好", "感谢"}, {"满意", "感谢"}, {"点赞", "感谢"},
        {"很棒", "感谢"}, {"好评", "感谢"}, {"辛苦了", "感谢"},
        {"不错", "感谢"}, {"多谢", "感谢"}, {"太好了", "感谢"},
        {"赞", "感谢"}, {"贴心", "感谢"}, {"感谢帮助", "感谢"},
        {"谢谢你的帮助", "感谢"},
    };

    /** 多轮对话场景: 每个场景3-5轮，含指代和省略 */
    private static final String[][][] MULTI_TURN_SCENARIOS = {
        // 场景1: 退换+指代(5轮)
        {
            {"user", "我买的东西坏了"},
            {"assistant", "非常抱歉，请问是什么商品呢？"},
            {"user", "手机壳，昨天收到的"},
            {"assistant", "好的，质量问题我们承担运费退换。请问您要退货还是换货？"},
            {"user", "换一个吧"}  // 指代：换货
        },
        // 场景2: 咨询+省略(4轮)
        {
            {"user", "iPhone 15多少钱"},
            {"assistant", "iPhone 15 128GB 售价5999元。"},
            {"user", "Pro呢"},  // 省略：iPhone 15 Pro多少钱
            {"assistant", "iPhone 15 Pro 128GB 售价7999元。"}
        },
        // 场景3: 投诉+指代(5轮)
        {
            {"user", "我要投诉"},
            {"assistant", "非常抱歉给您带来不好的体验，请问是什么问题呢？"},
            {"user", "快递三天了还没到"},
            {"assistant", "我帮您查一下物流信息，请问订单号是多少？"},
            {"user", "就是上次那个单子"}  // 指代：之前的订单
        },
        // 场景4: 咨询+指代(4轮)
        {
            {"user", "有什么颜色可以选"},
            {"assistant", "有黑色、白色、蓝色、粉色四种颜色。"},
            {"user", "蓝色有货吗"},
            {"assistant", "蓝色有现货。"},
            {"user", "那我要蓝色的"}  // 指代：蓝色商品
        },
        // 场景5: 退换+省略(4轮)
        {
            {"user", "想退货"},
            {"assistant", "好的，请问是什么原因呢？"},
            {"user", "不喜欢"},  // 省略：因为不喜欢所以退货
            {"assistant", "七天无理由退货，运费需要自理。"}
        },
        // 场景6: 咨询+多轮(5轮)
        {
            {"user", "你们的地址在哪"},
            {"assistant", "我们在北京市朝阳区XX路XX号。"},
            {"user", "营业时间呢"},
            {"assistant", "周一到周五 9:00-18:00。"},
            {"user", "周末去可以吗"}  // 指代：周末是否营业
        },
        // 场景7: 感谢+指代(3轮)
        {
            {"user", "帮我查一下物流"},
            {"assistant", "好的，您的快递已到达北京分拣中心，预计明天送达。"},
            {"user", "谢谢"}  // 指代：感谢查询物流
        },
        // 场景8: 投诉+省略(5轮)
        {
            {"user", "你们的产品质量太差了"},
            {"assistant", "非常抱歉，请问具体是什么问题呢？"},
            {"user", "屏幕有划痕"},
            {"assistant", "我们马上为您安排换货，请问您的订单号？"},
            {"user", "能快点吗"}  // 省略：能快点处理吗
        },
        // 场景9: 咨询+指代(4轮)
        {
            {"user", "有什么活动吗"},
            {"assistant", "目前有满减活动，满300减50。"},
            {"user", "那我买两个能减吗"},  // 指代：满减活动
            {"assistant", "可以的，两个商品总价超过300即可享受优惠。"}
        },
        // 场景10: 退换+指代(5轮)
        {
            {"user", "我要退款"},
            {"assistant", "好的，请问是什么原因呢？"},
            {"user", "发错颜色了"},
            {"assistant", "抱歉给您添麻烦了，我们马上安排退换。请问您要退款还是重新发货？"},
            {"user", "退钱吧"}  // 指代：退款
        },
        // 场景11: 咨询+省略(3轮)
        {
            {"user", "怎么买"},
            {"assistant", "您可以直接在公众号内点击菜单栏的商城链接购买。"},
            {"user", "能分期吗"}  // 省略：买东西能分期吗
        },
        // 场景12: 投诉+多轮(4轮)
        {
            {"user", "你们的服务态度太差了"},
            {"assistant", "非常抱歉，请问是哪位客服呢？"},
            {"user", "就是昨天那个"},
            {"assistant", "我们会严肃处理，请问具体发生了什么？"}
        },
        // 场景13: 咨询+指代(4轮)
        {
            {"user", "快递几天到"},
            {"assistant", "一般3-5天送达。"},
            {"user", "能加急吗"},
            {"assistant", "可以加急，加急件1-2天送达，需额外支付10元。"},
            {"user", "那就加急吧"}  // 指代：加急配送
        },
        // 场景14: 退换+省略(3轮)
        {
            {"user", "东西收到了"},
            {"assistant", "好的，请问商品满意吗？"},
            {"user", "和图片差太多了"}  // 省略：和图片差距大要退货
        },
        // 场景15: 感谢+指代(3轮)
        {
            {"user", "帮我改一下收货地址"},
            {"assistant", "好的，已为您修改为新地址。"},
            {"user", "谢谢，太好了"}  // 指代：感谢修改地址
        },
        // 场景16: 咨询+指代(4轮)
        {
            {"user", "有什么品牌"},
            {"assistant", "我们有苹果、华为、小米、OPPO等品牌。"},
            {"user", "华为的多少钱"},
            {"assistant", "华为 Mate 60 售价4999元起。"},
            {"user", "比苹果便宜啊"}  // 指代：与苹果价格比较
        },
        // 场景17: 投诉+指代(4轮)
        {
            {"user", "我要退款，还没收到"},
            {"assistant", "我帮您查一下退款进度，请问订单号？"},
            {"user", "上周五下的单"},
            {"assistant", "退款正在处理中，预计1-3个工作日到账。"},
            {"user", "太慢了吧"}  // 指代：退款速度慢
        },
        // 场景18: 咨询+省略(3轮)
        {
            {"user", "包邮吗"},
            {"assistant", "满99元包邮。"},
            {"user", "那我再买一个"}  // 省略：再买一个凑包邮
        },
        // 场景19: 退换+指代(4轮)
        {
            {"user", "收到的东西少了"},
            {"assistant", "非常抱歉，请问少了什么？"},
            {"user", "充电器没发"},
            {"assistant", "我们马上补发，请问您的订单号？"},
            {"user", "能快点发吗"}  // 指代：补发充电器
        },
        // 场景20: 咨询+多轮(5轮)
        {
            {"user", "有什么推荐吗"},
            {"assistant", "您预算多少呢？"},
            {"user", "3000左右"},
            {"assistant", "推荐小米14，售价2999元，性价比很高。"},
            {"user", "有什么颜色"}  // 指代：小米14的颜色
        },
    };

    /** 边界测试样本: {消息, 期望分类} — 模拟真实用户输入 */
    private static final String[][] BOUNDARY_DATA = {
        // 空/无意义消息 (5条)
        {"", "默认"}, {"  ", "默认"}, {"嗯", "默认"}, {"哦", "默认"}, {"好", "默认"},

        // 混合意图 (10条) — 真实场景中用户常混合表达
        {"你好，我要投诉", "投诉"}, {"谢谢，但是东西坏了", "退换"},
        {"多少钱，可以退吗", "咨询"}, {"你好，在吗，想退款", "退换"},
        {"你好，请问多少钱", "咨询"}, {"太差了，但是态度不错", "投诉"},
        {"谢谢帮忙，但是我要退货", "退换"}, {"你好，东西坏了", "退换"},
        {"嗯嗯好的，那我要退款", "退换"}, {"不错不错，但是快递太慢了", "投诉"},

        // 长文本 (5条)
        {"我昨天在你们这里买了一个手机壳，今天收到发现质量很差，边角有毛刺，而且颜色和图片不一样，我要退货退款，请问怎么操作？", "退换"},
        {"请问你们店里的iPhone 15 Pro Max 256GB版本现在售价多少钱？有没有优惠活动？可以分期付款吗？", "咨询"},
        {"非常感谢您的耐心解答，您的服务态度真的很好，我会推荐朋友来你们这里购买的，五星好评！", "感谢"},
        {"你们这个产品用了两天就坏了，找客服也没人理，太失望了，必须给我一个说法", "投诉"},
        {"你好你好你好你好你好你好你好你好你好你好", "问候"},

        // 纯英文 (5条)
        {"Hello", "问候"}, {"How much", "咨询"}, {"Thank you", "感谢"},
        {"I want a refund", "退换"}, {"This is terrible", "投诉"},
    };

    // ================================================================
    //  方案一：传统关键词匹配
    // ================================================================

    private static final Map<String, List<String>> KEYWORD_RULES = new HashMap<>();
    static {
        KEYWORD_RULES.put("问候", List.of("你好","您好","hi","hello","嗨","早","晚上好","下午好","在吗","在不在","哈喽","喂"));
        KEYWORD_RULES.put("投诉", List.of("投诉","不满","差评","太差","垃圾","骗人","举报","曝光","律师","工商","维权","坑","差劲","恶心","愤怒"));
        KEYWORD_RULES.put("退换", List.of("退款","退货","换货","退换","售后","七天无理由","质量问题","坏了","破损","发错","少发","退回"));
        KEYWORD_RULES.put("咨询", List.of("价格","多少钱","怎么买","在哪","联系方式","地址","电话","营业时间","几点","有货","库存","发货","快递","包邮","优惠","活动","打折"));
        KEYWORD_RULES.put("感谢", List.of("谢谢","感谢","多谢","太好了","很棒","点赞","满意","不错","好评","赞","辛苦","贴心"));
    }

    private String keywordMatch(String message) {
        if (message == null || message.trim().isEmpty()) return "默认";
        String lower = message.toLowerCase();
        for (Map.Entry<String, List<String>> entry : KEYWORD_RULES.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) return entry.getKey();
            }
        }
        return "默认";
    }

    // ================================================================
    //  方案二：单轮AI（固定Prompt，无上下文，无情感分析）
    // ================================================================

    private String singleTurnAI(String message) {
        // 模拟单轮AI：使用简化的TF-IDF（无bigram，无情感加权，固定Prompt）
        if (message == null || message.trim().isEmpty()) return "默认";

        // 简单分词（仅unigram，无停用词过滤）
        List<String> tokens = new ArrayList<>();
        for (char c : message.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(c)) tokens.add(String.valueOf(c));
        }
        if (tokens.isEmpty()) return "默认";

        // 简单词频匹配（无TF-IDF加权）
        Map<String, Double> scores = new HashMap<>();
        for (String intent : KEYWORD_RULES.keySet()) {
            double score = 0;
            for (String token : tokens) {
                for (String keyword : KEYWORD_RULES.get(intent)) {
                    if (keyword.contains(token) || token.contains(keyword)) {
                        score += 1.0;
                        break;
                    }
                }
            }
            scores.put(intent, score);
        }

        String best = "默认";
        double max = 0;
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); best = e.getKey(); }
        }
        return max > 0 ? best : "默认";
    }

    // ================================================================
    //  方案三：本系统完整算法
    // ================================================================

    // 说明：原先此处复制了一份 ChatService 的核心算法，并且额外加了一层生产代码
    // 中并不存在的 keywordBoost（关键词原文包含加成 /10）。这导致"本系统"在对比
    // 实验中的准确率（77.3%）明显高于同一算法在实验一中的表现（θ=0.15 时仅 23.3%），
    // 两个实验数据互相矛盾。现已改为直接调用生产 ChatService，保证同源。

    /**
     * 使用生产代码当前生效的阈值，而不是实验里硬编码的值。
     * 原先此处写死 0.15，而生产 ChatService 已调整为 0.05，
     * 会让"本系统"在对比实验中被严重低估。
     */
    private static final double INTENT_THRESHOLD = ChatService.productionIntentThreshold();

    private static final Map<String, double[]> SENTIMENT_DICT = buildSentimentDict();

    private static Map<String, double[]> buildSentimentDict() {
        Map<String, double[]> dict = new HashMap<>();
        dict.put("好", new double[]{0.7,0.6}); dict.put("很好", new double[]{0.8,0.7});
        dict.put("太好了", new double[]{0.9,0.8}); dict.put("棒", new double[]{0.8,0.7});
        dict.put("很棒", new double[]{0.85,0.8}); dict.put("优秀", new double[]{0.9,0.8});
        dict.put("满意", new double[]{0.8,0.7}); dict.put("不错", new double[]{0.6,0.5});
        dict.put("赞", new double[]{0.8,0.7}); dict.put("点赞", new double[]{0.8,0.7});
        dict.put("喜欢", new double[]{0.7,0.6}); dict.put("感谢", new double[]{0.6,0.5});
        dict.put("谢谢", new double[]{0.6,0.5}); dict.put("贴心", new double[]{0.8,0.7});
        dict.put("方便", new double[]{0.5,0.4}); dict.put("快速", new double[]{0.5,0.4});
        dict.put("专业", new double[]{0.7,0.6}); dict.put("靠谱", new double[]{0.7,0.6});
        dict.put("差", new double[]{-0.7,0.6}); dict.put("太差", new double[]{-0.85,0.8});
        dict.put("差劲", new double[]{-0.8,0.7}); dict.put("垃圾", new double[]{-0.9,0.9});
        dict.put("坑", new double[]{-0.8,0.7}); dict.put("骗", new double[]{-0.9,0.9});
        dict.put("骗人", new double[]{-0.95,0.95}); dict.put("恶心", new double[]{-0.9,0.85});
        dict.put("愤怒", new double[]{-0.85,0.9}); dict.put("不满", new double[]{-0.6,0.6});
        dict.put("失望", new double[]{-0.7,0.7}); dict.put("生气", new double[]{-0.8,0.8});
        dict.put("投诉", new double[]{-0.7,0.7}); dict.put("举报", new double[]{-0.8,0.8});
        dict.put("维权", new double[]{-0.7,0.7}); dict.put("坏", new double[]{-0.6,0.5});
        dict.put("破", new double[]{-0.6,0.5}); dict.put("烂", new double[]{-0.8,0.7});
        dict.put("慢", new double[]{-0.4,0.4}); dict.put("等", new double[]{-0.3,0.3});
        dict.put("贵", new double[]{-0.5,0.5}); dict.put("难用", new double[]{-0.7,0.6});
        dict.put("麻烦", new double[]{-0.5,0.5});
        dict.put("一般", new double[]{-0.1,0.3}); dict.put("还行", new double[]{0.1,0.3});
        dict.put("凑合", new double[]{-0.1,0.3});
        return dict;
    }

    /**
     * 方案三：本系统完整算法（意图识别直接复用生产 ChatService）
     */
    private String fullAlgorithm(String message) {
        // 完整四层算法
        if (message == null || message.trim().isEmpty()) return "默认";

        // 第一层 + 第二层：N-gram 分词 + 停用词过滤 + TF-IDF + 余弦相似度
        // 直接调用生产实现，不再复制逻辑、也不再添加生产代码中不存在的加成层
        List<String> tokens = productionChatService.tokenizeForExperiment(message);
        if (tokens.isEmpty()) return "默认";

        ChatService.IntentResult intentResult =
                productionChatService.classifyIntent(tokens, INTENT_THRESHOLD);
        String bestIntent = intentResult.intent;
        double bestScore = intentResult.confidence;

        // 第三层：情感分析
        double sumW = 0, sumS = 0;
        boolean found = false;
        for (String token : tokens) {
            double[] scores = lookupSentiment(token);
            if (scores != null) {
                sumS += scores[0] * scores[1];
                sumW += scores[1];
                found = true;
            }
        }
        // 同时检查原文中的情感词
        String lower = message.toLowerCase();
        for (Map.Entry<String, double[]> entry : SENTIMENT_DICT.entrySet()) {
            if (lower.contains(entry.getKey())) {
                sumS += entry.getValue()[0] * entry.getValue()[1];
                sumW += entry.getValue()[1];
                found = true;
            }
        }
        double E = found ? sumS / sumW : 0.0;
        String sentimentLevel = E >= 0.5 ? "高度正面" : E >= 0.1 ? "轻度正面" :
                               E >= -0.1 ? "中性" : E >= -0.5 ? "轻度负面" : "高度负面";

        // 第四层：紧急度
        double urgency = 0.0;
        for (String t : tokens) {
            if (t.contains("催") || t.contains("急") || t.contains("快") || t.contains("投诉") ||
                t.contains("报警") || t.contains("曝光") || t.contains("律师")) { urgency = 1.0; break; }
            if (t.contains("等") || t.contains("多久") || t.contains("什么时候") || t.contains("还没")) urgency = 0.5;
        }

        // 多因子融合决策
        double decision = 0.5 * bestScore + 0.3 * (E + 1) / 2 + 0.2 * urgency;

        // 情感修正：负面情感时调整意图
        if ("高度负面".equals(sentimentLevel) && !"默认".equals(bestIntent)) {
            if (!"投诉".equals(bestIntent) && decision < 0.6) {
                bestIntent = "投诉";
            }
        }

        return bestIntent;
    }

    private double[] lookupSentiment(String token) {
        if (SENTIMENT_DICT.containsKey(token)) return SENTIMENT_DICT.get(token);
        for (Map.Entry<String, double[]> entry : SENTIMENT_DICT.entrySet()) {
            if (token.contains(entry.getKey()) || entry.getKey().contains(token)) return entry.getValue();
        }
        return null;
    }

    // 说明：原 tokenize / cosineSimilarity / getCorpus 三份与 ChatService 重复的
    // 实现（且语料库已与生产代码漂移）已删除，统一改用 productionChatService。

    // ================================================================
    //  实验执行
    // ================================================================

    @Test
    void runComparisonExperiment() throws Exception {
        System.out.println("========== 对比实验：三组方案 ==========");
        System.out.println("数据集: " + SINGLE_TURN_DATA.length + " 单轮 + " +
                MULTI_TURN_SCENARIOS.length + " 多轮场景 + " + BOUNDARY_DATA.length + " 边界样本");
        System.out.println();

        // 统计变量
        int[] kwCorrect = {0}, aiCorrect = {0}, fullCorrect = {0};
        int[] kwNoReply = {0}, aiNoReply = {0}, fullNoReply = {0};
        int[] kwMultiCorrect = {0}, aiMultiCorrect = {0}, fullMultiCorrect = {0};
        int totalMultiTurn = 0;

        List<String[]> csvRows = new ArrayList<>();
        csvRows.add(new String[]{"method", "accuracy", "coherence", "no_reply_rate"});

        // ---- 单轮准确率测试（单轮问题集 + 边界测试） ----
        int singleTotal = SINGLE_TURN_DATA.length + BOUNDARY_DATA.length;
        // 分别统计单轮和边界
        int[] kwSingleCorrect = {0}, aiSingleCorrect = {0}, fullSingleCorrect = {0};
        int[] kwBoundCorrect = {0}, aiBoundCorrect = {0}, fullBoundCorrect = {0};

        for (String[] sample : SINGLE_TURN_DATA) {
            String msg = sample[0], expected = sample[1];
            String kwResult = keywordMatch(msg);
            String aiResult = singleTurnAI(msg);
            String fullResult = fullAlgorithm(msg);

            if (kwResult.equals(expected)) { kwCorrect[0]++; kwSingleCorrect[0]++; }
            if (aiResult.equals(expected)) { aiCorrect[0]++; aiSingleCorrect[0]++; }
            if (fullResult.equals(expected)) { fullCorrect[0]++; fullSingleCorrect[0]++; }

            if ("默认".equals(kwResult) && !"默认".equals(expected)) kwNoReply[0]++;
            if ("默认".equals(aiResult) && !"默认".equals(expected)) aiNoReply[0]++;
            if ("默认".equals(fullResult) && !"默认".equals(expected)) fullNoReply[0]++;
        }

        for (String[] sample : BOUNDARY_DATA) {
            String msg = sample[0], expected = sample[1];
            String kwResult = keywordMatch(msg);
            String aiResult = singleTurnAI(msg);
            String fullResult = fullAlgorithm(msg);

            if (kwResult.equals(expected)) { kwCorrect[0]++; kwBoundCorrect[0]++; }
            if (aiResult.equals(expected)) { aiCorrect[0]++; aiBoundCorrect[0]++; }
            if (fullResult.equals(expected)) { fullCorrect[0]++; fullBoundCorrect[0]++; }
        }

        // ---- 多轮连贯性测试 ----
        // 评估方法：检查多轮场景中最后一轮的意图是否能被正确识别
        // （关键词匹配无法处理指代/省略，单轮AI无上下文，完整算法有滑动窗口）
        for (String[][] scenario : MULTI_TURN_SCENARIOS) {
            totalMultiTurn++;
            String lastUserMsg = null;
            String lastExpectedIntent = null;

            // 获取最后一轮用户消息
            for (int i = scenario.length - 1; i >= 0; i--) {
                if ("user".equals(scenario[i][0])) {
                    lastUserMsg = scenario[i][1];
                    break;
                }
            }

            // 推断期望意图（基于场景上下文）
            lastExpectedIntent = inferIntentFromContext(scenario);

            if (lastUserMsg != null) {
                // 关键词匹配：只看最后一轮消息
                String kwResult = keywordMatch(lastUserMsg);
                // 单轮AI：只看最后一轮消息
                String aiResult = singleTurnAI(lastUserMsg);
                // 完整算法：模拟有上下文的情况
                // 对于多轮对话，最后一轮通常含指代/省略，需要上下文才能正确理解
                // 完整算法通过滑动窗口保留历史，能更好地理解上下文
                String fullResult = fullAlgorithmWithContext(scenario);

                if (kwResult.equals(lastExpectedIntent)) kwMultiCorrect[0]++;
                if (aiResult.equals(lastExpectedIntent)) aiMultiCorrect[0]++;
                if (fullResult.equals(lastExpectedIntent)) fullMultiCorrect[0]++;
            }
        }

        // ---- 计算最终结果 ----
        double kwAccuracy = (double) kwCorrect[0] / singleTotal * 100;
        double aiAccuracy = (double) aiCorrect[0] / singleTotal * 100;
        double fullAccuracy = (double) fullCorrect[0] / singleTotal * 100;

        double kwCoherence = (double) kwMultiCorrect[0] / totalMultiTurn * 100;
        double aiCoherence = (double) aiMultiCorrect[0] / totalMultiTurn * 100;
        double fullCoherence = (double) fullMultiCorrect[0] / totalMultiTurn * 100;

        double kwNoReplyRate = (double) kwNoReply[0] / singleTotal * 100;
        double aiNoReplyRate = (double) aiNoReply[0] / singleTotal * 100;
        double fullNoReplyRate = (double) fullNoReply[0] / singleTotal * 100;

        // 分类准确率
        double kwSingleAcc = (double) kwSingleCorrect[0] / SINGLE_TURN_DATA.length * 100;
        double aiSingleAcc = (double) aiSingleCorrect[0] / SINGLE_TURN_DATA.length * 100;
        double fullSingleAcc = (double) fullSingleCorrect[0] / SINGLE_TURN_DATA.length * 100;

        // 输出结果
        System.out.println("┌──────────────┬──────────┬──────────┬──────────┬──────────┐");
        System.out.println("│    方案      │ 单轮准确 │ 综合准确 │ 多轮连贯 │ 无法回复 │");
        System.out.println("├──────────────┼──────────┼──────────┼──────────┼──────────┤");
        System.out.printf("│ 关键词匹配   │  %6.1f%% │  %6.1f%% │  %6.1f%% │  %6.1f%% │\n", kwSingleAcc, kwAccuracy, kwCoherence, kwNoReplyRate);
        System.out.printf("│ 单轮AI       │  %6.1f%% │  %6.1f%% │  %6.1f%% │  %6.1f%% │\n", aiSingleAcc, aiAccuracy, aiCoherence, aiNoReplyRate);
        System.out.printf("│ 本系统       │  %6.1f%% │  %6.1f%% │  %6.1f%% │  %6.1f%% │\n", fullSingleAcc, fullAccuracy, fullCoherence, fullNoReplyRate);
        System.out.println("└──────────────┴──────────┴──────────┴──────────┴──────────┘");

        csvRows.add(new String[]{"关键词匹配", String.format("%.1f", kwSingleAcc), String.format("%.1f", kwAccuracy), String.format("%.1f", kwCoherence), String.format("%.1f", kwNoReplyRate)});
        csvRows.add(new String[]{"单轮AI", String.format("%.1f", aiSingleAcc), String.format("%.1f", aiAccuracy), String.format("%.1f", aiCoherence), String.format("%.1f", aiNoReplyRate)});
        csvRows.add(new String[]{"本系统", String.format("%.1f", fullSingleAcc), String.format("%.1f", fullAccuracy), String.format("%.1f", fullCoherence), String.format("%.1f", fullNoReplyRate)});

        writeCSV("experiment_comparison.csv", csvRows);
        System.out.println("\n数据已保存到: target/experiment/experiment_comparison.csv");

        // 输出详细分析
        System.out.println("\n---- 详细分析 ----");
        System.out.println("单轮问题集: " + SINGLE_TURN_DATA.length + " 条");
        System.out.printf("  关键词匹配: 正确 %d (%.1f%%)\n", kwSingleCorrect[0], kwSingleAcc);
        System.out.printf("  单轮AI:     正确 %d (%.1f%%)\n", aiSingleCorrect[0], aiSingleAcc);
        System.out.printf("  本系统:     正确 %d (%.1f%%)\n", fullSingleCorrect[0], fullSingleAcc);
        System.out.println("边界测试: " + BOUNDARY_DATA.length + " 条");
        System.out.printf("  关键词匹配: 正确 %d\n", kwBoundCorrect[0]);
        System.out.printf("  单轮AI:     正确 %d\n", aiBoundCorrect[0]);
        System.out.printf("  本系统:     正确 %d\n", fullBoundCorrect[0]);
        System.out.println("多轮测试: " + totalMultiTurn + " 个场景");
        System.out.printf("  关键词匹配: 正确 %d (%.1f%%)\n", kwMultiCorrect[0], kwCoherence);
        System.out.printf("  单轮AI:     正确 %d (%.1f%%)\n", aiMultiCorrect[0], aiCoherence);
        System.out.printf("  本系统:     正确 %d (%.1f%%)\n", fullMultiCorrect[0], fullCoherence);
        System.out.println("综合测试: " + singleTotal + " 条 + " + totalMultiTurn + " 场景");
        System.out.printf("  关键词匹配: 正确 %d, 无法回复 %d\n", kwCorrect[0], kwNoReply[0]);
        System.out.printf("  单轮AI:     正确 %d, 无法回复 %d\n", aiCorrect[0], aiNoReply[0]);
        System.out.printf("  本系统:     正确 %d, 无法回复 %d\n", fullCorrect[0], fullNoReply[0]);
    }

    /**
     * 模拟完整算法+上下文：对多轮对话的最后一轮，
     * 结合前面几轮的上下文信息来理解指代和省略
     */
    private String fullAlgorithmWithContext(String[][] scenario) {
        // 收集所有用户消息作为上下文
        StringBuilder context = new StringBuilder();
        for (String[] turn : scenario) {
            if ("user".equals(turn[0])) {
                context.append(turn[1]).append(" ");
            }
        }
        // 用完整上下文进行意图识别
        return fullAlgorithm(context.toString().trim());
    }

    /**
     * 从多轮对话上下文推断最后一轮的期望意图
     */
    private String inferIntentFromContext(String[][] scenario) {
        // 根据场景内容推断意图
        String allContent = Arrays.stream(scenario)
                .map(t -> t[1])
                .reduce("", (a, b) -> a + " " + b);

        if (allContent.contains("退") || allContent.contains("换") || allContent.contains("坏") || allContent.contains("发错") || allContent.contains("少发") || allContent.contains("破损")) return "退换";
        if (allContent.contains("投诉") || allContent.contains("差") || allContent.contains("太差") || allContent.contains("垃圾") || allContent.contains("不满") || allContent.contains("态度")) return "投诉";
        if (allContent.contains("多少钱") || allContent.contains("地址") || allContent.contains("营业") || allContent.contains("颜色") || allContent.contains("有货") || allContent.contains("快递") || allContent.contains("包邮") || allContent.contains("活动") || allContent.contains("品牌") || allContent.contains("推荐") || allContent.contains("怎么买") || allContent.contains("加急") || allContent.contains("分期")) return "咨询";
        if (allContent.contains("谢谢") || allContent.contains("感谢") || allContent.contains("太好了")) return "感谢";
        return "默认";
    }

    private void writeCSV(String filename, List<String[]> rows) throws Exception {
        String path = "target/experiment/" + filename;
        new java.io.File("target/experiment").mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            for (String[] row : rows) pw.println(String.join(",", row));
        }
    }
}
