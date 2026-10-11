-- ============================================================================
--  AI-Pub 智能公众号管理系统 - 数据库初始化脚本
-- ----------------------------------------------------------------------------
--  说明：
--  1. 本文件由 docker-compose.yml 挂载到 mysql 容器的
--     /docker-entrypoint-initdb.d/ 目录，MySQL 首次初始化数据卷时自动执行。
--     （原仓库中 deploy/schema.sql 误建为空"目录"，导致该挂载完全失效，
--       建表只能依赖 JPA ddl-auto: update，且 sys_user 为空表无法登录。）
--  2. 表结构与 JPA 实体保持一致；使用 IF NOT EXISTS，可安全重复执行。
--  3. 内置管理员账号：admin / admin123456（BCrypt 哈希，与 DataInitializer
--     中的兜底逻辑一致；即使本脚本未执行，后端启动时也会自动补建）。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS `ai_pub`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `ai_pub`;

-- ---------------------------------------------------------------------------
-- 系统用户
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `username`   VARCHAR(50)  NOT NULL,
    `password`   VARCHAR(255) NOT NULL,
    `nickname`   VARCHAR(50)  DEFAULT NULL,
    `role`       VARCHAR(20)  DEFAULT 'OPERATOR',
    `status`     INT          DEFAULT 1,
    `created_at` DATETIME(6)  DEFAULT NULL,
    `updated_at` DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 公众号配置
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wechat_account` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT,
    `name`                    VARCHAR(100) NOT NULL,
    `app_id`                  VARCHAR(100) NOT NULL,
    `app_secret`              VARCHAR(255) NOT NULL,
    `token`                   VARCHAR(100) NOT NULL,
    `encoding_aes_key`        VARCHAR(255) DEFAULT NULL,
    `access_token`            TEXT         DEFAULT NULL,
    `access_token_expires_at` DATETIME(6)  DEFAULT NULL,
    `ai_enabled`              BIT(1)       DEFAULT b'1',
    `default_reply`           VARCHAR(500) DEFAULT '正在转接人工客服',
    `status`                  INT          DEFAULT 1,
    `created_at`              DATETIME(6)  DEFAULT NULL,
    `updated_at`              DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wechat_account_app_id` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 自定义菜单
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wechat_menu` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `account_id` BIGINT       NOT NULL,
    `parent_id`  BIGINT       DEFAULT 0,
    `name`       VARCHAR(50)  NOT NULL,
    `type`       VARCHAR(20)  DEFAULT NULL,
    -- 注意：列名就是 MySQL 保留字 key，必须加反引号；与 WechatMenu.java 的
    -- @Column(name = "key") 一致。测试用 H2 也专门配了 NON_KEYWORDS=KEY。
    `key`        VARCHAR(50)  DEFAULT NULL,
    `url`        VARCHAR(500) DEFAULT NULL,
    `appid`      VARCHAR(100) DEFAULT NULL,
    `pagepath`   VARCHAR(200) DEFAULT NULL,
    `sort_order` INT          DEFAULT 0,
    `created_at` DATETIME(6)  DEFAULT NULL,
    `updated_at` DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_wechat_menu_account` (`account_id`),
    KEY `idx_wechat_menu_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 素材
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wechat_material` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `account_id`         BIGINT       NOT NULL,
    `type`               VARCHAR(20)  NOT NULL,
    `media_id`           VARCHAR(100) DEFAULT NULL,
    `title`              VARCHAR(200) DEFAULT NULL,
    `description`        TEXT         DEFAULT NULL,
    `url`                VARCHAR(500) DEFAULT NULL,
    `content`            TEXT         DEFAULT NULL,
    `thumb_media_id`     VARCHAR(100) DEFAULT NULL,
    `author`             VARCHAR(50)  DEFAULT NULL,
    `digest`             VARCHAR(200) DEFAULT NULL,
    `show_cover_pic`     INT          DEFAULT 0,
    `content_source_url` VARCHAR(500) DEFAULT NULL,
    `created_at`         DATETIME(6)  DEFAULT NULL,
    `updated_at`         DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_wechat_material_account` (`account_id`),
    KEY `idx_wechat_material_type` (`account_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 消息记录
-- 注：reply_type 由 MessageConsumer 写入（KEYWORD/AI/DEFAULT）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wechat_message` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `account_id`    BIGINT       NOT NULL,
    `msg_id`        VARCHAR(100) DEFAULT NULL,
    `to_user_name`  VARCHAR(100) DEFAULT NULL,
    `openid`        VARCHAR(100) NOT NULL,
    `msg_type`      VARCHAR(20)  NOT NULL,
    `event_type`    VARCHAR(50)  DEFAULT NULL,
    `content`       TEXT         DEFAULT NULL,
    `media_id`      VARCHAR(100) DEFAULT NULL,
    `pic_url`       VARCHAR(500) DEFAULT NULL,
    `format`        VARCHAR(50)  DEFAULT NULL,
    `recognition`   VARCHAR(500) DEFAULT NULL,
    `reply_type`    VARCHAR(20)  DEFAULT NULL,
    `reply_content` TEXT         DEFAULT NULL,
    `created_at`    DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_wechat_message_account_created` (`account_id`, `created_at`),
    KEY `idx_wechat_message_account_openid` (`account_id`, `openid`),
    KEY `idx_wechat_message_msg_id` (`msg_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 自动回复规则
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `wechat_reply_rule` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `account_id`         BIGINT       NOT NULL,
    `name`               VARCHAR(100) NOT NULL,
    `keyword`            VARCHAR(500) NOT NULL,
    `match_type`         VARCHAR(20)  DEFAULT 'CONTAINS',
    `reply_type`         VARCHAR(20)  NOT NULL,
    `reply_content`      TEXT         DEFAULT NULL,
    `reply_material_id`  BIGINT       DEFAULT NULL,
    `sort_order`         INT          DEFAULT 0,
    `status`             INT          DEFAULT 1,
    `created_at`         DATETIME(6)  DEFAULT NULL,
    `updated_at`         DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_wechat_reply_rule_account` (`account_id`),
    KEY `idx_wechat_reply_rule_active` (`account_id`, `status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 初始数据
-- ---------------------------------------------------------------------------

-- 管理员账号：admin / admin123456
-- 哈希由 BCryptPasswordEncoder(strength=10) 实际生成并验证通过。
-- ON DUPLICATE KEY UPDATE 保证脚本可重复执行且不会覆盖已改过的密码。
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `role`, `status`, `created_at`, `updated_at`)
VALUES ('admin',
        '$2a$10$sJMqPEShwo175smqeOW87ubKEzxdUDGYIH5sJ6..L8YZyhqAR4v9K',
        '系统管理员', 'ADMIN', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `username` = `username`;
