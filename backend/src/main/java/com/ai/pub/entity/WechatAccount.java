package com.ai.pub.entity;

import com.ai.pub.util.AesUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "wechat_account")
public class WechatAccount {

    @Transient
    private static final AesUtil AES = new AesUtil();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "app_id", nullable = false, unique = true, length = 100)
    private String appId;

    /**
     * AppSecret：调用微信接口的凭证。
     *
     * WRITE_ONLY 的作用：
     * 1. 不随 /api/wechat/accounts 等接口下发（原先会明文返回，任何能访问
     *    后台列表的账号都能拿到全部公众号的 AppSecret）；
     * 2. 仍可从请求体写入，新增/修改公众号时能正常保存。
     * 配套地，WechatAccountService.update() 改为"留空则不覆盖"，
     * 否则前端编辑时因拿不到原值会把它清空。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "app_secret", nullable = false)
    private String appSecret;

    @Column(name = "token", nullable = false, length = 100)
    private String token;

    /** EncodingAESKey：消息加解密密钥，同样不应下发（处理方式同 appSecret） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "encoding_aes_key")
    private String encodingAesKey;

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "access_token_expires_at")
    private LocalDateTime accessTokenExpiresAt;

    @Column(name = "ai_enabled")
    private Boolean aiEnabled = true;

    @Column(name = "default_reply", length = 500)
    private String defaultReply = "正在转接人工客服";

    @Column(name = "status")
    private Integer status = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void encryptBeforeSave() {
        if (appSecret != null && !appSecret.isEmpty() && !AesUtil.isEncrypted(appSecret)) {
            appSecret = AES.encrypt(appSecret);
        }
        if (encodingAesKey != null && !encodingAesKey.isEmpty() && !AesUtil.isEncrypted(encodingAesKey)) {
            encodingAesKey = AES.encrypt(encodingAesKey);
        }
    }

    @PostLoad
    protected void decryptAfterLoad() {
        // AesUtil.decrypt 对无前缀的历史明文会原样返回，因此旧数据无需特殊处理
        if (appSecret != null && !appSecret.isEmpty()) {
            try {
                appSecret = AES.decrypt(appSecret);
            } catch (Exception e) {
                // 兼容未加密的旧数据
            }
        }
        if (encodingAesKey != null && !encodingAesKey.isEmpty()) {
            try {
                encodingAesKey = AES.decrypt(encodingAesKey);
            } catch (Exception e) {
                // 兼容未加密的旧数据
            }
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEncodingAesKey() {
        return encodingAesKey;
    }

    public void setEncodingAesKey(String encodingAesKey) {
        this.encodingAesKey = encodingAesKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public LocalDateTime getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public void setAccessTokenExpiresAt(LocalDateTime accessTokenExpiresAt) {
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public Boolean getAiEnabled() {
        return aiEnabled;
    }

    public void setAiEnabled(Boolean aiEnabled) {
        this.aiEnabled = aiEnabled;
    }

    public String getDefaultReply() {
        return defaultReply;
    }

    public void setDefaultReply(String defaultReply) {
        this.defaultReply = defaultReply;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
