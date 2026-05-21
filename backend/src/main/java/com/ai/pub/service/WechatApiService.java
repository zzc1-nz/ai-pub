package com.ai.pub.service;

import com.ai.pub.entity.WechatAccount;
import com.ai.pub.entity.WechatMenu;
import com.ai.pub.repository.WechatAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class WechatApiService {

    private static final Logger log = LoggerFactory.getLogger(WechatApiService.class);

    private final WechatAccountRepository accountRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate;

    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";
    private static final String MENU_CREATE_URL = "https://api.weixin.qq.com/cgi-bin/menu/create?access_token=%s";
    private static final String MENU_DELETE_URL = "https://api.weixin.qq.com/cgi-bin/menu/delete?access_token=%s";
    private static final String CUSTOM_MESSAGE_URL = "https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=%s";
    private static final String MATERIAL_ADD_URL = "https://api.weixin.qq.com/cgi-bin/material/add_material?access_token=%s&type=%s";
    private static final String ACCESS_TOKEN_REDIS_KEY = "wechat:access_token:";
    private static final String TOKEN_LOCK_KEY = "wechat:token_lock:";
    private static final int PRE_RENEWAL_SECONDS = 300; // 提前5分钟续期
    private static final int LOCK_TIMEOUT_SECONDS = 10;  // 分布式锁超时

    public WechatApiService(WechatAccountRepository accountRepository, ObjectMapper objectMapper,
                           RedisTemplate<String, String> redisTemplate) {
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 获取access_token，采用三层策略：
     * 1. Redis缓存优先（毫秒级响应）
     * 2. TTL < 5分钟时提前异步续期（防过期）
     * 3. 分布式锁防止并发击穿（同一时刻只有一个线程刷新）
     */
    public String getAccessToken(Long accountId) {
        String redisKey = ACCESS_TOKEN_REDIS_KEY + accountId;

        // 第一层：从缓存读取
        String cachedToken = redisTemplate.opsForValue().get(redisKey);
        if (cachedToken != null && !cachedToken.isEmpty()) {
            // 检查剩余TTL，提前续期
            Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            if (ttl != null && ttl > 0 && ttl < PRE_RENEWAL_SECONDS) {
                log.info("access_token即将过期(剩余{}秒)，异步续期: accountId={}", ttl, accountId);
                asyncRenewToken(accountId);
            }
            return cachedToken;
        }

        // 第二层：缓存未命中，加分布式锁防击穿
        String lockKey = TOKEN_LOCK_KEY + accountId;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双重检查：获取锁期间可能已被其他线程刷新
                String recheck = redisTemplate.opsForValue().get(redisKey);
                if (recheck != null && !recheck.isEmpty()) {
                    return recheck;
                }
                // 实际刷新
                return fetchAndCacheToken(accountId, redisKey);
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            // 未获取到锁，等待后重试（最多等3秒）
            for (int i = 0; i < 6; i++) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                String token = redisTemplate.opsForValue().get(redisKey);
                if (token != null && !token.isEmpty()) {
                    return token;
                }
            }
            throw new RuntimeException("获取access_token超时，请稍后重试");
        }
    }

    /**
     * 异步续期：提交到单独线程执行，不阻塞当前请求
     */
    private void asyncRenewToken(Long accountId) {
        new Thread(() -> {
            try {
                String lockKey = TOKEN_LOCK_KEY + accountId;
                Boolean locked = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "1", LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (Boolean.TRUE.equals(locked)) {
                    try {
                        fetchAndCacheToken(accountId, ACCESS_TOKEN_REDIS_KEY + accountId);
                        log.info("异步续期成功: accountId={}", accountId);
                    } finally {
                        redisTemplate.delete(lockKey);
                    }
                }
            } catch (Exception e) {
                log.warn("异步续期失败: accountId={}", accountId, e);
            }
        }).start();
    }

    private String fetchAndCacheToken(Long accountId, String redisKey) {
        WechatAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("公众号不存在"));

        String url = String.format(ACCESS_TOKEN_URL, account.getAppId(), account.getAppSecret());

        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("access_token")) {
                String accessToken = root.get("access_token").asText();
                int expiresIn = root.has("expires_in") ? root.get("expires_in").asInt() : 7200;

                redisTemplate.opsForValue().set(redisKey, accessToken, expiresIn - 60, TimeUnit.SECONDS);
                log.info("获取access_token成功: accountId={}", accountId);
                return accessToken;
            } else if (root.has("errcode")) {
                int errcode = root.get("errcode").asInt();
                String errmsg = root.has("errmsg") ? root.get("errmsg").asText() : "未知错误";
                throw new RuntimeException("获取access_token失败: " + errmsg);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("获取access_token异常", e);
        }

        throw new RuntimeException("获取access_token失败");
    }

    public boolean syncMenu(Long accountId, List<WechatMenu> menus) {
        try {
            String accessToken = getAccessToken(accountId);
            
            String deleteUrl = String.format(MENU_DELETE_URL, accessToken);
            String deleteResponse = restTemplate.getForObject(deleteUrl, String.class);
            log.info("删除旧菜单: {}", deleteResponse);

            ObjectNode menuJson = objectMapper.createObjectNode();
            ArrayNode buttonArray = objectMapper.createArrayNode();

            for (WechatMenu menu : menus) {
                if (menu.getParentId() == null || menu.getParentId() == 0) {
                    ObjectNode buttonNode = objectMapper.createObjectNode();
                    buttonNode.put("name", menu.getName());

                    List<WechatMenu> subMenus = menus.stream()
                            .filter(m -> menu.getId().equals(m.getParentId()))
                            .toList();

                    if (!subMenus.isEmpty()) {
                        ArrayNode subButtonArray = objectMapper.createArrayNode();
                        for (WechatMenu subMenu : subMenus) {
                            ObjectNode subButtonNode = objectMapper.createObjectNode();
                            subButtonNode.put("name", subMenu.getName());
                            
                            if ("click".equals(subMenu.getType())) {
                                subButtonNode.put("type", "click");
                                subButtonNode.put("key", subMenu.getKey());
                            } else if ("view".equals(subMenu.getType())) {
                                subButtonNode.put("type", "view");
                                subButtonNode.put("url", subMenu.getUrl());
                            }
                            
                            subButtonArray.add(subButtonNode);
                        }
                        buttonNode.set("sub_button", subButtonArray);
                    } else {
                        if ("click".equals(menu.getType())) {
                            buttonNode.put("type", "click");
                            buttonNode.put("key", menu.getKey());
                        } else if ("view".equals(menu.getType())) {
                            buttonNode.put("type", "view");
                            buttonNode.put("url", menu.getUrl());
                        }
                    }

                    buttonArray.add(buttonNode);
                }
            }

            menuJson.set("button", buttonArray);
            log.info("准备同步的菜单JSON: {}", menuJson.toString());

            String createUrl = String.format(MENU_CREATE_URL, accessToken);
            HttpHeaders menuHeaders = new HttpHeaders();
            menuHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> menuEntity = new HttpEntity<>(menuJson.toString(), menuHeaders);
            String response = restTemplate.postForObject(createUrl, menuEntity, String.class);
            
            JsonNode root = objectMapper.readTree(response);
            if (root.has("errcode") && root.get("errcode").asInt() == 0) {
                log.info("菜单同步成功");
                return true;
            } else {
                int errcode = root.has("errcode") ? root.get("errcode").asInt() : -1;
                String errmsg = root.has("errmsg") ? root.get("errmsg").asText() : "未知错误";
                log.error("菜单同步失败: errcode={}, errmsg={}", errcode, errmsg);
                throw new RuntimeException("菜单同步失败: " + errmsg + " (错误码: " + errcode + ")");
            }

        } catch (RuntimeException e) {
            log.error("菜单同步失败", e);
            throw e;
        } catch (Exception e) {
            log.error("菜单同步异常", e);
            throw new RuntimeException("菜单同步异常: " + e.getMessage(), e);
        }
    }

    public String uploadPermanentMaterial(Long accountId, String type, byte[] fileBytes, String fileName) {
        try {
            String accessToken = getAccessToken(accountId);
            String url = String.format(MATERIAL_ADD_URL, accessToken, type);

            org.springframework.core.io.ByteArrayResource fileResource =
                    new org.springframework.core.io.ByteArrayResource(fileBytes) {
                        @Override
                        public String getFilename() {
                            return fileName;
                        }
                    };

            org.springframework.util.LinkedMultiValueMap<String, Object> parts =
                    new org.springframework.util.LinkedMultiValueMap<>();
            parts.add("media", fileResource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity =
                    new HttpEntity<>(parts, headers);

            String response = restTemplate.postForObject(url, entity, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("media_id")) {
                String mediaId = root.get("media_id").asText();
                String materialUrl = root.has("url") ? root.get("url").asText() : null;
                log.info("上传永久素材成功: mediaId={}, url={}", mediaId, materialUrl);
                return mediaId;
            } else {
                int errcode = root.has("errcode") ? root.get("errcode").asInt() : -1;
                String errmsg = root.has("errmsg") ? root.get("errmsg").asText() : "未知错误";
                log.error("上传永久素材失败: errcode={}, errmsg={}", errcode, errmsg);
                throw new RuntimeException("上传永久素材失败: " + errmsg);
            }
        } catch (Exception e) {
            log.error("上传永久素材异常", e);
            throw new RuntimeException("上传永久素材异常: " + e.getMessage());
        }
    }

    public boolean sendCustomerMessage(Long accountId, String openid, String content) {
        try {
            String accessToken = getAccessToken(accountId);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("touser", openid);
            body.put("msgtype", "text");
            ObjectNode textNode = objectMapper.createObjectNode();
            textNode.put("content", content);
            body.set("text", textNode);

            String url = String.format(CUSTOM_MESSAGE_URL, accessToken);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);
            String response = restTemplate.postForObject(url, entity, String.class);

            JsonNode root = objectMapper.readTree(response);
            if (root.has("errcode") && root.get("errcode").asInt() == 0) {
                log.info("客服消息发送成功: openid={}", openid);
                return true;
            } else {
                int errcode = root.has("errcode") ? root.get("errcode").asInt() : -1;
                String errmsg = root.has("errmsg") ? root.get("errmsg").asText() : "未知错误";
                log.error("客服消息发送失败: errcode={}, errmsg={}", errcode, errmsg);
                return false;
            }
        } catch (Exception e) {
            log.error("发送客服消息异常", e);
            return false;
        }
    }
}
