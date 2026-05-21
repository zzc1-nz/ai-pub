package com.ai.pub.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "wechat_material")
public class WechatMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    private WechatAccount account;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "media_id", length = 100)
    private String mediaId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "thumb_media_id", length = 100)
    private String thumbMediaId;

    @Column(name = "author", length = 50)
    private String author;

    @Column(name = "digest", length = 200)
    private String digest;

    @Column(name = "show_cover_pic")
    private Integer showCoverPic = 0;

    @Column(name = "content_source_url", length = 500)
    private String contentSourceUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public WechatAccount getAccount() { return account; }
    public void setAccount(WechatAccount account) { this.account = account; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMediaId() { return mediaId; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getThumbMediaId() { return thumbMediaId; }
    public void setThumbMediaId(String thumbMediaId) { this.thumbMediaId = thumbMediaId; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getDigest() { return digest; }
    public void setDigest(String digest) { this.digest = digest; }
    public Integer getShowCoverPic() { return showCoverPic; }
    public void setShowCoverPic(Integer showCoverPic) { this.showCoverPic = showCoverPic; }
    public String getContentSourceUrl() { return contentSourceUrl; }
    public void setContentSourceUrl(String contentSourceUrl) { this.contentSourceUrl = contentSourceUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
