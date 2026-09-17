-- ============================================================
-- XSY-SCM 演示数据（生鲜供应链场景）
-- 说明：使用 INSERT IGNORE + 显式主键，可重复执行；已存在则跳过。
--       重点：t_stock_adjust 造了「待审核」数据，用于体验审核通过/驳回。
-- ============================================================

-- ---------------- 供应商 ----------------
INSERT IGNORE INTO t_supplier
  (supplier_id, supplier_no, supplier_name, contact_name, contact_phone, address, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (2001, 'S2001', '山东绿源果业有限公司', '张伟', '13805310001', '山东省烟台市栖霞市果园路18号', 1, NOW(), NOW(), 1, 'system', 0),
  (2002, 'S2002', '深圳鲜供销农产品有限公司', '陈静', '13923450002', '广东省深圳市龙岗区平湖街道白泥坑', 1, NOW(), NOW(), 1, 'system', 0),
  (2003, 'S2003', '广州江南果品批发市场', '黄强', '13711220003', '广东省广州市荔湾区芳村大道中', 1, NOW(), NOW(), 1, 'system', 0),
  (2004, 'S2004', '海南热带水果合作社', '吴海', '13698990004', '海南省三亚市崖州区水果基地', 2, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 产品 ----------------
INSERT IGNORE INTO t_product
  (product_id, product_no, category_id, product_name, product_type, measure_type, base_unit, spec_flag, purchase_mode, default_supplier_id, default_buyer_id, detail, main_image, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (1001, 'P1001', 1, '红富士苹果', 1, 2, 'kg', 1, 1, 2001, 1, '山东烟台产，甜脆多汁', '', 3, NOW(), NOW(), 1, 'system', 0),
  (1002, 'P1002', 1, '智利车厘子', 1, 2, 'kg', 1, 1, 2002, 1, '进口JJ级，单果约10g', '', 3, NOW(), NOW(), 1, 'system', 0),
  (1003, 'P1003', 1, '泰国金枕榴莲', 1, 2, 'kg', 1, 2, 2003, 1, '整颗进口，A级果', '', 3, NOW(), NOW(), 1, 'system', 0),
  (1004, 'P1004', 1, '沙糖桔', 2, 2, 'kg', 1, 1, 2001, 1, '非标品，按批次称重', '', 3, NOW(), NOW(), 1, 'system', 0),
  (1005, 'P1005', 1, '进口香蕉', 1, 1, '件', 0, 1, 2004, 1, '菲律宾进口，每件约13kg', '', 2, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 产品规格 ----------------
INSERT IGNORE INTO t_product_sku
  (sku_id, product_id, sku_no, spec_name, unit, unit_weight, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (3001, 1001, 'SKU3001', '红富士苹果-80mm一级', 'kg', 1.000, 1, NOW(), NOW(), 1, 'system', 0),
  (3002, 1001, 'SKU3002', '红富士苹果-75mm二级', 'kg', 1.000, 1, NOW(), NOW(), 1, 'system', 0),
  (3003, 1002, 'SKU3003', '智利车厘子-JJ级2斤装', 'kg', 1.000, 1, NOW(), NOW(), 1, 'system', 0),
  (3004, 1003, 'SKU3004', '泰国金枕榴莲-整颗A级', 'kg', 3.000, 1, NOW(), NOW(), 1, 'system', 0),
  (3005, 1004, 'SKU3005', '沙糖桔-散装称重', 'kg', 1.000, 1, NOW(), NOW(), 1, 'system', 0),
  (3006, 1005, 'SKU3006', '进口香蕉-整件13kg', '件', 13.000, 2, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 产品价格 ----------------
INSERT IGNORE INTO t_product_price
  (price_id, product_id, sku_id, price_type, customer_level_id, customer_id, price, effective_time, expire_time, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (4001, 1001, 3001, 1, NULL, NULL, 8.6000, NOW(), '2026-12-31 23:59:59', 1, NOW(), NOW(), 1, 'system', 0),
  (4002, 1001, 3002, 2, 1, NULL, 7.2000, NOW(), '2026-12-31 23:59:59', 1, NOW(), NOW(), 1, 'system', 0),
  (4003, 1002, 3003, 1, NULL, NULL, 68.0000, NOW(), '2026-12-31 23:59:59', 1, NOW(), NOW(), 1, 'system', 0),
  (4004, 1003, 3004, 4, NULL, 6001, 42.5000, NOW(), '2026-12-31 23:59:59', 1, NOW(), NOW(), 1, 'system', 0),
  (4005, 1004, 3005, 3, NULL, NULL, 5.8000, NOW(), '2026-10-31 23:59:59', 2, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 产品供应商 ----------------
INSERT IGNORE INTO t_product_supplier
  (id, product_id, supplier_id, supply_price, default_flag, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (5001, 1001, 2001, 6.2000, 1, NOW(), NOW(), 1, 'system', 0),
  (5002, 1001, 2003, 6.8000, 0, NOW(), NOW(), 1, 'system', 0),
  (5003, 1002, 2002, 55.0000, 1, NOW(), NOW(), 1, 'system', 0),
  (5004, 1003, 2003, 35.0000, 1, NOW(), NOW(), 1, 'system', 0),
  (5005, 1005, 2004, 78.0000, 1, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 客户 ----------------
INSERT IGNORE INTO t_customer
  (customer_id, customer_no, customer_name, customer_type, customer_level_id, parent_customer_id, settle_mode, seller_id, supplier_id, contact_name, contact_phone, address, longitude, latitude, balance, credit_amount, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (6001, 'C6001', '好又多超市(天河店)', 1, 1, 0, 1, 1, NULL, '李娜', '13900010001', '广东省广州市天河区天河路208号', 113.324, 23.135, 12000.00, 50000.00, 2, NOW(), NOW(), 1, 'system', 0),
  (6002, 'C6002', '钱大妈生鲜(越秀店)', 1, 2, 0, 1, 1, NULL, '王强', '13900020002', '广东省广州市越秀区东风中路300号', 113.267, 23.130, -3200.00, 30000.00, 2, NOW(), NOW(), 1, 'system', 0),
  (6003, 'C6003', '个人客户-王先生', 2, NULL, 0, 1, 1, NULL, '王先生', '13900030003', '广东省广州市海珠区新港西路', 113.290, 23.100, 0.00, 0.00, 1, NOW(), NOW(), 1, 'system', 0),
  (6004, 'C6004', '华润万家(华南集团)', 3, 1, 0, 2, 1, NULL, '赵敏', '13900040004', '广东省深圳市福田区深南大道', 114.055, 22.543, 88000.00, 200000.00, 2, NOW(), NOW(), 1, 'system', 0),
  (6005, 'C6005', '暂停合作客户-示例', 1, 3, 0, 1, 1, NULL, '孙涛', '13900050005', '广东省佛山市南海区桂城街道', 113.150, 23.020, 0.00, 0.00, 3, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 客户账期 ----------------
INSERT IGNORE INTO t_customer_period
  (period_id, customer_id, period_type, amount_threshold, period_value, period_unit, settle_day, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (7001, 6001, 2, NULL, 30, 1, NULL, 1, NOW(), NOW(), 1, 'system', 0),
  (7002, 6002, 1, 50000.00, NULL, NULL, 15, 1, NOW(), NOW(), 1, 'system', 0),
  (7003, 6004, 2, NULL, 2, 2, NULL, 1, NOW(), NOW(), 1, 'system', 0),
  (7004, 6005, 2, NULL, 15, 1, NULL, 2, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 客户商品可见 ----------------
INSERT IGNORE INTO t_customer_goods_visible
  (id, customer_id, product_id, visible_type, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (7101, 6001, 1001, 1, NOW(), NOW(), 1, 'system', 0),
  (7102, 6001, 1002, 1, NOW(), NOW(), 1, 'system', 0),
  (7103, 6002, 1003, 2, NOW(), NOW(), 1, 'system', 0),
  (7104, 6004, 1002, 1, NOW(), NOW(), 1, 'system', 0),
  (7105, 6005, 1004, 2, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 客户收款码 ----------------
INSERT IGNORE INTO t_customer_qrcode
  (qrcode_id, seller_id, qrcode_url, scan_count, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (7201, 1, 'https://xsy-scm.example.com/qr/7201', 128, NOW(), NOW(), 1, 'system', 0),
  (7202, 1, 'https://xsy-scm.example.com/qr/7202', 36, NOW(), NOW(), 1, 'system', 0),
  (7203, 2, 'https://xsy-scm.example.com/qr/7203', 0, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 销售订单 ----------------
INSERT IGNORE INTO t_order
  (order_id, order_no, customer_id, settle_customer_id, source, settle_type, total_amount, discount_amount, payable_amount, actual_amount, pay_status, expect_delivery_time, seller_id, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (8001, 'SO20260908001', 6001, 6001, 2, 1, 1860.00, 60.00, 1800.00, 0.00, 1, '2026-09-10 08:00:00', 1, 2, NOW(), NOW(), 1, 'system', 0),
  (8002, 'SO20260908002', 6002, 6002, 1, 3, 3400.00, 0.00, 3400.00, 3400.00, 3, '2026-09-09 08:00:00', 1, 8, NOW(), NOW(), 1, 'system', 0),
  (8003, 'SO20260908003', 6004, 6004, 2, 1, 12750.00, 750.00, 12000.00, 6000.00, 2, '2026-09-12 08:00:00', 1, 4, NOW(), NOW(), 1, 'system', 0),
  (8004, 'SO20260908004', 6003, 6003, 3, 2, 258.00, 0.00, 258.00, 0.00, 1, NULL, 1, 11, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 订单明细 ----------------
INSERT IGNORE INTO t_order_item
  (item_id, order_id, product_id, sku_id, quantity, snapshot_price, price_type, actual_weight, actual_quantity, item_amount, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (8101, 8001, 1001, 3001, 100.000, 8.6000, 1, 100.000, 100.000, 860.00, 1, NOW(), NOW(), 1, 'system', 0),
  (8102, 8001, 1004, 3005, 200.000, 5.0000, 1, 200.000, 200.000, 1000.00, 1, NOW(), NOW(), 1, 'system', 0),
  (8103, 8002, 1002, 3003, 50.000, 68.0000, 1, 50.000, 50.000, 3400.00, 1, NOW(), NOW(), 1, 'system', 0),
  (8104, 8003, 1003, 3004, 300.000, 42.5000, 4, 300.000, 300.000, 12750.00, 1, NOW(), NOW(), 1, 'system', 0),
  (8105, 8004, 1001, 3002, 30.000, 8.6000, 1, 30.000, 30.000, 258.00, 2, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 订单日志 ----------------
INSERT IGNORE INTO t_order_log
  (log_id, order_id, operate_type, before_value, after_value, operate_by, operate_time, create_time, update_time)
VALUES
  (8201, 8001, 1, '', '创建订单 SO20260908001，金额1860.00', 1, NOW(), NOW(), NOW()),
  (8202, 8002, 7, '待配送', '已签收', 1, NOW(), NOW(), NOW()),
  (8203, 8003, 3, '单价45.00', '单价42.50', 1, NOW(), NOW(), NOW()),
  (8204, 8004, 5, '待确认', '已取消', 1, NOW(), NOW(), NOW());

-- ---------------- 退款单 ----------------
INSERT IGNORE INTO t_refund
  (refund_id, refund_no, order_id, item_id, refund_type, refund_amount, refund_reason, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (8301, 'RF20260908001', 8004, 8105, 1, 258.00, '客户取消订单，全额退款', 1, NOW(), NOW(), 1, 'system', 0),
  (8302, 'RF20260908002', 8002, 8103, 2, 680.00, '车厘子到货破损，退货退款', 3, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 采购订单 ----------------
INSERT IGNORE INTO t_purchase_order
  (purchase_id, purchase_no, supplier_id, buyer_id, category_id, total_amount, actual_amount, expect_arrive_time, qrcode_url, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (9001, 'PO20260908001', 2001, 1, 1, 6200.00, 6200.00, '2026-09-09 08:00:00', 'https://xsy-scm.example.com/po/9001', 4, NOW(), NOW(), 1, 'system', 0),
  (9002, 'PO20260908002', 2002, 1, 1, 27500.00, 13750.00, '2026-09-11 08:00:00', 'https://xsy-scm.example.com/po/9002', 3, NOW(), NOW(), 1, 'system', 0),
  (9003, 'PO20260908003', 2003, 1, 1, 10500.00, 0.00, '2026-09-12 08:00:00', 'https://xsy-scm.example.com/po/9003', 1, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 采购明细 ----------------
INSERT IGNORE INTO t_purchase_item
  (item_id, purchase_id, product_id, sku_id, require_quantity, purchase_quantity, received_quantity, unit_price, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (9101, 9001, 1001, 3001, 1000.000, 1000.000, 1000.000, 6.2000, 3, NOW(), NOW(), 1, 'system', 0),
  (9102, 9002, 1002, 3003, 500.000, 500.000, 250.000, 55.0000, 2, NOW(), NOW(), 1, 'system', 0),
  (9103, 9003, 1003, 3004, 300.000, 300.000, 0.000, 35.0000, 1, NOW(), NOW(), 1, 'system', 0),
  (9104, 9003, 1004, 3005, 800.000, 800.000, 0.000, 4.5000, 1, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 采购收货 ----------------
INSERT IGNORE INTO t_receive
  (receive_id, receive_no, purchase_id, item_id, receive_quantity, receive_weight, unit_price, receive_by, receive_time, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (9201, 'RC20260908001', 9001, 9101, 1000.000, 1000.000, 6.2000, 1, '2026-09-08 10:00:00', 2, NOW(), NOW(), 1, 'system', 0),
  (9202, 'RC20260908002', 9002, 9102, 250.000, 250.000, 55.0000, 1, '2026-09-08 14:00:00', 1, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 库存余额 ----------------
INSERT IGNORE INTO t_stock_balance
  (balance_id, product_id, sku_id, warehouse_id, batch_id, quantity, weight, avg_cost, total_cost, warn_min, warn_max, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (13001, 1001, 3001, 1, NULL, 1000.000, 1000.000, 6.2000, 6200.00, 200.000, 5000.000, NOW(), NOW(), 1, 'system', 0),
  (13002, 1001, 3002, 1, NULL, 320.000, 320.000, 5.4000, 1728.00, 100.000, 3000.000, NOW(), NOW(), 1, 'system', 0),
  (13003, 1002, 3003, 1, NULL, 250.000, 250.000, 55.0000, 13750.00, 50.000, 1000.000, NOW(), NOW(), 1, 'system', 0),
  (13004, 1003, 3004, 1, NULL, 0.000, 0.000, 0.0000, 0.00, 20.000, 500.000, NOW(), NOW(), 1, 'system', 0),
  (13005, 1004, 3005, 1, NULL, 480.000, 480.000, 4.5000, 2160.00, 100.000, 2000.000, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 库存流水 ----------------
INSERT IGNORE INTO t_stock_flow
  (flow_id, flow_no, product_id, sku_id, warehouse_id, batch_id, flow_type, biz_type, biz_id, direction, quantity, weight, unit_price, amount, before_quantity, after_quantity, before_avg_cost, after_avg_cost, operate_by, operate_time, create_time, update_time)
VALUES
  (14001, 'FL20260908001', 1001, 3001, 1, NULL, 1, 1, 9001, 1, 1000.000, 1000.000, 6.2000, 6200.00, 0.000, 1000.000, 0.0000, 6.2000, 1, NOW(), NOW(), NOW()),
  (14002, 'FL20260908002', 1002, 3003, 1, NULL, 1, 1, 9002, 1, 250.000, 250.000, 55.0000, 13750.00, 0.000, 250.000, 0.0000, 55.0000, 1, NOW(), NOW(), NOW()),
  (14003, 'FL20260908003', 1001, 3001, 1, NULL, 2, 2, 8001, 2, 100.000, 100.000, 8.6000, 860.00, 1000.000, 900.000, 6.2000, 6.2000, 1, NOW(), NOW(), NOW()),
  (14004, 'FL20260908004', 1004, 3005, 1, NULL, 4, 5, 10001, 2, 20.000, 20.000, 4.5000, 90.00, 500.000, 480.000, 4.5000, 4.5000, 1, NOW(), NOW(), NOW()),
  (14005, 'FL20260908005', 1001, 3001, 1, NULL, 5, 5, 10002, 1, 10.000, 10.000, 6.2000, 62.00, 900.000, 910.000, 6.2000, 6.2000, 1, NOW(), NOW(), NOW());

-- ---------------- 库存调整单（报损报溢）----------------
-- 状态：1 待审核 / 2 已完成 / 3 已驳回
INSERT IGNORE INTO t_stock_adjust
  (adjust_id, adjust_no, adjust_type, product_id, sku_id, warehouse_id, quantity, weight, reason, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (10001, 'AD20260908001', 1, 1004, 3005, 1, 20.000, 20.000, '仓库抽检发现沙糖桔腐烂，报损20kg', 1, NOW(), NOW(), 1, 'system', 0),
  (10002, 'AD20260908002', 2, 1001, 3001, 1, 10.000, 10.000, '盘点盘盈红富士苹果10kg', 1, NOW(), NOW(), 1, 'system', 0),
  (10003, 'AD20260908003', 1, 1002, 3003, 1, 5.000, 5.000, '车厘子在途损耗报损5kg', 2, NOW(), NOW(), 1, 'system', 0),
  (10004, 'AD20260908004', 4, 1005, 3006, 1, 2.000, 26.000, '香蕉整件拆零规格转换', 3, NOW(), NOW(), 1, 'system', 0),
  (10005, 'AD20260908005', 3, 1003, 3004, 1, 3.000, 9.000, '盘点差异调整榴莲3kg', 1, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 库存盘点单 ----------------
-- 状态：1 待盘点 / 2 盘点中 / 3 已完成 / 4 已取消
INSERT IGNORE INTO t_stock_check
  (check_id, check_no, warehouse_id, check_type, status, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (11001, 'CK20260908001', 1, 1, 1, NOW(), NOW(), 1, 'system', 0),
  (11002, 'CK20260908002', 1, 2, 2, NOW(), NOW(), 1, 'system', 0),
  (11003, 'CK20260908003', 1, 3, 3, NOW(), NOW(), 1, 'system', 0);

-- ---------------- 盘点明细 ----------------
INSERT IGNORE INTO t_stock_check_item
  (item_id, check_id, product_id, sku_id, book_quantity, book_weight, actual_quantity, actual_weight, diff_quantity, create_time, update_time, create_user_id, create_user_name, deleted_flag)
VALUES
  (12001, 11001, 1001, 3001, 900.000, 900.000, 895.000, 895.000, -5.000, NOW(), NOW(), 1, 'system', 0),
  (12002, 11001, 1002, 3003, 250.000, 250.000, 250.000, 250.000, 0.000, NOW(), NOW(), 1, 'system', 0),
  (12003, 11001, 1004, 3005, 480.000, 480.000, 470.000, 470.000, -10.000, NOW(), NOW(), 1, 'system', 0),
  (12004, 11002, 1003, 3004, 50.000, 150.000, NULL, NULL, NULL, NOW(), NOW(), 1, 'system', 0);
