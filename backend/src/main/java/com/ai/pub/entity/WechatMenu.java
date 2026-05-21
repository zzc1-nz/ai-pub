package com.ai.pub.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "wechat_menu")
public class WechatMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    private WechatAccount account;

    @Column(name = "parent_id")
    private Long parentId = 0L;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "type", length = 20)
    private String type;

    @Column(name = "key", length = 50)
    private String key;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "appid", length = 100)
    private String appid;

    @Column(name = "pagepath", length = 200)
    private String pagepath;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

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
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getAppid() { return appid; }
    public void setAppid(String appid) { this.appid = appid; }
    public String getPagepath() { return pagepath; }
    public void setPagepath(String pagepath) { this.pagepath = pagepath; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
