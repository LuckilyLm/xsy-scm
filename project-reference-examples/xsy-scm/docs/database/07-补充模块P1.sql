-- =============================================================
-- XSY-SCM 补充模块 P1 建表脚本
-- 依据：docs/requirement/14-对标蔬东坡补充需求规划.md（§3.1 第一批）
-- 参考：蔬东坡 SaaS 更新日志 17.1 / 17.3 / 17.4 / 17.5 / 16.5 / 17.0
-- 口径：金额 DECIMAL(18,2) 不含税；单价 DECIMAL(18,4)；数量/重量 DECIMAL(18,3)、kg
--      单仓库、不启用库存批次（G-03）；不使用物理外键；编号走编号生成器（G-01）
-- 说明：本文件仅覆盖 P1 新增表与原表变更，落地前需与 01~05 P0 脚本一并评审
-- =============================================================

-- =============================================================
-- 一、模块 14 供应商协同（14 供应商协同）
-- =============================================================

-- ----------------------------
-- 供应商账号
-- ----------------------------
DROP TABLE IF EXISTS `t_supplier_account`;
CREATE TABLE `t_supplier_account` (
  `account_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `supplier_id`   BIGINT       NOT NULL COMMENT '供应商 ID',
  `account`       VARCHAR(64)  NOT NULL COMMENT '登录账号',
  `mobile`        VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '手机号',
  `password`      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '密码（加密存储，禁止明文）',
  `openid`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '微信 openid（小程序登录用）',
  `login_version` INT          NOT NULL DEFAULT 0 COMMENT '登录态版本号，改密/禁用后 +1，使历史登录态失效（17.4）',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，2 停用',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_account` (`account`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_openid` (`openid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '供应商账号';

-- ----------------------------
-- 供应商商品提报（提报审核后转 t_product 草稿）
-- ----------------------------
DROP TABLE IF EXISTS `t_supplier_product_apply`;
CREATE TABLE `t_supplier_product_apply` (
  `apply_id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `apply_no`            VARCHAR(32)    NOT NULL COMMENT '提报单号，SPB + yyyyMMdd + 4 位流水（G-01）',
  `supplier_id`         BIGINT         NOT NULL COMMENT '供应商 ID',
  `product_name`        VARCHAR(128)   NOT NULL COMMENT '商品名称',
  `alias`               VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '商品别名（16.5，≤20 字）',
  `category_id`         BIGINT                  DEFAULT NULL COMMENT '拟归类 ID',
  `supply_price`        DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '供货价（不含税）',
  `last_purchase_price` DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '最近一次进价（17.5，受权限控制）',
  `image`               VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '商品图片（文件服务）',
  `audit_status`        TINYINT        NOT NULL DEFAULT 1 COMMENT '审核状态：1 待审核，2 已通过，3 已驳回',
  `reject_reason`       VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '驳回原因',
  `product_id`          BIGINT                  DEFAULT NULL COMMENT '审核通过后生成的商品 ID',
  `create_time`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`        TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`apply_id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_audit_status` (`audit_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '供应商商品提报';

-- ----------------------------
-- 供应商厂商信息（对接溯源，资质预警）
-- ----------------------------
DROP TABLE IF EXISTS `t_supplier_manufacturer`;
CREATE TABLE `t_supplier_manufacturer` (
  `manufacturer_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `supplier_id`              BIGINT       NOT NULL COMMENT '供应商 ID',
  `manufacturer_name`        VARCHAR(128) NOT NULL COMMENT '厂商名称',
  `qualification_file`       VARCHAR(255) NOT NULL DEFAULT '' COMMENT '资质证件（文件服务）',
  `qualification_expire_date` DATE                DEFAULT NULL COMMENT '资质到期日期（17.4，支持预警）',
  `inspect_report_file`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '质检报告文件（文件服务）',
  `status`                   TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，2 停用',
  `create_time`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`manufacturer_id`),
  KEY `idx_supplier_id` (`supplier_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '供应商厂商信息';

-- ----------------------------
-- 供应商对账单
-- ----------------------------
DROP TABLE IF EXISTS `t_supplier_statement`;
CREATE TABLE `t_supplier_statement` (
  `statement_id`  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `statement_no`  VARCHAR(32)    NOT NULL COMMENT '对账单号，DZD + yyyyMMdd + 4 位流水（G-01）',
  `supplier_id`   BIGINT         NOT NULL COMMENT '供应商 ID',
  `period_start`  DATE           NOT NULL COMMENT '对账周期开始',
  `period_end`    DATE           NOT NULL COMMENT '对账周期结束',
  `total_amount`  DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '对账总金额（不含税）',
  `paid_amount`   DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '已结算金额（不含税）',
  `status`        TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 待供应商确认，2 供应商已确认，3 已结算，4 已驳回',
  `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`  TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`statement_id`),
  UNIQUE KEY `uk_statement_no` (`statement_no`),
  KEY `idx_supplier_id` (`supplier_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '供应商对账单';

-- =============================================================
-- 二、采购询价报价（P1-2，归属 05 采购）
-- =============================================================

-- ----------------------------
-- 询价单
-- ----------------------------
DROP TABLE IF EXISTS `t_inquiry`;
CREATE TABLE `t_inquiry` (
  `inquiry_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `inquiry_no`   VARCHAR(32)  NOT NULL COMMENT '询价单号，XJD + yyyyMMdd + 4 位流水（G-01）',
  `inquiry_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '询价单名称',
  `valid_start`  DATETIME     NOT NULL COMMENT '询价有效开始时间（16.8）',
  `valid_end`    DATETIME     NOT NULL COMMENT '询价有效结束时间（16.8）',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 待报价，2 报价中，3 已完成，4 已取消',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag` TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`inquiry_id`),
  UNIQUE KEY `uk_inquiry_no` (`inquiry_no`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '询价单';

-- ----------------------------
-- 询价明细（按商品）
-- ----------------------------
DROP TABLE IF EXISTS `t_inquiry_item`;
CREATE TABLE `t_inquiry_item` (
  `item_id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `inquiry_id`        BIGINT         NOT NULL COMMENT '询价单 ID',
  `product_id`        BIGINT         NOT NULL COMMENT '商品 ID',
  `sku_id`            BIGINT                  DEFAULT NULL COMMENT '规格 ID',
  `require_quantity`  DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '询价数量',
  `create_time`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`      TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`item_id`),
  KEY `idx_inquiry_id` (`inquiry_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '询价明细';

-- ----------------------------
-- 供应商报价（同一明细多供应商报价，用于评分与平均价/中位价对比）
-- ----------------------------
DROP TABLE IF EXISTS `t_inquiry_quote`;
CREATE TABLE `t_inquiry_quote` (
  `quote_id`     BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `inquiry_id`   BIGINT         NOT NULL COMMENT '询价单 ID',
  `item_id`      BIGINT         NOT NULL COMMENT '询价明细 ID',
  `supplier_id`  BIGINT         NOT NULL COMMENT '供应商 ID',
  `quote_price`  DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '报价（不含税）',
  `quote_time`   DATETIME                DEFAULT NULL COMMENT '报价时间',
  `score`        DECIMAL(5, 2)  NOT NULL DEFAULT 0.00 COMMENT '供应商综合评分（16.5，权重加权，0~100）',
  `create_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag` TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`quote_id`),
  UNIQUE KEY `uk_item_supplier` (`item_id`, `supplier_id`),
  KEY `idx_inquiry_id` (`inquiry_id`),
  KEY `idx_supplier_id` (`supplier_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '供应商报价';

-- =============================================================
-- 三、商品转换单（P1-3，归属 06 库存；一品转多品）
-- =============================================================

-- ----------------------------
-- 商品转换单
-- ----------------------------
DROP TABLE IF EXISTS `t_product_convert`;
CREATE TABLE `t_product_convert` (
  `convert_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `convert_no`   VARCHAR(32)  NOT NULL COMMENT '转换单号，ZHD + yyyyMMdd + 4 位流水（G-01）',
  `convert_type` TINYINT      NOT NULL DEFAULT 1 COMMENT '转换类型：1 整件拆零，2 组合拆分',
  `warehouse_id` BIGINT       NOT NULL DEFAULT 1 COMMENT '仓库 ID（G-03 单仓库，字段保留）',
  `source_type`  TINYINT      NOT NULL DEFAULT 1 COMMENT '来源：1 手工创建，2 发货差异表批量转换（17.1）',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 待审核，2 已完成，3 已驳回',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag` TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`convert_id`),
  UNIQUE KEY `uk_convert_no` (`convert_no`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品转换单';

-- ----------------------------
-- 商品转换明细（原商品 1 行 → 目标商品 N 行）
-- ----------------------------
DROP TABLE IF EXISTS `t_product_convert_item`;
CREATE TABLE `t_product_convert_item` (
  `item_id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `convert_id`        BIGINT         NOT NULL COMMENT '转换单 ID',
  `source_product_id` BIGINT         NOT NULL COMMENT '原商品 ID',
  `source_sku_id`     BIGINT                  DEFAULT NULL COMMENT '原规格 ID',
  `source_quantity`   DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '出库数量（扣减原商品）',
  `source_weight`     DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '出库重量（kg）',
  `target_product_id` BIGINT         NOT NULL COMMENT '目标商品 ID（17.3 一品转多品）',
  `target_sku_id`     BIGINT                  DEFAULT NULL COMMENT '目标规格 ID',
  `target_quantity`   DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '入库数量',
  `target_weight`     DECIMAL(18, 3) NOT NULL DEFAULT 0.000 COMMENT '入库重量（kg）',
  `target_unit_price` DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '入库单价（不含税），生成新商品成本',
  `target_amount`     DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '入库金额（不含税）',
  `create_time`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`      TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`item_id`),
  KEY `idx_convert_id` (`convert_id`),
  KEY `idx_source_product_id` (`source_product_id`),
  KEY `idx_target_product_id` (`target_product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品转换明细';

-- =============================================================
-- 四、客户折扣率 / 计算折前价（P1-4，归属 01 / 02）
-- =============================================================

-- ----------------------------
-- 客户折扣率（统一折扣 / 按商品 / 按分类 三维度）
-- ----------------------------
DROP TABLE IF EXISTS `t_customer_discount`;
CREATE TABLE `t_customer_discount` (
  `discount_id`   BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `customer_id`   BIGINT         NOT NULL COMMENT '客户 ID',
  `scope_type`    TINYINT        NOT NULL DEFAULT 1 COMMENT '折扣范围：1 统一折扣，2 按商品，3 按分类',
  `product_id`    BIGINT                  DEFAULT NULL COMMENT '商品 ID（scope_type=2 时使用）',
  `category_id`   BIGINT                  DEFAULT NULL COMMENT '分类 ID（scope_type=3 时使用）',
  `discount_rate` DECIMAL(18, 4) NOT NULL DEFAULT 1.0000 COMMENT '折扣率（0~1，1 表示不打折）',
  `status`        TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 生效，2 停用',
  `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`  TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`discount_id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_category_id` (`category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户折扣率（计算折前价）';

-- =============================================================
-- 五、客户商品别名（P1-7，归属 02 / 11）
-- =============================================================

-- ----------------------------
-- 客户商品别名（含别名描述 / 副别名描述）
-- ----------------------------
DROP TABLE IF EXISTS `t_customer_product_alias`;
CREATE TABLE `t_customer_product_alias` (
  `alias_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `customer_id`       BIGINT       NOT NULL COMMENT '客户 ID',
  `product_id`        BIGINT       NOT NULL COMMENT '商品 ID',
  `sku_id`            BIGINT                DEFAULT NULL COMMENT '规格 ID',
  `alias_name`        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '别名',
  `alias_desc`        VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '别名描述（17.4，≤50 字）',
  `sub_alias_name`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '副别名',
  `sub_alias_desc`    VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '副别名描述（17.4，≤50 字）',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`alias_id`),
  UNIQUE KEY `uk_customer_product_sku` (`customer_id`, `product_id`, `sku_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户商品别名';

-- =============================================================
-- 六、商品条码（P1-5，归属 07 / 12 扫码作业）
-- =============================================================

-- ----------------------------
-- 商品条码（一商品多单位多码）
-- ----------------------------
DROP TABLE IF EXISTS `t_product_barcode`;
CREATE TABLE `t_product_barcode` (
  `barcode_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id`   BIGINT       NOT NULL COMMENT '商品 ID',
  `sku_id`       BIGINT                DEFAULT NULL COMMENT '规格 ID',
  `barcode`      VARCHAR(64)  NOT NULL COMMENT '条形码（唯一）',
  `unit`         VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '对应单位',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag` TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`barcode_id`),
  UNIQUE KEY `uk_barcode` (`barcode`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商品条码';

-- =============================================================
-- 七、原表变更（P1-6 无单收货）
-- =============================================================

-- 收货单支持「无单收货」：放宽采购单/明细必填，新增收货类型与来源
ALTER TABLE `t_receive`
  MODIFY COLUMN `purchase_id` BIGINT DEFAULT NULL COMMENT '采购单 ID（无单收货时为空）',
  MODIFY COLUMN `item_id`     BIGINT DEFAULT NULL COMMENT '采购明细 ID（无单收货时为空）',
  ADD COLUMN `receive_type` TINYINT NOT NULL DEFAULT 1 COMMENT '收货类型：1 采购收货，2 无单收货（17.0）' AFTER `receive_no`,
  ADD COLUMN `supplier_id`  BIGINT  DEFAULT NULL COMMENT '供应商 ID（无单收货时手工选择）' AFTER `receive_type`,
  ADD COLUMN `product_id`   BIGINT  DEFAULT NULL COMMENT '商品 ID（无单收货时使用）' AFTER `item_id`,
  ADD COLUMN `sku_id`       BIGINT  DEFAULT NULL COMMENT '规格 ID（无单收货时使用）' AFTER `product_id`,
  ADD COLUMN `remark`       VARCHAR(255) NOT NULL DEFAULT '' COMMENT '商品备注' AFTER `receive_flag`;

-- 采购明细绑定供应商（供应商分拣分配依据，对标蔬东坡 17.1）
ALTER TABLE `t_purchase_item`
  ADD COLUMN `supplier_id` BIGINT DEFAULT NULL COMMENT '绑定供应商 ID（供应商分拣分配依据）' AFTER `sku_id`;

-- =============================================================
-- 八、打印模板（P1-8，归属 10 后台管理）
-- =============================================================

-- ----------------------------
-- 打印模板（采购单 / 发货单 / 分拣小票 / 询价报价单）
-- ----------------------------
DROP TABLE IF EXISTS `t_print_template`;
CREATE TABLE `t_print_template` (
  `template_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_code` VARCHAR(64)  NOT NULL COMMENT '模板编码（PURCHASE / DELIVERY / SORT_TICKET / INQUIRY）',
  `template_name` VARCHAR(128) NOT NULL COMMENT '模板名称',
  `biz_type`      TINYINT      NOT NULL COMMENT '业务类型：1 采购单，2 发货单，3 分拣小票，4 询价报价单',
  `content`       TEXT         NOT NULL COMMENT '模板内容（HTML，使用 {{key}} 占位）',
  `paper_size`    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '纸张规格（A4 / 58mm / 80mm）',
  `default_flag`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认模板：0 否，1 是',
  `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，2 停用',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`template_id`),
  UNIQUE KEY `uk_template_code` (`template_code`),
  KEY `idx_biz_type` (`biz_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '打印模板';

-- =============================================================
-- 九、数据大屏（P2-1，模块 15）
-- 说明：大屏数据只读聚合现有表，本表仅存大屏布局与指标配置
-- =============================================================

-- ----------------------------
-- 数据大屏配置
-- ----------------------------
DROP TABLE IF EXISTS `t_screen_config`;
CREATE TABLE `t_screen_config` (
  `screen_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `screen_code`      TINYINT      NOT NULL COMMENT '大屏编码：1 经营，2 库存，3 采购，4 分拣绩效，5 配送，6 溯源',
  `screen_name`      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '大屏名称',
  `layout_json`      TEXT              COMMENT '布局与指标配置（JSON）',
  `refresh_interval` INT          DEFAULT NULL COMMENT '刷新间隔（秒）',
  `sort_field`       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '排序维度（分拣绩效等使用）',
  `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，2 停用',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`screen_id`),
  KEY `idx_screen_code` (`screen_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '数据大屏配置';

-- =============================================================
-- 十、财务凭证与外部对接（P2-2，模块 16）
-- 对标蔬东坡 13.6 / 17.1 / 17.4 / 17.5 / 16.5
-- =============================================================

-- ----------------------------
-- 会计凭证（由应收应付 / 收付款 / 费用单生成，借贷必须平衡）
-- ----------------------------
DROP TABLE IF EXISTS `t_finance_voucher`;
CREATE TABLE `t_finance_voucher` (
  `voucher_id`    BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `voucher_no`    VARCHAR(32)    NOT NULL COMMENT '凭证号，PZD + yyyyMMdd + 4 位流水（G-01）',
  `voucher_date`  DATE           NOT NULL COMMENT '凭证日期',
  `voucher_type`  TINYINT        NOT NULL COMMENT '凭证类型：1 收款，2 付款，3 应收，4 应付，5 费用',
  `biz_type`      TINYINT                 DEFAULT NULL COMMENT '关联业务类型',
  `biz_id`        BIGINT                  DEFAULT NULL COMMENT '关联业务单 ID',
  `total_debit`   DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '借方合计（不含税）',
  `total_credit`  DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '贷方合计（不含税）',
  `sync_status`   TINYINT        NOT NULL DEFAULT 1 COMMENT '同步状态：1 未同步，2 同步中，3 同步成功，4 同步失败',
  `external_no`   VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '外部系统单据号（同步成功后回写）',
  `status`        TINYINT        NOT NULL DEFAULT 1 COMMENT '凭证状态：1 已生成，2 已作废',
  `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`  TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是（凭证不允许物理删除）',
  PRIMARY KEY (`voucher_id`),
  UNIQUE KEY `uk_voucher_no` (`voucher_no`),
  KEY `idx_voucher_type` (`voucher_type`),
  KEY `idx_biz` (`biz_type`, `biz_id`),
  KEY `idx_sync_status` (`sync_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会计凭证';

-- ----------------------------
-- 凭证分录
-- ----------------------------
DROP TABLE IF EXISTS `t_voucher_entry`;
CREATE TABLE `t_voucher_entry` (
  `entry_id`       BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `voucher_id`     BIGINT         NOT NULL COMMENT '凭证 ID',
  `subject_code`   VARCHAR(32)    NOT NULL COMMENT '会计科目编码',
  `subject_name`   VARCHAR(64)    NOT NULL DEFAULT '' COMMENT '会计科目名称',
  `direction`      TINYINT        NOT NULL COMMENT '借贷方向：1 借，2 贷',
  `amount`         DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '金额（不含税）',
  `create_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`   TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`entry_id`),
  KEY `idx_voucher_id` (`voucher_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '凭证分录';

-- ----------------------------
-- 外部系统配置（密钥加密存储，禁止明文）
-- ----------------------------
DROP TABLE IF EXISTS `t_external_config`;
CREATE TABLE `t_external_config` (
  `config_id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `system_type`       TINYINT      NOT NULL COMMENT '系统类型：1 金蝶云星空，2 金蝶云星瀚，3 用友T+，4 用友U8，5 溯源平台，6 团餐平台',
  `api_url`           VARCHAR(255) NOT NULL DEFAULT '' COMMENT '接口地址',
  `app_key`           VARCHAR(128) NOT NULL DEFAULT '' COMMENT '应用 Key',
  `app_secret`        VARCHAR(255) NOT NULL DEFAULT '' COMMENT '应用密钥（加密存储，禁止明文 / 提交 Git）',
  `field_mapping_json` TEXT                COMMENT '字段映射配置（JSON）',
  `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，2 停用',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`config_id`),
  KEY `idx_system_type` (`system_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '外部系统配置';

-- ----------------------------
-- 发票（支持按税率拆分开票的整单红冲联动）
-- ----------------------------
DROP TABLE IF EXISTS `t_invoice`;
CREATE TABLE `t_invoice` (
  `invoice_id`         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `invoice_no`         VARCHAR(64)    NOT NULL COMMENT '发票号',
  `order_id`           BIGINT         NOT NULL COMMENT '关联订单 ID',
  `relate_invoice_nos` VARCHAR(512)   NOT NULL DEFAULT '' COMMENT '关联发票号（多张时逗号分隔）',
  `amount`             DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '价税合计',
  `tax_rate`           DECIMAL(18, 4) NOT NULL DEFAULT 0.0000 COMMENT '税率',
  `status`             TINYINT        NOT NULL DEFAULT 2 COMMENT '状态：1 可开票，2 已开票，3 已红冲',
  `create_time`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`       TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`invoice_id`),
  UNIQUE KEY `uk_invoice_no` (`invoice_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '发票';

-- =============================================================
-- 十一、溯源（P2-3，模块 13）
-- 对标蔬东坡 17.5 生产批号模式 / 17.4 检测报告与保质期
-- 说明：生产批号与库存批次解耦（G-03 不启用库存批次），溯源颗粒度按批次（13-01）
-- =============================================================

-- ----------------------------
-- 溯源批次（生产批号模式）
-- ----------------------------
DROP TABLE IF EXISTS `t_trace_batch`;
CREATE TABLE `t_trace_batch` (
  `batch_id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `batch_no`         VARCHAR(32)  NOT NULL COMMENT '批次号，PCB + yyyyMMdd + 4 位流水（G-01）',
  `product_id`       BIGINT       NOT NULL COMMENT '商品 ID',
  `sku_id`           BIGINT                DEFAULT NULL COMMENT '规格 ID',
  `supplier_id`      BIGINT                DEFAULT NULL COMMENT '供应商 ID',
  `manufacturer_id`  BIGINT                DEFAULT NULL COMMENT '厂商 ID',
  `produce_batch_no` VARCHAR(64)  NOT NULL COMMENT '生产批号',
  `origin_place`     VARCHAR(128) NOT NULL DEFAULT '' COMMENT '产地',
  `produce_date`     DATE         NOT NULL COMMENT '生产 / 采收日期',
  `shelf_life_unit`  TINYINT               DEFAULT NULL COMMENT '保质期单位：1 天，2 月',
  `shelf_life_value` INT                   DEFAULT NULL COMMENT '保质期数值',
  `expire_date`      DATE                  DEFAULT NULL COMMENT '到期日期（按月按自然月计算）',
  `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 有效，2 已过期，3 已作废',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`batch_id`),
  UNIQUE KEY `uk_trace_batch_no` (`batch_no`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_supplier_id` (`supplier_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '溯源批次（生产批号）';

-- ----------------------------
-- 检测报告
-- ----------------------------
DROP TABLE IF EXISTS `t_trace_inspect`;
CREATE TABLE `t_trace_inspect` (
  `inspect_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `batch_id`         BIGINT                DEFAULT NULL COMMENT '关联溯源批次 ID',
  `product_id`       BIGINT       NOT NULL COMMENT '商品 ID',
  `report_name`      VARCHAR(128) NOT NULL COMMENT '报告名称',
  `match_mode`       TINYINT      NOT NULL DEFAULT 2 COMMENT '匹配模式：1 绑定采购单，2 绑定生产批号',
  `report_file`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '报告图片（文件服务）',
  `pdf_file`         VARCHAR(255) NOT NULL DEFAULT '' COMMENT '报告 PDF（文件服务）',
  `inspect_date`     DATE                  DEFAULT NULL COMMENT '检测日期',
  `inspect_org`      VARCHAR(128) NOT NULL DEFAULT '' COMMENT '检测机构',
  `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 有效，2 已作废',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`inspect_id`),
  KEY `idx_batch_id` (`batch_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '检测报告';

-- ----------------------------
-- 溯源码（一码一批）
-- ----------------------------
DROP TABLE IF EXISTS `t_trace_code`;
CREATE TABLE `t_trace_code` (
  `code_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `trace_code`       VARCHAR(64)  NOT NULL COMMENT '唯一溯源码',
  `code_type`        TINYINT      NOT NULL DEFAULT 1 COMMENT '类型：1 批次码',
  `product_id`       BIGINT       NOT NULL COMMENT '商品 ID',
  `sku_id`           BIGINT                DEFAULT NULL COMMENT '规格 ID',
  `batch_id`         BIGINT       NOT NULL COMMENT '关联溯源批次 ID',
  `qrcode_url`       VARCHAR(255) NOT NULL DEFAULT '' COMMENT '二维码图片（文件服务）',
  `status`           TINYINT      NOT NULL DEFAULT 2 COMMENT '状态：1 未启用，2 已启用，3 已作废',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`code_id`),
  UNIQUE KEY `uk_trace_code` (`trace_code`),
  KEY `idx_batch_id` (`batch_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '溯源码';

-- =============================================================
-- 十二、外部平台对接（P2-4，模块 17）
-- 对标蔬东坡 17.3 / 17.4 / 16.5 / 17.2
-- 说明：授权配置复用 16 模块的 t_external_config，本模块只需映射与同步日志
-- =============================================================

-- ----------------------------
-- 外部平台映射（商品 / 客户 / 供应商，支持一对多与转换系数）
-- ----------------------------
DROP TABLE IF EXISTS `t_external_mapping`;
CREATE TABLE `t_external_mapping` (
  `mapping_id`    BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
  `system_type`   TINYINT        NOT NULL COMMENT '平台类型：1 金蝶云星空，2 金蝶云星瀚，3 用友T+，4 用友U8，5 溯源平台，6 团餐平台',
  `biz_type`      TINYINT        NOT NULL COMMENT '映射对象：1 商品，2 客户，3 供应商',
  `local_id`      BIGINT         NOT NULL COMMENT '系统内 ID',
  `external_id`   VARCHAR(64)    NOT NULL COMMENT '外部平台 ID',
  `convert_ratio` DECIMAL(18, 4) NOT NULL DEFAULT 1.0000 COMMENT '单位转换系数（未填默认 1，17.3）',
  `status`        TINYINT        NOT NULL DEFAULT 1 COMMENT '状态：1 启用，2 停用',
  `create_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`  TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`mapping_id`),
  KEY `idx_system_biz` (`system_type`, `biz_type`),
  KEY `idx_local_id` (`local_id`),
  KEY `idx_external_id` (`external_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '外部平台映射';

-- ----------------------------
-- 外部平台同步日志（上报 / 拉取，失败可重试）
-- ----------------------------
DROP TABLE IF EXISTS `t_external_sync_log`;
CREATE TABLE `t_external_sync_log` (
  `log_id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `system_type`    TINYINT      NOT NULL COMMENT '平台类型',
  `biz_type`       TINYINT      NOT NULL COMMENT '业务单据类型',
  `biz_id`         BIGINT       NOT NULL COMMENT '业务单据 ID',
  `sync_type`      TINYINT      NOT NULL COMMENT '同步方向：1 上报，2 拉取',
  `sync_status`    TINYINT      NOT NULL DEFAULT 3 COMMENT '同步状态：1 成功，2 失败，3 待同步',
  `fail_reason`    VARCHAR(500) NOT NULL DEFAULT '' COMMENT '失败原因',
  `retry_count`    INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
  `sync_time`      DATETIME              DEFAULT NULL COMMENT '同步时间',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_user_id`   BIGINT      DEFAULT NULL COMMENT '创建人ID',
  `create_user_name` VARCHAR(30) DEFAULT NULL COMMENT '创建人姓名',
  `deleted_flag`   TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`log_id`),
  KEY `idx_system_biz` (`system_type`, `biz_type`, `biz_id`),
  KEY `idx_sync_status` (`sync_status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '外部平台同步日志';

-- =============================================================
-- 编号前缀补充（需同步维护到需求总览 §6）
-- 供应商编码 GYS（已在 04-采购.sql）；询价单 XJD；商品提报单 SPB；
-- 供应商对账单 DZD；商品转换单 ZHD（沿用总览「规格转换单」前缀）
-- =============================================================
