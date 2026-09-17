-- =============================================================
-- XSY-SCM 订单 P0 建表脚本
-- 依据：docs/requirement/04-订单管理.md + 已确认口径（见需求总览 §13）
-- 关键点：订单明细保存成交价快照；最终金额 HALF_UP 到分（G-04）；应收在签收后生成（09-01）
-- =============================================================

-- ----------------------------
-- 订单主表
-- ----------------------------
DROP TABLE IF EXISTS `t_order`;
CREATE TABLE `t_order` (
  `order_id`             BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no`             VARCHAR(32)    NOT NULL COMMENT '订单号，XSD + yyyyMMdd + 4 位流水（G-01）',
  `customer_id`          BIGINT         NOT NULL COMMENT '下单客户 ID（下属单位下单时为本单位）',
  `settle_customer_id`   BIGINT         NOT NULL COMMENT '结算客户 ID（集团统一结算时为集团客户）',
  `source`               TINYINT        NOT NULL DEFAULT 1 COMMENT '来源：1 商城下单，2 后台录单，3 补单',
  `settle_type`          TINYINT        NOT NULL DEFAULT 1 COMMENT '结算方式：1 账期支付，2 货到付款，3 在线支付，4 余额充值',
  `total_amount`         DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '下单金额（不含税，快照价计算）',
  `discount_amount`      DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额（不含税）',
  `payable_amount`       DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '应付金额（不含税）',
  `actual_amount`        DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '核算金额（不含税，按实重核算后）',
  `pay_status`           TINYINT        NOT NULL DEFAULT 1 COMMENT '支付状态：1 未付，2 部分支付，3 已付',
  `expect_delivery_time` DATETIME                DEFAULT NULL COMMENT '期望配送时间',
  `seller_id`            BIGINT                  DEFAULT NULL COMMENT '归属业务员 ID',
  `status`               TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 草稿，2 待确认，3 已确认，4 采购中，5 待分拣，6 分拣中，7 配送中，8 已签收，9 已完成，10 退款中，11 已取消，12 已作废',
  `create_time`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`         TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是（订单不允许物理删除）',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_settle_customer_id` (`settle_customer_id`),
  KEY `idx_status` (`status`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '订单主表';

-- ----------------------------
-- 订单明细（含价格快照与实重）
-- ----------------------------
DROP TABLE IF EXISTS `t_order_item`;
CREATE TABLE `t_order_item` (
  `item_id`         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`        BIGINT         NOT NULL COMMENT '订单 ID',
  `product_id`      BIGINT         NOT NULL COMMENT '商品 ID',
  `sku_id`          BIGINT                  DEFAULT NULL COMMENT '规格 ID',
  `quantity`        DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '下单数量',
  `snapshot_price`  DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '成交价快照（不含税），下单时锁定，后续改价不影响',
  `price_type`      TINYINT                 DEFAULT NULL COMMENT '取价类型：1 基础价，2 客户分级价，3 时价，4 协议价',
  `actual_weight`   DECIMAL(18, 3)          DEFAULT NULL COMMENT '实际重量（kg），分拣/收货后回写',
  `actual_quantity` DECIMAL(18, 3)          DEFAULT NULL COMMENT '实际数量',
  `item_amount`     DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '明细核算金额（不含税，按实重计算）',
  `status`          TINYINT        NOT NULL DEFAULT 1 COMMENT '明细状态：1 正常，2 已退款，3 已退货',
  `create_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`    TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`item_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '订单明细';

-- ----------------------------
-- 订单操作日志（日志类表，不做逻辑删除）
-- ----------------------------
DROP TABLE IF EXISTS `t_order_log`;
CREATE TABLE `t_order_log` (
  `log_id`       BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`     BIGINT   NOT NULL COMMENT '订单 ID',
  `operate_type` TINYINT  NOT NULL COMMENT '操作类型：1 创建，2 确认，3 改价，4 编辑，5 取消，6 发货，7 签收，8 核算，9 退款，10 作废',
  `before_value` TEXT              COMMENT '修改前值',
  `after_value`  TEXT              COMMENT '修改后值',
  `operate_by`   BIGINT            DEFAULT NULL COMMENT '操作人',
  `operate_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_operate_time` (`operate_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '订单操作日志';

-- ----------------------------
-- 退款单
-- ----------------------------
DROP TABLE IF EXISTS `t_refund`;
CREATE TABLE `t_refund` (
  `refund_id`     BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `refund_no`     VARCHAR(32)    NOT NULL COMMENT '退款单号，TKD + yyyyMMdd + 4 位流水（G-01）',
  `order_id`      BIGINT         NOT NULL COMMENT '订单 ID',
  `item_id`       BIGINT                  DEFAULT NULL COMMENT '订单明细 ID，整单退款时为空',
  `refund_type`   TINYINT        NOT NULL DEFAULT 1 COMMENT '退款类型：1 仅退款，2 退货退款',
  `refund_amount` DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '退款金额（不含税）',
  `refund_reason` VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '退款原因',
  `status`        TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 待审核，2 已通过，3 已退款，4 已驳回',
  `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`  TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是（退款记录不允许物理删除）',
  PRIMARY KEY (`refund_id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '退款单';
