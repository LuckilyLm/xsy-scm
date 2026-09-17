-- =============================================================
-- XSY-SCM 库存 P0 建表脚本
-- 依据：docs/requirement/06-库存管理.md + 已确认口径（见需求总览 §13）
-- 关键点：余额 + 流水，禁止直接 UPDATE 余额；加权平均每次入库实时重算（06-04）；
--        单仓库、不启用批次（G-03），warehouse_id / batch_id 保留备用
-- =============================================================

-- ----------------------------
-- 库存余额（由流水推导，禁止业务层直接 UPDATE）
-- ----------------------------
DROP TABLE IF EXISTS `t_stock_balance`;
CREATE TABLE `t_stock_balance` (
  `balance_id`   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id`   BIGINT         NOT NULL COMMENT '商品 ID',
  `sku_id`       BIGINT         NOT NULL COMMENT '规格 ID',
  `warehouse_id` BIGINT         NOT NULL DEFAULT 1 COMMENT '仓库 ID，G-03 单仓库，字段保留备用',
  `batch_id`     BIGINT                  DEFAULT NULL COMMENT '批次 ID，G-03 不启用批次，字段保留备用',
  `quantity`     DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '当前数量',
  `weight`       DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '当前重量（kg）',
  `avg_cost`     DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '加权平均成本单价（不含税），每次入库实时重算',
  `total_cost`   DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '结存总成本（不含税）',
  `warn_min`     DECIMAL(18, 3)          DEFAULT NULL COMMENT '预警下限',
  `warn_max`     DECIMAL(18, 3)          DEFAULT NULL COMMENT '预警上限',
  `create_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag` TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`balance_id`),
  UNIQUE KEY `uk_sku_warehouse` (`sku_id`, `warehouse_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存余额';

-- ----------------------------
-- 库存流水（流水类表，不做逻辑删除，冲销用反向流水）
-- ----------------------------
DROP TABLE IF EXISTS `t_stock_flow`;
CREATE TABLE `t_stock_flow` (
  `flow_id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `flow_no`          VARCHAR(64)    NOT NULL COMMENT '流水号',
  `product_id`       BIGINT         NOT NULL COMMENT '商品 ID',
  `sku_id`           BIGINT         NOT NULL COMMENT '规格 ID',
  `warehouse_id`     BIGINT         NOT NULL DEFAULT 1 COMMENT '仓库 ID，G-03 单仓库，字段保留备用',
  `batch_id`         BIGINT                  DEFAULT NULL COMMENT '批次 ID，G-03 不启用，字段保留备用',
  `flow_type`        TINYINT        NOT NULL COMMENT '流水类型：1 采购入库，2 销售出库，3 退货入库，4 报损，5 报溢，6 盘点调整，7 规格转换出，8 规格转换入',
  `biz_type`         TINYINT        NOT NULL COMMENT '关联业务：1 采购，2 订单，3 分拣，4 盘点，5 报损报溢，6 规格转换',
  `biz_id`           BIGINT                  DEFAULT NULL COMMENT '关联业务单 ID',
  `direction`        TINYINT        NOT NULL COMMENT '方向：1 入，2 出',
  `quantity`         DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '变动数量（正数）',
  `weight`           DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '变动重量（kg，正数）',
  `unit_price`       DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '变动单价（不含税）',
  `amount`           DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '变动金额（不含税）',
  `before_quantity`  DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '变动前数量',
  `after_quantity`   DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '变动后数量',
  `before_avg_cost`  DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '变动前加权平均成本',
  `after_avg_cost`   DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '变动后加权平均成本',
  `operate_by`       BIGINT                  DEFAULT NULL COMMENT '操作人',
  `operate_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  `create_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`flow_id`),
  KEY `idx_product_sku` (`product_id`, `sku_id`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_operate_time` (`operate_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存流水';

-- ----------------------------
-- 盘点单
-- ----------------------------
DROP TABLE IF EXISTS `t_stock_check`;
CREATE TABLE `t_stock_check` (
  `check_id`     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `check_no`     VARCHAR(32) NOT NULL COMMENT '盘点单号，PDD + yyyyMMdd + 4 位流水（G-01）',
  `warehouse_id` BIGINT      NOT NULL DEFAULT 1 COMMENT '仓库 ID，G-03 单仓库，字段保留备用',
  `check_type`   TINYINT     NOT NULL DEFAULT 1 COMMENT '盘点类型：1 全盘，2 抽盘',
  `status`       TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 待盘点，2 盘点中，3 已完成，4 已取消',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag` TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`check_id`),
  UNIQUE KEY `uk_check_no` (`check_no`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存盘点单';

-- ----------------------------
-- 盘点明细
-- ----------------------------
DROP TABLE IF EXISTS `t_stock_check_item`;
CREATE TABLE `t_stock_check_item` (
  `item_id`         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `check_id`        BIGINT         NOT NULL COMMENT '盘点单 ID',
  `product_id`      BIGINT         NOT NULL COMMENT '商品 ID',
  `sku_id`          BIGINT         NOT NULL COMMENT '规格 ID',
  `book_quantity`   DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '账面数量',
  `book_weight`     DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '账面重量（kg）',
  `actual_quantity` DECIMAL(18, 3)          DEFAULT NULL COMMENT '实盘数量',
  `actual_weight`   DECIMAL(18, 3)          DEFAULT NULL COMMENT '实盘重量（kg）',
  `diff_quantity`   DECIMAL(18, 3)          DEFAULT NULL COMMENT '差异数量（实盘 - 账面）',
  `create_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`    TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`item_id`),
  KEY `idx_check_id` (`check_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存盘点明细';

-- ----------------------------
-- 库存调整单（报损 / 报溢 / 盘点调整 / 规格转换）
-- ----------------------------
DROP TABLE IF EXISTS `t_stock_adjust`;
CREATE TABLE `t_stock_adjust` (
  `adjust_id`    BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `adjust_no`    VARCHAR(32)    NOT NULL COMMENT '调整单号，BSD 报损 / BYD 报溢 / ZHD 转换 + yyyyMMdd + 4 位流水（G-01）',
  `adjust_type`  TINYINT        NOT NULL COMMENT '调整类型：1 报损，2 报溢，3 盘点调整，4 规格转换',
  `product_id`   BIGINT         NOT NULL COMMENT '商品 ID',
  `sku_id`       BIGINT         NOT NULL COMMENT '规格 ID',
  `warehouse_id` BIGINT         NOT NULL DEFAULT 1 COMMENT '仓库 ID，G-03 单仓库，字段保留备用',
  `quantity`     DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '调整数量（正数）',
  `weight`       DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '调整重量（kg，正数）',
  `reason`       VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '调整原因',
  `status`       TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 待审核，2 已完成，3 已驳回',
  `create_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag` TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`adjust_id`),
  UNIQUE KEY `uk_adjust_no` (`adjust_no`),
  KEY `idx_status` (`status`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '库存调整单（报损/报溢/盘点调整/规格转换）';
