-- =============================================================
-- XSY-SCM 客户 P0 建表脚本
-- 依据：docs/requirement/02-客户管理.md + 已确认口径（见需求总览 §13）
-- 说明：金额 DECIMAL(18,2) 不含税；单币种 CNY；不使用物理外键
-- =============================================================

-- ----------------------------
-- 客户档案
-- ----------------------------
DROP TABLE IF EXISTS `t_customer`;
CREATE TABLE `t_customer` (
  `customer_id`         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `customer_no`         VARCHAR(32)    NOT NULL COMMENT '客户编码，KH + 6 位流水（G-01）',
  `customer_name`       VARCHAR(128)   NOT NULL COMMENT '客户名称',
  `customer_type`       TINYINT        NOT NULL DEFAULT 1 COMMENT '客户类型：1 企业，2 个人，3 集团',
  `customer_level_id`   BIGINT                  DEFAULT NULL COMMENT '客户分级 ID，影响取价',
  `parent_customer_id`  BIGINT         NOT NULL DEFAULT 0 COMMENT '上级集团客户 ID，独立客户为 0',
  `settle_mode`         TINYINT        NOT NULL DEFAULT 1 COMMENT '结算方式：1 独立结算，2 集团统一结算',
  `seller_id`           BIGINT                  DEFAULT NULL COMMENT '归属业务员 ID',
  `supplier_id`         BIGINT                  DEFAULT NULL COMMENT '绑定供应商 ID',
  `contact_name`        VARCHAR(32)    NOT NULL DEFAULT '' COMMENT '联系人',
  `contact_phone`       VARCHAR(32)    NOT NULL DEFAULT '' COMMENT '联系电话',
  `address`             VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '地址，配送使用',
  `longitude`           DECIMAL(10, 7)          DEFAULT NULL COMMENT '经度，线路规划使用',
  `latitude`            DECIMAL(10, 7)          DEFAULT NULL COMMENT '纬度，线路规划使用',
  `balance`             DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '余额账户（不含税），余额充值结算使用',
  `credit_amount`       DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '授信额度（不含税），账期按金额时使用',
  `status`              TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 潜在，2 合作中，3 暂停合作，4 黑名单',
  `create_time`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`        TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`customer_id`),
  UNIQUE KEY `uk_customer_no` (`customer_no`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_status` (`status`),
  KEY `idx_parent_customer_id` (`parent_customer_id`),
  KEY `idx_customer_name` (`customer_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户档案';

-- ----------------------------
-- 客户账期
-- ----------------------------
DROP TABLE IF EXISTS `t_customer_period`;
CREATE TABLE `t_customer_period` (
  `period_id`        BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `customer_id`      BIGINT         NOT NULL COMMENT '客户 ID',
  `period_type`      TINYINT        NOT NULL DEFAULT 2 COMMENT '账期类型：1 按金额，2 按时间',
  `amount_threshold` DECIMAL(18, 2)          DEFAULT NULL COMMENT '金额阈值（不含税），按金额时使用',
  `period_value`     INT                     DEFAULT NULL COMMENT '账期值，按时间时使用',
  `period_unit`      TINYINT                 DEFAULT NULL COMMENT '账期单位：1 天，2 月',
  `settle_day`       TINYINT                 DEFAULT NULL COMMENT '固定结算日，按月时使用',
  `status`           TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 生效，2 暂停',
  `create_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`     TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`period_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户账期';

-- ----------------------------
-- 客户商品可见性（指定客户显示 / 屏蔽商品）
-- ----------------------------
DROP TABLE IF EXISTS `t_customer_goods_visible`;
CREATE TABLE `t_customer_goods_visible` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `customer_id`  BIGINT   NOT NULL COMMENT '客户 ID',
  `product_id`   BIGINT   NOT NULL COMMENT '商品 ID',
  `visible_type` TINYINT  NOT NULL DEFAULT 1 COMMENT '可见性：1 显示，2 屏蔽',
  `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag` TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_product` (`customer_id`, `product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户商品可见性';

-- ----------------------------
-- 业务员推广二维码
-- ----------------------------
DROP TABLE IF EXISTS `t_customer_qrcode`;
CREATE TABLE `t_customer_qrcode` (
  `qrcode_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `seller_id`    BIGINT       NOT NULL COMMENT '业务员 ID',
  `qrcode_url`   VARCHAR(255) NOT NULL DEFAULT '' COMMENT '二维码地址（文件服务，不硬编码公网 URL）',
  `scan_count`   INT          NOT NULL DEFAULT 0 COMMENT '扫描次数',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag` TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`qrcode_id`),
  KEY `idx_seller_id` (`seller_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '业务员推广二维码';
