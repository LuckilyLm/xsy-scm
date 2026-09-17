-- =============================================================
-- XSY-SCM 采购 P0 建表脚本
-- 依据：docs/requirement/05-采购管理.md + 已确认口径（见需求总览 §13）
-- 关键点：多次收货；实重手工录入（G-05）；收货入库后生成应付（09-02 / 05-07）
-- 说明：供应商编码前缀 GYS 为编号前缀表的补充项，需同步维护到需求总览 §6
-- =============================================================

-- ----------------------------
-- 供应商档案
-- ----------------------------
DROP TABLE IF EXISTS `t_supplier`;
CREATE TABLE `t_supplier` (
  `supplier_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `supplier_no`   VARCHAR(32)  NOT NULL COMMENT '供应商编码，GYS + 6 位流水（G-01 补充前缀）',
  `supplier_name` VARCHAR(128) NOT NULL COMMENT '供应商名称',
  `contact_name`  VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '联系人',
  `contact_phone` VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '联系电话',
  `address`       VARCHAR(255) NOT NULL DEFAULT '' COMMENT '地址',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，2 停用',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`supplier_id`),
  UNIQUE KEY `uk_supplier_no` (`supplier_no`),
  KEY `idx_supplier_name` (`supplier_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '供应商档案';

-- ----------------------------
-- 采购单
-- ----------------------------
DROP TABLE IF EXISTS `t_purchase_order`;
CREATE TABLE `t_purchase_order` (
  `purchase_id`         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `purchase_no`         VARCHAR(32)    NOT NULL COMMENT '采购单号，CGD + yyyyMMdd + 4 位流水（G-01）',
  `supplier_id`         BIGINT         NOT NULL COMMENT '供应商 ID',
  `buyer_id`            BIGINT                  DEFAULT NULL COMMENT '采购员 ID',
  `category_id`         BIGINT                  DEFAULT NULL COMMENT '品类 ID，按品类汇总时使用',
  `total_amount`        DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '采购预估金额（不含税）',
  `actual_amount`       DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '实际采购金额（不含税，按实重收货后）',
  `expect_arrive_time`  DATETIME                DEFAULT NULL COMMENT '期望到货时间',
  `qrcode_url`          VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '采购单二维码（文件服务，不硬编码公网 URL）',
  `status`              TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 待接单，2 采购中，3 部分收货，4 已完成，5 已取消',
  `create_time`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`        TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是（采购单不允许物理删除）',
  PRIMARY KEY (`purchase_id`),
  UNIQUE KEY `uk_purchase_no` (`purchase_no`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_buyer_id` (`buyer_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购单';

-- ----------------------------
-- 采购明细
-- ----------------------------
DROP TABLE IF EXISTS `t_purchase_item`;
CREATE TABLE `t_purchase_item` (
  `item_id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `purchase_id`       BIGINT         NOT NULL COMMENT '采购单 ID',
  `product_id`        BIGINT         NOT NULL COMMENT '商品 ID',
  `sku_id`            BIGINT                  DEFAULT NULL COMMENT '规格 ID',
  `require_quantity`  DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '需求量（订单汇总得出）',
  `purchase_quantity` DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '计划采购量',
  `received_quantity` DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '累计已收数量',
  `unit_price`        DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '采购单价（不含税），可后续回填',
  `status`            TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 待收，2 部分收，3 已收齐',
  `create_time`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`      TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`item_id`),
  KEY `idx_purchase_id` (`purchase_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购明细';

-- ----------------------------
-- 收货单（支持同一采购明细多次收货）
-- ----------------------------
DROP TABLE IF EXISTS `t_receive`;
CREATE TABLE `t_receive` (
  `receive_id`       BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `receive_no`       VARCHAR(32)    NOT NULL COMMENT '收货单号，SHD + yyyyMMdd + 4 位流水（G-01）',
  `purchase_id`      BIGINT         NOT NULL COMMENT '采购单 ID',
  `item_id`          BIGINT         NOT NULL COMMENT '采购明细 ID',
  `receive_quantity` DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '本次收货数量',
  `receive_weight`   DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '本次实收重量（kg），G-05 一期手工录入',
  `unit_price`       DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '本次单价（不含税），可传输回填',
  `receive_by`       BIGINT                  DEFAULT NULL COMMENT '收货人',
  `receive_time`     DATETIME                DEFAULT NULL COMMENT '收货时间',
  `status`           TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 已收，2 已入库，3 已作废（入库后生成应付）',
  `receive_flag`     TINYINT        NOT NULL DEFAULT 1 COMMENT '收货标记：1 正常，2 少收，3 超收（动态记录少多收，Q2）',
  `create_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`     TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`receive_id`),
  UNIQUE KEY `uk_receive_no` (`receive_no`),
  KEY `idx_purchase_id` (`purchase_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '采购收货单';
