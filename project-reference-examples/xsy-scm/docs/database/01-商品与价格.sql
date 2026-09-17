-- =============================================================
-- XSY-SCM 商品与价格 P0 建表脚本
-- 依据：docs/requirement/01-商品管理.md + 已确认口径（见需求总览 §13）
-- 说明：金额 DECIMAL(18,2) 不含税；单价 DECIMAL(18,4)；重量 DECIMAL(18,3) kg
--       不使用物理外键；业务表统一逻辑删除
-- =============================================================

-- ----------------------------
-- 商品分类（三级）
-- ----------------------------
DROP TABLE IF EXISTS `t_product_category`;
CREATE TABLE `t_product_category` (
  `category_id`   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id`     BIGINT      NOT NULL DEFAULT 0 COMMENT '父级ID，一级为 0',
  `category_name` VARCHAR(64) NOT NULL COMMENT '分类名称',
  `level`         TINYINT     NOT NULL DEFAULT 1 COMMENT '层级：1 一级，2 二级，3 三级',
  `sort`          INT         NOT NULL DEFAULT 0 COMMENT '排序，升序',
  `status`        TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 启用，2 停用',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`  TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`category_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品分类';

-- ----------------------------
-- 商品 SPU
-- ----------------------------
DROP TABLE IF EXISTS `t_product`;
CREATE TABLE `t_product` (
  `product_id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_no`          VARCHAR(32)   NOT NULL COMMENT '商品编码，SP + 6 位流水（G-01）',
  `category_id`         BIGINT        NOT NULL COMMENT '三级分类 ID',
  `product_name`        VARCHAR(128)  NOT NULL COMMENT '商品名称',
  `product_type`        TINYINT       NOT NULL DEFAULT 1 COMMENT '商品类型：1 标品，2 非标品',
  `measure_type`        TINYINT       NOT NULL DEFAULT 1 COMMENT '计量方式：1 按件，2 按重',
  `base_unit`           VARCHAR(16)   NOT NULL DEFAULT '' COMMENT '基本单位，如 件 / kg',
  `spec_flag`           TINYINT       NOT NULL DEFAULT 0 COMMENT '是否多规格：0 否，1 是',
  `purchase_mode`       TINYINT       NOT NULL DEFAULT 1 COMMENT '采购方式：1 自采，2 供应商送货',
  `default_supplier_id` BIGINT                 DEFAULT NULL COMMENT '默认供应商 ID',
  `default_buyer_id`    BIGINT                 DEFAULT NULL COMMENT '默认采购员 ID',
  `detail`              TEXT                   COMMENT '商品详情',
  `main_image`          VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '主图（文件服务地址，不硬编码公网 URL）',
  `status`              TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1 草稿，2 待上架，3 已上架，4 已下架，5 已作废',
  `create_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_product_no` (`product_no`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_product_name` (`product_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品 SPU';

-- ----------------------------
-- 商品规格 SKU
-- ----------------------------
DROP TABLE IF EXISTS `t_product_sku`;
CREATE TABLE `t_product_sku` (
  `sku_id`       BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id`   BIGINT         NOT NULL COMMENT '所属商品 ID',
  `sku_no`       VARCHAR(32)    NOT NULL COMMENT '规格编码',
  `spec_name`    VARCHAR(128)   NOT NULL DEFAULT '' COMMENT '规格名称，如 规格/单位 组合',
  `unit`         VARCHAR(16)    NOT NULL DEFAULT '' COMMENT '销售单位',
  `unit_weight`  DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '单件折算重量（kg），非标品换算用',
  `status`       TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 启用，2 停用',
  `create_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag` TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`sku_id`),
  UNIQUE KEY `uk_sku_no` (`sku_no`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品规格 SKU';

-- ----------------------------
-- 商品价格（基础价 / 分级价 / 时价 / 协议价）
-- 取价优先级（01-01 已定）：协议价 > 促销价 > 分级价 > 时价 > 基础价，不叠加
-- ----------------------------
DROP TABLE IF EXISTS `t_product_price`;
CREATE TABLE `t_product_price` (
  `price_id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id`         BIGINT         NOT NULL COMMENT '商品 ID',
  `sku_id`             BIGINT                  DEFAULT NULL COMMENT '规格 ID，为空表示按商品定价',
  `price_type`         TINYINT        NOT NULL COMMENT '价格类型：1 基础价，2 客户分级价，3 时价，4 协议价',
  `customer_level_id`  BIGINT                  DEFAULT NULL COMMENT '客户分级 ID，分级价使用',
  `customer_id`        BIGINT                  DEFAULT NULL COMMENT '指定客户 ID，协议价使用',
  `price`              DECIMAL(18, 4) NOT NULL COMMENT '单价（不含税）',
  `effective_time`     DATETIME                DEFAULT NULL COMMENT '生效时间，时价使用',
  `expire_time`        DATETIME                DEFAULT NULL COMMENT '失效时间',
  `status`             TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 生效，2 失效',
  `create_time`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`       TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`price_id`),
  KEY `idx_product_sku` (`product_id`, `sku_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_price_type_status` (`price_type`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品价格';

-- ----------------------------
-- 商品-供应商关系（多供应商）
-- ----------------------------
DROP TABLE IF EXISTS `t_product_supplier`;
CREATE TABLE `t_product_supplier` (
  `id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id`   BIGINT         NOT NULL COMMENT '商品 ID',
  `supplier_id`  BIGINT         NOT NULL COMMENT '供应商 ID',
  `supply_price` DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '供应价（不含税）',
  `default_flag` TINYINT        NOT NULL DEFAULT 0 COMMENT '是否默认供应商：0 否，1 是',
  `create_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag` TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_supplier` (`product_id`, `supplier_id`),
  KEY `idx_supplier_id` (`supplier_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品-供应商关系';
