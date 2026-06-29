-- 跨版本不变的插件元信息
CREATE TABLE plugin (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    package_name  VARCHAR(214) NOT NULL,
    plugin_name   VARCHAR(128) NOT NULL,
    description   VARCHAR(1024),
    owner_team    VARCHAR(128),
    PRIMARY KEY (id),
    UNIQUE KEY uk_plugin_package (package_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 每个已发布版本一行,产物指针 + 完整性哈希
CREATE TABLE plugin_version (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    plugin_id     BIGINT       NOT NULL,
    version       VARCHAR(64)  NOT NULL,
    tarball_ref   VARCHAR(255) NOT NULL,
    integrity     VARCHAR(160) NOT NULL,
    shasum        VARCHAR(64)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    uploaded_by   VARCHAR(128) NOT NULL,
    published_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_version_plugin_ver (plugin_id, version),
    KEY idx_version_plugin (plugin_id),
    CONSTRAINT fk_version_plugin FOREIGN KEY (plugin_id) REFERENCES plugin (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 可移动的发布渠道指针
CREATE TABLE dist_tag (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    plugin_id  BIGINT      NOT NULL,
    tag        VARCHAR(32) NOT NULL,
    version    VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_disttag_plugin_tag (plugin_id, tag),
    CONSTRAINT fk_disttag_plugin FOREIGN KEY (plugin_id) REFERENCES plugin (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 上架审核状态机
CREATE TABLE submission (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    package_name  VARCHAR(214) NOT NULL,
    version       VARCHAR(64)  NOT NULL,
    plugin_name   VARCHAR(128) NOT NULL,
    description   VARCHAR(1024),
    tarball_ref   VARCHAR(255) NOT NULL,
    integrity     VARCHAR(160) NOT NULL,
    shasum        VARCHAR(64)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    state         VARCHAR(16)  NOT NULL,
    submitter     VARCHAR(128) NOT NULL,
    reviewer      VARCHAR(128),
    review_notes  VARCHAR(2048),
    lock_version  BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_submission_state (state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 来自 OIDC 的最小用户表
CREATE TABLE app_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    subject     VARCHAR(255) NOT NULL,
    email       VARCHAR(255),
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_subject (subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 本地角色表(AUTHOR / ADMIN),admin 维护
CREATE TABLE user_role (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    user_id  BIGINT      NOT NULL,
    role     VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_userrole (user_id, role),
    CONSTRAINT fk_userrole_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- registry 只读 token(只存 sha256 hash)
CREATE TABLE registry_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash  VARCHAR(64)  NOT NULL,
    label       VARCHAR(128) NOT NULL,
    created_by  VARCHAR(128) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    revoked     BIT(1)       NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_token_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
