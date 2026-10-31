-- 为 SCM 业务表补齐表与字段注释。
-- 生成口径：2026-11-01 的 xsy_v2 schema 快照；只补当前没有注释的对象，已有注释保持不变。

-- ---------------------------------------------------------------------------
-- 1. 表注释
-- ---------------------------------------------------------------------------
COMMENT ON TABLE xsy_v2.xsy_business_category IS '业务分类：按适用范围维护树形分类，供客户与供应商业务归类。';
COMMENT ON TABLE xsy_v2.xsy_customer_group IS '客户分组：客户分组维度，用于分组价与运营筛选。';
COMMENT ON TABLE xsy_v2.xsy_customer_group_price IS '客户分组价：按客户分组与商品规格维护的销售价，含有效期。';
COMMENT ON TABLE xsy_v2.xsy_customer_promotion_code IS '客户推广码：为单个客户生成的可撤销推广码。';
COMMENT ON TABLE xsy_v2.xsy_customer_promotion_scan IS '客户推广扫码记录：记录推广码被扫描的时间与来源终端。';
COMMENT ON TABLE xsy_v2.xsy_customer_sku_alias IS '客户商品别名：客户侧的商品规格叫法，用于导入与检索。';
COMMENT ON TABLE xsy_v2.xsy_customer_statement IS '客户对账单：按期间冻结客户应收、收款、核销与余额。';
COMMENT ON TABLE xsy_v2.xsy_customer_statement_item IS '客户对账单明细：逐笔列示期间的应收、收款、核销与退款事实。';
COMMENT ON TABLE xsy_v2.xsy_customer_statement_source IS '客户对账单来源：记录本次对账纳入的财务事实，避免重复统计。';
COMMENT ON TABLE xsy_v2.xsy_delivery_gps_event IS '配送 GPS 轨迹事件：车辆上报的定位点，仅作历史轨迹留存。';
COMMENT ON TABLE xsy_v2.xsy_delivery_plan_proposal IS '配送排线建议：保存排线算法的输入、结果与应用/废弃状态。';
COMMENT ON TABLE xsy_v2.xsy_finance_purchase_cost_entry IS '采购成本入账：采购成本调整产生的应付冲减事实。';
COMMENT ON TABLE xsy_v2.xsy_inventory_outbound IS '库存出库单：新建即待出库，确认后扣减库存并写流水。';
COMMENT ON TABLE xsy_v2.xsy_inventory_outbound_item IS '库存出库单明细：出库商品规格、数量与记账单位快照。';
COMMENT ON TABLE xsy_v2.xsy_inventory_reservation IS '库存预留：销售订单锁定的可用量，发货或释放时解除。';
COMMENT ON TABLE xsy_v2.xsy_inventory_warning_state IS '库存预警状态：按仓库与商品规格记录当前预警状态，用于去重通知。';
COMMENT ON TABLE xsy_v2.xsy_mall_district_warehouse IS '商城行政区配送仓：行政区与配送仓的优先级映射。';
COMMENT ON TABLE xsy_v2.xsy_mall_home_draft IS '商城首页草稿：单行表，保存当前待发布的首页装修内容。';
COMMENT ON TABLE xsy_v2.xsy_mall_home_pointer IS '商城首页指针：单行表，指向当前已发布的首页版本。';
COMMENT ON TABLE xsy_v2.xsy_mall_home_popup IS '商城首页弹窗：单行配置，保存弹窗内容与展示规则。';
COMMENT ON TABLE xsy_v2.xsy_mall_home_release IS '商城首页发布版本：每次发布冻结的不可变装修内容。';
COMMENT ON TABLE xsy_v2.xsy_mall_page IS '商城单页：公告、条款、关于我们与售后规则的草稿及发布内容。';
COMMENT ON TABLE xsy_v2.xsy_mall_store IS '商城店铺：店铺名称与对外链接配置。';
COMMENT ON TABLE xsy_v2.xsy_notification_event IS '站内通知事件：记录已发送的通知幂等键与接收人。';
COMMENT ON TABLE xsy_v2.xsy_order_bundle_component IS '订单套餐明细：套餐订单拆解出的组件商品与库存分摊快照。';
COMMENT ON TABLE xsy_v2.xsy_order_discount IS '订单优惠：订单级优惠活动、券与分摊金额快照。';
COMMENT ON TABLE xsy_v2.xsy_order_promotion_gift IS '订单促销赠品：活动赠送的商品规格与数量快照。';
COMMENT ON TABLE xsy_v2.xsy_order_received_quantity_correction IS '订单实收数量更正：追加记录出库后客户实收量的更正审计。';
COMMENT ON TABLE xsy_v2.xsy_order_return_receipt IS '退货收货单：退货实物到仓后的验收记录。';
COMMENT ON TABLE xsy_v2.xsy_order_return_receipt_item IS '退货收货单明细：逐行记录退货验收数量、单位与处置方式。';
COMMENT ON TABLE xsy_v2.xsy_order_tag IS '订单标签：订单标记的标签目录。';
COMMENT ON TABLE xsy_v2.xsy_order_tag_link IS '订单标签关联：订单与标签的多对多关联。';
COMMENT ON TABLE xsy_v2.xsy_pricing_time_price IS '时价：按商品规格维护的一段时间内有效的销售价。';
COMMENT ON TABLE xsy_v2.xsy_print_customer_template IS '客户打印模板绑定：指定客户与单据类型使用的模板。';
COMMENT ON TABLE xsy_v2.xsy_print_record IS '打印记录：冻结每次打印使用的模板与业务数据快照。';
COMMENT ON TABLE xsy_v2.xsy_print_template IS '打印模板：按单据类型维护的字段白名单模板。';
COMMENT ON TABLE xsy_v2.xsy_product_bundle_component IS '商品套餐组件：套餐商品包含的组件商品与换算关系。';
COMMENT ON TABLE xsy_v2.xsy_product_sku_stock_conversion IS '商品规格库存换算：销售单位与库存单位之间的换算关系。';
COMMENT ON TABLE xsy_v2.xsy_promotion_activity IS '促销活动：活动规则、有效期与启停状态。';
COMMENT ON TABLE xsy_v2.xsy_promotion_coupon IS '优惠券模板：券面额、使用门槛、有效期与发放限制。';
COMMENT ON TABLE xsy_v2.xsy_promotion_coupon_instance IS '优惠券实例：发放给客户的单张券及其预占、使用、释放状态。';
COMMENT ON TABLE xsy_v2.xsy_purchase_cost_adjustment IS '采购成本调整：收货后对采购行成本价的调整事实与明细。';
COMMENT ON TABLE xsy_v2.xsy_purchase_demand_calculation_batch IS '采购需求计算批次：冻结一次净需求计算的输入、结果与统计。';
COMMENT ON TABLE xsy_v2.xsy_purchase_demand_calculation_batch_item IS '采购需求计算批次明细：逐行冻结需求来源、计算量与生成结果。';
COMMENT ON TABLE xsy_v2.xsy_purchase_inquiry IS '采购询价单：按批次、仓库与商品发起的询价及受理状态。';
COMMENT ON TABLE xsy_v2.xsy_purchase_inquiry_quote IS '采购询价报价：供应商逐次提交的报价与有效期。';
COMMENT ON TABLE xsy_v2.xsy_purchase_share_feedback IS '采购分享回填：供应商通过分享链接回填的单价、实收量与备注。';
COMMENT ON TABLE xsy_v2.xsy_purchase_share_token IS '采购分享令牌：按采购单生成、可撤销且有过期时间的供应商回填令牌。';
COMMENT ON TABLE xsy_v2.xsy_sorting_scale_event IS '分拣秤事件：电子秤上报的原始读数、稳定标记与受理结果。';
COMMENT ON TABLE xsy_v2.xsy_supplier_statement IS '供应商对账单：按期间冻结供应商应付、付款、核销与余额。';
COMMENT ON TABLE xsy_v2.xsy_supplier_statement_item IS '供应商对账单明细：逐笔列示期间的应付、付款与核销事实。';
COMMENT ON TABLE xsy_v2.xsy_supplier_statement_source IS '供应商对账单来源：记录本次对账纳入的财务事实，避免重复统计。';
COMMENT ON TABLE xsy_v2.xsy_user_filter_preset IS '用户筛选预设：员工保存的列表筛选条件。';

-- ---------------------------------------------------------------------------
-- 2. 字段注释
-- ---------------------------------------------------------------------------

-- xsy_business_category
COMMENT ON COLUMN xsy_v2.xsy_business_category.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_business_category.scope IS '适用范围（CUSTOMER 客户 / SUPPLIER 供应商）';
COMMENT ON COLUMN xsy_v2.xsy_business_category.category_code IS '分类编码';
COMMENT ON COLUMN xsy_v2.xsy_business_category.name IS '分类名称';
COMMENT ON COLUMN xsy_v2.xsy_business_category.parent_id IS '上级分类 ID';
COMMENT ON COLUMN xsy_v2.xsy_business_category.level IS '分类层级（从 1 开始）';
COMMENT ON COLUMN xsy_v2.xsy_business_category.status IS '状态：ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN xsy_v2.xsy_business_category.sort_order IS '排序号';
COMMENT ON COLUMN xsy_v2.xsy_business_category.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_business_category.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_business_category.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_business_category.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_business_category.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_business_category.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_customer
COMMENT ON COLUMN xsy_v2.xsy_customer.settlement_customer_id IS '结算客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer.cod_allowed IS '是否允许货到付款';
COMMENT ON COLUMN xsy_v2.xsy_customer.mall_delivery_warehouse_id IS '商城默认配送仓 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer.business_category_id IS '业务分类 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer.customer_group_id IS '客户分组 ID';

-- xsy_customer_agreement_price
COMMENT ON COLUMN xsy_v2.xsy_customer_agreement_price.formula_type IS '计价公式类型';
COMMENT ON COLUMN xsy_v2.xsy_customer_agreement_price.formula_parameter IS '计价公式参数';

-- xsy_customer_balance_account
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_account.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_account.settlement_customer_id IS '结算客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_account.settlement_customer_name_snapshot IS '结算客户名称快照';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_account.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_account.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_account.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_account.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_account.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_account.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_customer_balance_movement
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.movement_no IS '余额流水号';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.account_id IS '账户 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.type IS '余额变动类型';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.direction IS '方向';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.amount IS '余额变动金额';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.source_id IS '来源 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.reason IS '原因';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.occurred_at IS '业务发生时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_movement.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_customer_balance_recharge
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.recharge_no IS '充值单号';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.settlement_customer_name_snapshot IS '结算客户名称快照';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.customer_name_snapshot IS '客户名称快照';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.amount IS '充值金额';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_customer_balance_recharge.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_customer_group
COMMENT ON COLUMN xsy_v2.xsy_customer_group.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_group.group_code IS '客户分组编码';
COMMENT ON COLUMN xsy_v2.xsy_customer_group.group_name IS '客户分组名称';
COMMENT ON COLUMN xsy_v2.xsy_customer_group.status IS '状态：ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN xsy_v2.xsy_customer_group.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_customer_group.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_customer_group.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_group.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_group.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_customer_group.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_customer_group_price
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.group_id IS '客户分组 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.spu_id IS '商品 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.sale_unit IS '销售单位';
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.unit_price IS '单价';
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.effective_from IS '生效时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.effective_to IS '失效时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_customer_group_price.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_customer_promotion_code
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_code.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_code.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_code.code IS '推广码';
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_code.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_code.revoked_at IS '撤销时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_code.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_code.updated_at IS '更新时间';

-- xsy_customer_promotion_scan
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_scan.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_scan.code_id IS '推广码 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_scan.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_scan.seller_id IS '业务员（员工 ID）';
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_scan.scanned_at IS '扫码时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_scan.client_ip IS '客户端 IP';
COMMENT ON COLUMN xsy_v2.xsy_customer_promotion_scan.user_agent IS '客户端 User-Agent';

-- xsy_customer_sku_alias
COMMENT ON COLUMN xsy_v2.xsy_customer_sku_alias.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_sku_alias.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_sku_alias.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_sku_alias.alias IS '客户侧商品规格别名';
COMMENT ON COLUMN xsy_v2.xsy_customer_sku_alias.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_customer_sku_alias.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_customer_sku_alias.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_sku_alias.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_sku_alias.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_customer_sku_alias.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_customer_statement
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.settlement_customer_id IS '结算客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.settlement_customer_name IS '结算客户名称';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.start_date IS '对账期间开始日期';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.end_date IS '对账期间结束日期';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.partial_scope IS '部分对账范围说明';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.generated_by_employee_id IS '生成人（员工 ID）';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.generated_at IS '生成时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.opening_receivable IS '期初应收金额';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.receivable_increase IS '本期应收增加金额';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.receivable_red IS '本期应收红字金额';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.write_off_net IS '本期核销净额';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.closing_receivable IS '期末应收金额';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.opening_unallocated IS '期初未核销收款金额';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.receipt_net IS '本期收款净额';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.closing_unallocated IS '期末未核销收款金额';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement.refund_net IS '本期退款净额';

-- xsy_customer_statement_item
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.statement_id IS '对账单 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.line_no IS '行号';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.fact_type IS '事实类型';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.fact_id IS '事实 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.event_at IS '业务发生时间';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.document_no IS '单据编号';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.related_no IS '关联单据编号';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.customer_name IS '客户名称';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.receivable_delta IS '应收变动额';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.receipt_delta IS '收款变动额';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.write_off_delta IS '核销变动额';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.refund_delta IS '退款变动额';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_item.receivable_balance IS '应收余额';

-- xsy_customer_statement_source
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_source.statement_id IS '对账单 ID';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_source.fact_type IS '事实类型';
COMMENT ON COLUMN xsy_v2.xsy_customer_statement_source.fact_id IS '事实 ID';

-- xsy_delivery_gps_event
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.event_key IS '事件幂等键';
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.route_id IS '配送线路 ID';
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.driver_id IS '司机 ID';
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.device_code IS '设备编码';
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.captured_at IS '采集时间';
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.received_at IS '接收时间';
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.longitude IS '经度';
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.latitude IS '纬度';
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.geom_crs IS '坐标参考系';
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.accuracy_meters IS '定位精度（米）';
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.speed_kph IS '速度（公里/小时）';
COMMENT ON COLUMN xsy_v2.xsy_delivery_gps_event.reported_by IS '上报设备或司机标识';

-- xsy_delivery_plan_proposal
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.route_id IS '配送线路 ID';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.status IS '建议状态：PROPOSED 待选择 / APPLIED 已应用 / DISCARDED 已废弃';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.provider_code IS '排线算法提供方编码';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.provider_version IS '排线算法版本';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.estimated_flag IS '是否为估算结果';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.rule_code IS '排线规则编码';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.stop_count IS '站点数量';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.total_distance IS '总距离（米）';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.input_snapshot IS '排线输入快照（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.result_snapshot IS '排线结果快照（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.applied_at IS '应用时间';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.applied_by IS '应用人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.discarded_at IS '废弃时间';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.discarded_by IS '废弃人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_delivery_plan_proposal.version IS '乐观锁版本号';

-- xsy_finance_customer_expense
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense.expense_no IS '费用单号';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense.customer_name_snapshot IS '客户名称快照';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense.expense_month IS '费用所属月份';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense.tax_amount IS '税费金额';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense.service_amount IS '服务费金额';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense.freight_amount IS '运费金额';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense.commission_amount IS '佣金金额';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense.reason IS '原因';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_finance_customer_expense_void
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense_void.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense_void.expense_id IS '客户月费用 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense_void.reason IS '原因';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense_void.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_finance_customer_expense_void.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_finance_payable
COMMENT ON COLUMN xsy_v2.xsy_finance_payable.due_date IS '应付到期日';
COMMENT ON COLUMN xsy_v2.xsy_finance_payable.payment_rule_snapshot IS '付款规则快照';

-- xsy_finance_posting
COMMENT ON COLUMN xsy_v2.xsy_finance_posting.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting.posting_no IS '过账批次号';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting.customer_name_snapshot IS '客户名称快照';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting.settlement_customer_id IS '结算客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting.settlement_customer_name_snapshot IS '结算客户名称快照';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_finance_posting_void
COMMENT ON COLUMN xsy_v2.xsy_finance_posting_void.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting_void.posting_id IS '财务过账批次 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting_void.voided_at IS '作废时间';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting_void.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting_void.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting_void.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting_void.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting_void.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_finance_posting_void.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_finance_purchase_cost_entry
COMMENT ON COLUMN xsy_v2.xsy_finance_purchase_cost_entry.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_purchase_cost_entry.adjustment_id IS '采购成本调整 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_purchase_cost_entry.receipt_item_id IS '收货单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_purchase_cost_entry.original_payable_id IS '原应付记录 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_purchase_cost_entry.amount IS '成本调整金额';
COMMENT ON COLUMN xsy_v2.xsy_finance_purchase_cost_entry.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_finance_purchase_cost_entry.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_finance_purchase_cost_entry.account_item_id IS '账户明细 ID';

-- xsy_finance_receipt
COMMENT ON COLUMN xsy_v2.xsy_finance_receipt.settlement_customer_id IS '结算客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_receipt.settlement_customer_name_snapshot IS '结算客户名称快照';

-- xsy_finance_receivable
COMMENT ON COLUMN xsy_v2.xsy_finance_receivable.settlement_customer_id IS '结算客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_receivable.settlement_customer_name_snapshot IS '结算客户名称快照';
COMMENT ON COLUMN xsy_v2.xsy_finance_receivable.due_date IS '应收到期日';
COMMENT ON COLUMN xsy_v2.xsy_finance_receivable.credit_rule_snapshot IS '账期规则快照';

-- xsy_finance_receivable_item
COMMENT ON COLUMN xsy_v2.xsy_finance_receivable_item.discount_amount IS '折扣金额';

-- xsy_finance_waiver
COMMENT ON COLUMN xsy_v2.xsy_finance_waiver.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_waiver.waiver_no IS '减免单号';
COMMENT ON COLUMN xsy_v2.xsy_finance_waiver.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_waiver.settlement_customer_id IS '结算客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_finance_waiver.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_finance_waiver.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_finance_waiver.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_finance_waiver.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_finance_waiver.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_finance_waiver.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_inventory_balance
COMMENT ON COLUMN xsy_v2.xsy_inventory_balance.reserved_quantity IS '预留数量';

-- xsy_inventory_lot_balance
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_balance.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_balance.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_balance.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_balance.lot_id IS '批次 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_balance.unit IS '记账单位';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_balance.quantity IS '批次结存数量';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_balance.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_balance.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_balance.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_balance.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_balance.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_inventory_lot_movement
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.lot_id IS '批次 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.movement_type IS '批次流水类型';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.source_document_type IS '来源单据类型';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.source_document_id IS '来源单据 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.source_document_item_id IS '来源单据明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.unit_snapshot IS '单位快照';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.before_quantity IS '变动前批次数量';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.after_quantity IS '变动后批次数量';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.occurred_at IS '业务发生时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.operator IS '操作人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_lot_movement.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_inventory_outbound
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound.outbound_no IS '出库单号';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound.confirmed_at IS '确认出库时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound.operator IS '操作人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_inventory_outbound_item
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.outbound_id IS '出库单 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.quantity IS '出库数量';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.unit_snapshot IS '单位快照';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.updated_by IS '更新人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.bundle_component_id IS '套餐组件 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_outbound_item.bundle_sale_quantity IS '套餐组件销售数量';

-- xsy_inventory_reservation
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.source_document_type IS '来源单据类型';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.source_document_id IS '来源单据 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.source_document_item_id IS '来源单据明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.quantity IS '数量';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.unit_snapshot IS '单位快照';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.status IS '预留状态：ACTIVE 生效 / RELEASED 已释放 / CONSUMED 已消耗';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.occurred_at IS '业务发生时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.operator IS '操作人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_inventory_reservation.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_inventory_warning_state
COMMENT ON COLUMN xsy_v2.xsy_inventory_warning_state.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_warning_state.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_inventory_warning_state.status IS '预警状态：NORMAL 正常 / LOW 低于下限 / HIGH 高于上限';
COMMENT ON COLUMN xsy_v2.xsy_inventory_warning_state.epoch IS '预警轮次（状态变化时递增，用于去重通知）';
COMMENT ON COLUMN xsy_v2.xsy_inventory_warning_state.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_inventory_warning_state.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_mall_cart_item
COMMENT ON COLUMN xsy_v2.xsy_mall_cart_item.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_cart_item.account_id IS '账户 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_cart_item.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_cart_item.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_cart_item.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_mall_cart_item.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_mall_cart_item.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_cart_item.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_cart_item.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_mall_cart_item.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_mall_customer_account
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.password_hash IS '登录密码哈希';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.status IS '账号状态：ENABLED 启用 / DISABLED 停用';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.login_failed_count IS '连续登录失败次数';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.locked_until IS '账号锁定截止时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.last_login_at IS '最后登录时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.last_login_ip IS '最后登录 IP';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_account.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_mall_customer_address
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.receiver_name IS '收货人姓名';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.receiver_phone IS '收货人电话';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.province_code IS '省编码';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.province_name IS '省名称';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.city_code IS '市编码';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.city_name IS '市名称';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.district_code IS '区县编码';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.district_name IS '区县名称';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.detail_address IS '详细地址';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.longitude IS '经度';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.latitude IS '纬度';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.is_default IS '是否默认';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_mall_customer_address.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_mall_district_warehouse
COMMENT ON COLUMN xsy_v2.xsy_mall_district_warehouse.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_district_warehouse.district_code IS '行政区编码';
COMMENT ON COLUMN xsy_v2.xsy_mall_district_warehouse.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_district_warehouse.priority IS '优先级（数值越小越优先）';
COMMENT ON COLUMN xsy_v2.xsy_mall_district_warehouse.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_mall_district_warehouse.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_district_warehouse.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_district_warehouse.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_mall_district_warehouse.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_mall_home_draft
COMMENT ON COLUMN xsy_v2.xsy_mall_home_draft.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_draft.version IS '草稿内容版本号（每次保存递增）';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_draft.schema_version IS '数据结构版本号';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_draft.content IS '首页装修内容（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_draft.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_draft.updated_by IS '更新人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_draft.theme_code IS '主题编码';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_draft.theme_color IS '主题主色';

-- xsy_mall_home_pointer
COMMENT ON COLUMN xsy_v2.xsy_mall_home_pointer.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_pointer.current_release_id IS '当前发布的首页版本 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_pointer.version IS '指针版本号';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_pointer.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_pointer.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_mall_home_popup
COMMENT ON COLUMN xsy_v2.xsy_mall_home_popup.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_popup.enabled IS '是否启用';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_popup.display_rule IS '弹窗展示规则（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_popup.start_at IS '生效开始时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_popup.end_at IS '生效结束时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_popup.content IS '弹窗内容（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_popup.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_popup.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_popup.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_mall_home_release
COMMENT ON COLUMN xsy_v2.xsy_mall_home_release.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_release.schema_version IS '数据结构版本号';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_release.content IS '首页发布内容（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_release.publish_kind IS '发布类型：PUBLISH 发布 / ROLLBACK 回滚';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_release.source_release_id IS '回滚来源发布版本 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_release.reason IS '发布或回滚原因';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_release.published_at IS '发布时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_release.published_by IS '发布人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_release.theme_code IS '主题编码';
COMMENT ON COLUMN xsy_v2.xsy_mall_home_release.theme_color IS '主题主色';

-- xsy_mall_page
COMMENT ON COLUMN xsy_v2.xsy_mall_page.page_code IS '页面编码（ANNOUNCEMENT / TERMS / ABOUT / AFTER_SALE）';
COMMENT ON COLUMN xsy_v2.xsy_mall_page.title IS '标题';
COMMENT ON COLUMN xsy_v2.xsy_mall_page.draft_content IS '草稿正文';
COMMENT ON COLUMN xsy_v2.xsy_mall_page.version IS '草稿内容版本号';
COMMENT ON COLUMN xsy_v2.xsy_mall_page.published_content IS '已发布正文';
COMMENT ON COLUMN xsy_v2.xsy_mall_page.published_version IS '已发布内容版本号';
COMMENT ON COLUMN xsy_v2.xsy_mall_page.published_at IS '发布时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_page.published_by IS '发布人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_mall_page.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_page.updated_by IS '更新人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_mall_page.published_title IS '已发布标题';

-- xsy_mall_store
COMMENT ON COLUMN xsy_v2.xsy_mall_store.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_store.store_name IS '店铺名称';
COMMENT ON COLUMN xsy_v2.xsy_mall_store.store_link IS '店铺对外链接';
COMMENT ON COLUMN xsy_v2.xsy_mall_store.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_mall_store.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_store.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_mall_wechat_binding
COMMENT ON COLUMN xsy_v2.xsy_mall_wechat_binding.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_wechat_binding.account_id IS '账户 ID';
COMMENT ON COLUMN xsy_v2.xsy_mall_wechat_binding.app_id IS '微信小程序 AppID';
COMMENT ON COLUMN xsy_v2.xsy_mall_wechat_binding.open_id IS '微信 OpenID';
COMMENT ON COLUMN xsy_v2.xsy_mall_wechat_binding.union_id IS '微信 UnionID';
COMMENT ON COLUMN xsy_v2.xsy_mall_wechat_binding.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_mall_wechat_binding.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_mall_wechat_binding.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_wechat_binding.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_mall_wechat_binding.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_mall_wechat_binding.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_notification_event
COMMENT ON COLUMN xsy_v2.xsy_notification_event.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_notification_event.event_key IS '事件幂等键';
COMMENT ON COLUMN xsy_v2.xsy_notification_event.event_type IS '事件类型';
COMMENT ON COLUMN xsy_v2.xsy_notification_event.receiver_user_type IS '接收人用户类型';
COMMENT ON COLUMN xsy_v2.xsy_notification_event.receiver_user_id IS '接收人用户 ID';
COMMENT ON COLUMN xsy_v2.xsy_notification_event.data_id IS '关联业务数据 ID';
COMMENT ON COLUMN xsy_v2.xsy_notification_event.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_notification_event.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_order_bundle_component
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.order_id IS '订单 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.order_item_id IS '订单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.bundle_sku_id IS '套餐商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.component_sku_id IS '组件商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.component_sku_code_snapshot IS '组件商品规格编码快照';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.component_product_name_snapshot IS '组件商品名称快照';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.component_sale_unit_snapshot IS '组件销售单位快照';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.component_stock_unit_snapshot IS '组件库存单位快照';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.quantity_per_bundle_sale_unit IS '每份套餐包含的组件销售数量';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.stock_units_per_component_sale_unit IS '组件每个销售单位对应的库存单位数';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.component_sale_quantity_snapshot IS '组件销售数量快照';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.market_unit_price_snapshot IS '组件市场单价快照';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.market_reference_amount_snapshot IS '组件市场参考金额快照';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.allocated_sale_amount_snapshot IS '组件分摊销售金额快照';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.product_bundle_component_id IS '商品套餐组件 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.product_bundle_component_version IS '商品套餐组件版本号';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_order_bundle_component.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_order_discount
COMMENT ON COLUMN xsy_v2.xsy_order_discount.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.sales_order_id IS '销售订单 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.activity_id IS '促销活动 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.activity_version IS '促销活动版本号';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.activity_snapshot IS '促销活动快照';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.coupon_instance_id IS '优惠券实例 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.coupon_snapshot IS '优惠券快照';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.base_amount IS '优惠计算基数';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.discount_amount IS '优惠金额';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.allocations IS '优惠分摊明细（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.rounding_target_item_id IS '尾差归集订单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_order_discount.special_discount_amount IS '特殊折扣金额';

-- xsy_order_operation_log
COMMENT ON COLUMN xsy_v2.xsy_order_operation_log.customer_id IS '客户 ID';

-- xsy_order_promotion_gift
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.sales_order_id IS '销售订单 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.activity_id IS '促销活动 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.activity_version IS '促销活动版本号';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.sku_code_snapshot IS '商品规格编码快照';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.product_name_snapshot IS '商品名称快照';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.spec_name_snapshot IS '规格名称快照';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.sale_unit_snapshot IS '销售单位快照';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.quantity IS '数量';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.rule_snapshot IS '赠品规则快照';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_order_promotion_gift.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_order_received_quantity_correction
COMMENT ON COLUMN xsy_v2.xsy_order_received_quantity_correction.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_received_quantity_correction.order_id IS '订单 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_received_quantity_correction.order_item_id IS '订单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_received_quantity_correction.sale_unit_snapshot IS '销售单位快照';
COMMENT ON COLUMN xsy_v2.xsy_order_received_quantity_correction.shipped_quantity IS '已出库数量';
COMMENT ON COLUMN xsy_v2.xsy_order_received_quantity_correction.previous_received_quantity IS '更正前实收数量';
COMMENT ON COLUMN xsy_v2.xsy_order_received_quantity_correction.received_quantity IS '更正后实收数量';
COMMENT ON COLUMN xsy_v2.xsy_order_received_quantity_correction.reason IS '原因';
COMMENT ON COLUMN xsy_v2.xsy_order_received_quantity_correction.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_order_received_quantity_correction.created_at IS '创建时间';

-- xsy_order_return
COMMENT ON COLUMN xsy_v2.xsy_order_return.source_type IS '退货来源类型：ORDER 关联订单 / INDEPENDENT 独立退货';
COMMENT ON COLUMN xsy_v2.xsy_order_return.customer_name_snapshot IS '客户名称快照';

-- xsy_order_return_item
COMMENT ON COLUMN xsy_v2.xsy_order_return_item.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_return_item.sku_name_snapshot IS '商品规格名称快照';
COMMENT ON COLUMN xsy_v2.xsy_order_return_item.spec_name_snapshot IS '规格名称快照';
COMMENT ON COLUMN xsy_v2.xsy_order_return_item.sale_unit_snapshot IS '销售单位快照';
COMMENT ON COLUMN xsy_v2.xsy_order_return_item.price_source IS '退货价格来源';

-- xsy_order_return_receipt
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt.return_id IS '退货单 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt.idempotency_key IS '幂等键';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt.received_at IS '接收时间';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt.operator IS '操作人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_order_return_receipt_item
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt_item.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt_item.receipt_id IS '收货单 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt_item.return_item_id IS '退货单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt_item.source_sales_out_movement_id IS '来源销售出库流水 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt_item.source_outbound_item_id IS '来源出库单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt_item.disposition IS '退货处置方式';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt_item.unit_snapshot IS '单位快照';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt_item.unit_cost IS '单位成本';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt_item.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt_item.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt_item.bundle_component_id IS '套餐组件 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_return_receipt_item.bundle_receipt_counted IS '套餐组件是否已计入验收数量';

-- xsy_order_tag
COMMENT ON COLUMN xsy_v2.xsy_order_tag.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_tag.name IS '标签名称';
COMMENT ON COLUMN xsy_v2.xsy_order_tag.color IS '标签颜色';
COMMENT ON COLUMN xsy_v2.xsy_order_tag.enabled IS '是否启用';
COMMENT ON COLUMN xsy_v2.xsy_order_tag.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_order_tag.created_at IS '创建时间';

-- xsy_order_tag_link
COMMENT ON COLUMN xsy_v2.xsy_order_tag_link.order_id IS '订单 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_tag_link.tag_id IS '标签 ID';
COMMENT ON COLUMN xsy_v2.xsy_order_tag_link.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_order_tag_link.created_at IS '创建时间';

-- xsy_payment_callback_event
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.provider IS '支付渠道';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.provider_event_id IS '渠道回调事件 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.event_type IS '事件类型';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.signature_verified IS '渠道签名是否校验通过';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.payload_hash IS '回调报文摘要';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.payload IS '回调原始报文';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.transaction_id IS '支付交易 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.process_status IS '处理状态：RECEIVED 已接收 / APPLIED 已应用 / DUPLICATE 重复 / REJECTED 已拒绝';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.reject_reason IS '拒绝原因';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.received_at IS '接收时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.processed_at IS '处理时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_callback_event.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_payment_intent
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.intent_no IS '支付意图号';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.customer_name_snapshot IS '客户名称快照';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.source_type IS '来源类型';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.source_id IS '来源 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.source_no_snapshot IS '来源单号快照';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.amount IS '支付金额';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.method IS '支付方式';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.provider IS '支付渠道';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.status IS '支付意图状态：CREATED / PENDING / SUCCEEDED / FAILED / EXPIRED / CLOSED';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.external_intent_id IS '渠道支付意图 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.mock_scenario IS '模拟支付场景';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.expire_at IS '支付意图过期时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.succeeded_at IS '支付成功时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.closed_at IS '支付意图关闭时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_payment_intent.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_payment_mock_ledger
COMMENT ON COLUMN xsy_v2.xsy_payment_mock_ledger.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_mock_ledger.provider IS '支付渠道';
COMMENT ON COLUMN xsy_v2.xsy_payment_mock_ledger.provider_transaction_no IS '渠道交易号';
COMMENT ON COLUMN xsy_v2.xsy_payment_mock_ledger.provider_refund_no IS '渠道退款号';
COMMENT ON COLUMN xsy_v2.xsy_payment_mock_ledger.direction IS '资金方向';
COMMENT ON COLUMN xsy_v2.xsy_payment_mock_ledger.amount IS '模拟流水金额';
COMMENT ON COLUMN xsy_v2.xsy_payment_mock_ledger.biz_date IS '业务日期';
COMMENT ON COLUMN xsy_v2.xsy_payment_mock_ledger.occurred_at IS '业务发生时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_mock_ledger.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_mock_ledger.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_payment_reconciliation
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.reconciliation_no IS '对账单号';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.provider IS '支付渠道';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.biz_date IS '对账业务日期';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.status IS '对账状态：PENDING 待对账 / MATCHED 已匹配 / MISMATCHED 有差异';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.provider_total IS '渠道汇总金额';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.local_total IS '本地汇总金额';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.difference IS '渠道与本地差异金额';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.provider_count IS '渠道交易笔数';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.local_count IS '本地交易笔数';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.total_count IS '对账总笔数';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.matched_count IS '匹配笔数';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.difference_count IS '差异笔数';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.detail IS '差异明细（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.reconciled_at IS '对账完成时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_payment_reconciliation_item
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation_item.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation_item.reconciliation_id IS '对账单 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation_item.category IS '差异分类';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation_item.provider_transaction_no IS '渠道交易号';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation_item.transaction_id IS '支付交易 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation_item.local_amount IS '本地金额';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation_item.provider_amount IS '渠道金额';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation_item.local_status IS '本地交易状态';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation_item.provider_status IS '渠道交易状态';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation_item.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_reconciliation_item.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_payment_refund
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.refund_no IS '退款单号';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.intent_id IS '支付意图 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.transaction_id IS '支付交易 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.provider IS '支付渠道';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.amount IS '退款金额';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.source_id IS '来源 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.status IS '退款状态：CREATED / PROCESSING / SUCCEEDED / FAILED / CLOSED';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.provider_refund_no IS '渠道退款号';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.mock_scenario IS '模拟退款场景';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.reason IS '原因';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.refunded_at IS '退款成功时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.failure_code IS '退款失败码';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.failure_message IS '退款失败信息';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.updated_by IS '更新人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.recovery_attempts IS '退款恢复尝试次数';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.next_recovery_at IS '下次退款恢复时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.recovery_status IS '退款恢复状态';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund.recovery_message IS '退款恢复说明';

-- xsy_payment_refund_recovery_log
COMMENT ON COLUMN xsy_v2.xsy_payment_refund_recovery_log.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund_recovery_log.refund_id IS '退款单 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund_recovery_log.operator IS '操作人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund_recovery_log.reason IS '原因';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund_recovery_log.result IS '恢复处理结果';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund_recovery_log.provider_amount IS '渠道退款金额';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund_recovery_log.detail IS '恢复处理详情';
COMMENT ON COLUMN xsy_v2.xsy_payment_refund_recovery_log.created_at IS '创建时间';

-- xsy_payment_transaction
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.transaction_no IS '支付交易号';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.intent_id IS '支付意图 ID';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.provider IS '支付渠道';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.provider_transaction_no IS '渠道交易号';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.amount IS '支付金额';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.provider_amount IS '渠道支付金额';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.status IS '支付交易状态：PENDING / SUCCEEDED / FAILED / CLOSED';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.paid_at IS '支付成功时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.failure_code IS '支付失败码';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.failure_message IS '支付失败信息';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_payment_transaction.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_price_operation_log
COMMENT ON COLUMN xsy_v2.xsy_price_operation_log.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_price_operation_log.price_type IS '价格类型（协议价 / 客户类型价 / 客户分组价 / 时价）';
COMMENT ON COLUMN xsy_v2.xsy_price_operation_log.price_id IS '价格记录 ID';
COMMENT ON COLUMN xsy_v2.xsy_price_operation_log.operation_type IS '价格操作类型';
COMMENT ON COLUMN xsy_v2.xsy_price_operation_log.operator IS '操作人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_price_operation_log.before_data IS '变更前价格数据（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_price_operation_log.after_data IS '变更后价格数据（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_price_operation_log.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_price_operation_log.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_pricing_time_price
COMMENT ON COLUMN xsy_v2.xsy_pricing_time_price.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_pricing_time_price.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_pricing_time_price.unit_price IS '单价';
COMMENT ON COLUMN xsy_v2.xsy_pricing_time_price.start_at IS '生效开始时间';
COMMENT ON COLUMN xsy_v2.xsy_pricing_time_price.end_at IS '生效结束时间';
COMMENT ON COLUMN xsy_v2.xsy_pricing_time_price.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_pricing_time_price.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_pricing_time_price.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_pricing_time_price.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_pricing_time_price.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_pricing_time_price.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_pricing_time_price.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_print_customer_template
COMMENT ON COLUMN xsy_v2.xsy_print_customer_template.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_print_customer_template.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_print_customer_template.document_type IS '单据类型';
COMMENT ON COLUMN xsy_v2.xsy_print_customer_template.template_id IS '模板 ID';
COMMENT ON COLUMN xsy_v2.xsy_print_customer_template.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_print_customer_template.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_print_customer_template.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_print_customer_template.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_print_customer_template.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_print_customer_template.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_print_record
COMMENT ON COLUMN xsy_v2.xsy_print_record.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_print_record.document_type IS '单据类型';
COMMENT ON COLUMN xsy_v2.xsy_print_record.business_id IS '业务单据 ID';
COMMENT ON COLUMN xsy_v2.xsy_print_record.business_no IS '业务单据编号';
COMMENT ON COLUMN xsy_v2.xsy_print_record.template_id IS '模板 ID';
COMMENT ON COLUMN xsy_v2.xsy_print_record.template_code IS '模板编码';
COMMENT ON COLUMN xsy_v2.xsy_print_record.template_name IS '模板名称';
COMMENT ON COLUMN xsy_v2.xsy_print_record.template_version IS '模板版本号';
COMMENT ON COLUMN xsy_v2.xsy_print_record.model_snapshot IS '模板模型快照（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_print_record.data_snapshot IS '打印数据快照（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_print_record.printed_at IS '打印时间';
COMMENT ON COLUMN xsy_v2.xsy_print_record.printed_by IS '打印人（用户类型:用户 ID）';

-- xsy_print_template
COMMENT ON COLUMN xsy_v2.xsy_print_template.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_print_template.document_type IS '单据类型';
COMMENT ON COLUMN xsy_v2.xsy_print_template.template_code IS '模板编码';
COMMENT ON COLUMN xsy_v2.xsy_print_template.template_name IS '模板名称';
COMMENT ON COLUMN xsy_v2.xsy_print_template.default_flag IS '是否默认模板';
COMMENT ON COLUMN xsy_v2.xsy_print_template.enabled_flag IS '是否启用';
COMMENT ON COLUMN xsy_v2.xsy_print_template.model IS '模板模型（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_print_template.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_print_template.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_print_template.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_print_template.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_print_template.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_print_template.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_print_template.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_product_bundle_component
COMMENT ON COLUMN xsy_v2.xsy_product_bundle_component.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_product_bundle_component.bundle_sku_id IS '套餐商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_product_bundle_component.component_sku_id IS '组件商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_product_bundle_component.quantity_per_bundle_sale_unit IS '每份套餐包含的组件销售数量';
COMMENT ON COLUMN xsy_v2.xsy_product_bundle_component.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_product_bundle_component.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_product_bundle_component.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_product_bundle_component.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_product_bundle_component.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_product_bundle_component.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_product_sku_stock_conversion
COMMENT ON COLUMN xsy_v2.xsy_product_sku_stock_conversion.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_product_sku_stock_conversion.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_product_sku_stock_conversion.sale_unit IS '销售单位';
COMMENT ON COLUMN xsy_v2.xsy_product_sku_stock_conversion.stock_unit IS '库存单位';
COMMENT ON COLUMN xsy_v2.xsy_product_sku_stock_conversion.stock_units_per_sale_unit IS '每个销售单位对应的库存单位数';
COMMENT ON COLUMN xsy_v2.xsy_product_sku_stock_conversion.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_product_sku_stock_conversion.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_product_sku_stock_conversion.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_product_sku_stock_conversion.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_product_sku_stock_conversion.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_product_sku_stock_conversion.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_product_tag
COMMENT ON COLUMN xsy_v2.xsy_product_tag.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_product_tag.tag_code IS '标签编码';
COMMENT ON COLUMN xsy_v2.xsy_product_tag.name IS '名称';
COMMENT ON COLUMN xsy_v2.xsy_product_tag.sort_order IS '排序号';
COMMENT ON COLUMN xsy_v2.xsy_product_tag.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_product_tag.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_product_tag.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_product_tag.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_product_tag.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_product_tag.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_product_tag_relation
COMMENT ON COLUMN xsy_v2.xsy_product_tag_relation.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_product_tag_relation.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_product_tag_relation.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_product_tag_relation.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_product_tag_relation.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_product_tag_relation.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_product_tag_relation.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_promotion_activity
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.activity_code IS '活动编码';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.activity_name IS '活动名称';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.activity_type IS '活动类型';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.exclusive_group IS '互斥组';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.priority IS '优先级（数值越小越优先）';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.valid_from IS '有效期开始';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.valid_to IS '有效期结束';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.status IS '活动状态：DRAFT 草稿 / ACTIVE 生效 / STOPPED 停止';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.rule IS '活动规则（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_promotion_activity.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_promotion_coupon
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.coupon_code IS '优惠券编码';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.coupon_name IS '优惠券名称';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.discount_type IS '优惠类型';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.discount_value IS '优惠值';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.min_order_amount IS '最低订单金额';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.valid_from IS '有效期开始';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.valid_to IS '有效期结束';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.status IS '券状态：DRAFT 草稿 / ACTIVE 生效 / STOPPED 停止';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.updated_by IS '更新人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.claimable IS '是否允许客户领取';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.per_customer_limit IS '每客户领取上限';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon.total_limit IS '总发放上限';

-- xsy_promotion_coupon_instance
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.coupon_id IS '优惠券模板 ID';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.instance_no IS '优惠券实例号';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.status IS '券实例状态：AVAILABLE 可用 / RESERVED 已预占 / USED 已使用 / RELEASED 已释放';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.reserved_order_id IS '预占订单 ID';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.reserved_at IS '预占时间';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.used_order_id IS '使用订单 ID';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.used_at IS '使用时间';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.released_at IS '释放时间';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.release_reason IS '释放原因';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_promotion_coupon_instance.issue_source IS '发放来源';

-- xsy_purchase_account
COMMENT ON COLUMN xsy_v2.xsy_purchase_account.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account.account_no IS '采购账户号';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account.purchase_order_id IS '采购单 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account.supplier_id IS '供应商 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account.supplier_name_snapshot IS '供应商名称快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account.confirmed_at IS '采购确认时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account.confirmed_by IS '采购确认人（用户类型:用户 ID）';

-- xsy_purchase_account_item
COMMENT ON COLUMN xsy_v2.xsy_purchase_account_item.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account_item.account_id IS '账户 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account_item.purchase_order_item_id IS '采购单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account_item.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account_item.product_name_snapshot IS '商品名称快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account_item.sku_name_snapshot IS '商品规格名称快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account_item.unit_snapshot IS '单位快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account_item.quantity IS '数量';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account_item.unit_price IS '单价';
COMMENT ON COLUMN xsy_v2.xsy_purchase_account_item.amount IS '金额';

-- xsy_purchase_cost_adjustment
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.adjustment_no IS '成本调整单号';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.receipt_item_id IS '收货单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.receipt_id IS '收货单 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.purchase_order_id IS '采购单 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.purchase_order_item_id IS '采购单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.supplier_id IS '供应商 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.supplier_name_snapshot IS '供应商名称快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.product_name_snapshot IS '商品名称快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.unit_snapshot IS '单位快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.quantity IS '数量';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.previous_adjustment_id IS '上一笔成本调整 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.previous_price IS '调整前单价';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.new_price IS '调整后单价';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.direction IS '方向';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.amount IS '成本调整金额';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.reason IS '原因';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.occurred_at IS '业务发生时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.account_item_id IS '账户明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_cost_adjustment.account_id IS '账户 ID';

-- xsy_purchase_demand
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand.calculation_batch_id IS '需求计算批次 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand.calculation_batch_item_id IS '需求计算批次明细 ID';

-- xsy_purchase_demand_calculation_batch
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.start_at IS '生效开始时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.end_at IS '生效结束时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.supplier_id IS '供应商 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.purchaser_id IS '采购员（员工 ID）';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.category_id IS '商品分类 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.keyword IS '商品关键词';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.status IS '批次状态：READY 待生成 / GENERATED 已生成';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.source_line_count IS '来源订单行数';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.candidate_line_count IS '候选需求行数';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.generated_count IS '生成需求数';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.skipped_count IS '跳过行数';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.summary_snapshot IS '计算汇总快照（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.result_snapshot IS '计算结果快照（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.generated_at IS '生成时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_purchase_demand_calculation_batch_item
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.batch_id IS '需求计算批次 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.line_no IS '行号';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.sales_order_id IS '销售订单 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.sales_order_item_id IS '销售订单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.sales_order_no_snapshot IS '销售订单号快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.source_confirmed_at IS '来源销售订单确认时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.spu_id IS '商品 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.spu_code_snapshot IS '商品编码快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.product_name_snapshot IS '商品名称快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.sku_code_snapshot IS '商品规格编码快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.sku_name_snapshot IS '商品规格名称快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.spec_values_snapshot IS '规格值快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.demand_unit_snapshot IS '需求单位快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.product_type_snapshot IS '商品类型快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.source_quantity IS '来源订单数量';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.required_quantity IS '计算后的净需求量';
COMMENT ON COLUMN xsy_v2.xsy_purchase_demand_calculation_batch_item.existing_demand_id IS '已存在的采购需求 ID';

-- xsy_purchase_inquiry
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.batch_id IS '需求计算批次 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.purchaser_id IS '采购员（员工 ID）';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.sku_code_snapshot IS '商品规格编码快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.product_name_snapshot IS '商品名称快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.demand_unit IS '需求单位';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.required_quantity IS '需求数量';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.deadline_at IS '报价截止时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.status IS '询价状态';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.accepted_quote_id IS '已采纳的报价 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.accepted_at IS '受理时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.accepted_by IS '受理人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_purchase_inquiry_quote
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry_quote.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry_quote.inquiry_id IS '询价单 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry_quote.supplier_id IS '供应商 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry_quote.supplier_name_snapshot IS '供应商名称快照';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry_quote.revision IS '报价轮次';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry_quote.purchase_unit IS '采购单位';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry_quote.unit_price IS '单价';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry_quote.valid_until IS '报价有效期至';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry_quote.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry_quote.recorded_at IS '报价记录时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_inquiry_quote.recorded_by IS '报价记录人（用户类型:用户 ID）';

-- xsy_purchase_order
COMMENT ON COLUMN xsy_v2.xsy_purchase_order.locked_at IS '锁单时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_order.locked_by IS '锁单人（用户类型:用户 ID）';

-- xsy_purchase_price_adjust
COMMENT ON COLUMN xsy_v2.xsy_purchase_price_adjust.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_price_adjust.purchase_order_id IS '采购单 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_price_adjust.purchase_order_item_id IS '采购单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_price_adjust.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_price_adjust.old_price IS '调整前单价';
COMMENT ON COLUMN xsy_v2.xsy_purchase_price_adjust.new_price IS '调整后单价';
COMMENT ON COLUMN xsy_v2.xsy_purchase_price_adjust.reason IS '原因';
COMMENT ON COLUMN xsy_v2.xsy_purchase_price_adjust.operator IS '操作人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_purchase_price_adjust.created_at IS '创建时间';

-- xsy_purchase_receipt_item_lot
COMMENT ON COLUMN xsy_v2.xsy_purchase_receipt_item_lot.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_receipt_item_lot.purchase_receipt_item_id IS '采购收货单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_receipt_item_lot.lot_id IS '批次 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_receipt_item_lot.quantity IS '批次收货数量';
COMMENT ON COLUMN xsy_v2.xsy_purchase_receipt_item_lot.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_receipt_item_lot.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_purchase_share_feedback
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_feedback.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_feedback.share_token_id IS '采购分享令牌 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_feedback.purchase_order_id IS '采购单 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_feedback.purchase_order_item_id IS '采购单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_feedback.unit_price IS '单价';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_feedback.received_quantity IS '供应商回填的实收数量';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_feedback.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_feedback.submitted_at IS '供应商提交时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_feedback.operator_ip IS '提交终端 IP';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_feedback.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_feedback.created_at IS '创建时间';

-- xsy_purchase_share_token
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_token.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_token.token_hash IS '分享令牌哈希';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_token.token_prefix IS '分享令牌前缀（用于展示定位）';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_token.purchase_order_id IS '采购单 ID';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_token.expires_at IS '过期时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_token.revoked_at IS '撤销时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_token.last_used_at IS '最近使用时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_token.use_count IS '使用次数';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_token.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_token.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_purchase_share_token.updated_at IS '更新时间';

-- xsy_refund_funding_allocation
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.order_refund_id IS '订单退款 ID';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.order_id IS '订单 ID';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.settlement_customer_id IS '结算客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.source_method IS '退款资金来源方式';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.status IS '资金来源分摊状态';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.failure_code IS '资金来源失败码';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.failure_message IS '资金来源失败信息';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.source_succeeded_at IS '资金来源成功时间';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.sort_order IS '资金来源处理顺序';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_refund_funding_allocation.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_region
COMMENT ON COLUMN xsy_v2.xsy_region.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_region.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_region.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_region.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_region.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_region.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_region.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_report_negative_stock_snapshot
COMMENT ON COLUMN xsy_v2.xsy_report_negative_stock_snapshot.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_report_negative_stock_snapshot.snapshot_no IS '快照编号';
COMMENT ON COLUMN xsy_v2.xsy_report_negative_stock_snapshot.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_report_negative_stock_snapshot.warehouse_name IS '仓库名称';
COMMENT ON COLUMN xsy_v2.xsy_report_negative_stock_snapshot.snapshot_month IS '快照所属月份';
COMMENT ON COLUMN xsy_v2.xsy_report_negative_stock_snapshot.captured_at IS '采集时间';
COMMENT ON COLUMN xsy_v2.xsy_report_negative_stock_snapshot.row_count IS '数据行数';
COMMENT ON COLUMN xsy_v2.xsy_report_negative_stock_snapshot.zero_cost_row_count IS '零成本数据行数';
COMMENT ON COLUMN xsy_v2.xsy_report_negative_stock_snapshot.estimated_amount IS '估算金额';
COMMENT ON COLUMN xsy_v2.xsy_report_negative_stock_snapshot.row_data IS '行数据快照（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_report_negative_stock_snapshot.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_report_negative_stock_snapshot.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_report_purchase_daily
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily.generated_at IS '生成时间';

-- xsy_report_purchase_daily_item
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.report_date IS '报表日期';
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.purchaser_id IS '采购员（员工 ID）';
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.spu_code IS '商品编码';
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.product_name IS '商品名称';
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.sku_code IS '商品规格编码';
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.sku_name IS '商品规格名称';
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.purchase_unit IS '采购单位';
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.order_count IS '采购单数';
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.planned_quantity IS '计划采购数量';
COMMENT ON COLUMN xsy_v2.xsy_report_purchase_daily_item.order_amount IS '采购金额';

-- xsy_sales_order
COMMENT ON COLUMN xsy_v2.xsy_sales_order.settlement_customer_id IS '结算客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_sales_order.settlement_customer_name_snapshot IS '结算客户名称快照';
COMMENT ON COLUMN xsy_v2.xsy_sales_order.credit_override_reason IS '账期覆盖原因';
COMMENT ON COLUMN xsy_v2.xsy_sales_order.mall_coupon_instance_id IS '商城优惠券实例 ID';
COMMENT ON COLUMN xsy_v2.xsy_sales_order.settlement_method IS '结算方式';
COMMENT ON COLUMN xsy_v2.xsy_sales_order.locked_at IS '锁单时间';
COMMENT ON COLUMN xsy_v2.xsy_sales_order.locked_by IS '锁单人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_sales_order.auto_lock_at IS '自动锁单时间';

-- xsy_sales_order_item
COMMENT ON COLUMN xsy_v2.xsy_sales_order_item.mall_stock_unit_snapshot IS '商城库存单位快照';

-- xsy_scale_device
COMMENT ON COLUMN xsy_v2.xsy_scale_device.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.device_name IS '设备名称';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.device_model IS '设备型号';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.station_code IS '工位编码';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.protocol_type IS '通信协议类型';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.agent_version IS '设备代理版本';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.pairing_expires_at IS '配对码过期时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.credential_expires_at IS '设备凭证过期时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.last_seen_at IS '最后在线时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.last_reading_at IS '最后读数时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.scale_connected IS '秤是否连接';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.printer_connected IS '打印机是否连接';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.revoked_at IS '撤销时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.revoked_by IS '撤销人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_scale_device.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_scale_device_session
COMMENT ON COLUMN xsy_v2.xsy_scale_device_session.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_scale_device_session.issued_at IS '会话签发时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_device_session.expires_at IS '过期时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_device_session.end_reason IS '会话结束原因';
COMMENT ON COLUMN xsy_v2.xsy_scale_device_session.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_device_session.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_scale_item_completion
COMMENT ON COLUMN xsy_v2.xsy_scale_item_completion.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_scale_item_completion.task_id IS '任务 ID';
COMMENT ON COLUMN xsy_v2.xsy_scale_item_completion.task_item_id IS '任务明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_scale_item_completion.device_id IS '设备 ID';
COMMENT ON COLUMN xsy_v2.xsy_scale_item_completion.device_session_id IS '设备会话 ID';
COMMENT ON COLUMN xsy_v2.xsy_scale_item_completion.employee_id IS '员工 ID';
COMMENT ON COLUMN xsy_v2.xsy_scale_item_completion.business_unit IS '业务单位';
COMMENT ON COLUMN xsy_v2.xsy_scale_item_completion.reason IS '原因';
COMMENT ON COLUMN xsy_v2.xsy_scale_item_completion.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_item_completion.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_scale_job_lease
COMMENT ON COLUMN xsy_v2.xsy_scale_job_lease.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_scale_job_lease.device_id IS '设备 ID';
COMMENT ON COLUMN xsy_v2.xsy_scale_job_lease.employee_id IS '员工 ID';
COMMENT ON COLUMN xsy_v2.xsy_scale_job_lease.task_id IS '任务 ID';
COMMENT ON COLUMN xsy_v2.xsy_scale_job_lease.leased_at IS '租约开始时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_job_lease.expires_at IS '过期时间';
COMMENT ON COLUMN xsy_v2.xsy_scale_job_lease.release_reason IS '租约释放原因';
COMMENT ON COLUMN xsy_v2.xsy_scale_job_lease.created_at IS '创建时间';

-- xsy_sorting_part_record
COMMENT ON COLUMN xsy_v2.xsy_sorting_part_record.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_sorting_part_record.task_id IS '任务 ID';
COMMENT ON COLUMN xsy_v2.xsy_sorting_part_record.task_item_id IS '任务明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_sorting_part_record.event_type IS '事件类型';
COMMENT ON COLUMN xsy_v2.xsy_sorting_part_record.note IS '分次录入说明';
COMMENT ON COLUMN xsy_v2.xsy_sorting_part_record.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_sorting_part_record.created_at IS '创建时间';

-- xsy_sorting_scale_event
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.event_key IS '事件幂等键';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.task_id IS '任务 ID';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.task_item_id IS '任务明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.device_code IS '设备编码';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.raw_reading IS '电子秤原始读数';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.unit IS '读数单位';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.stable_flag IS '读数是否稳定';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.captured_at IS '采集时间';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.received_at IS '接收时间';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.status IS '事件状态';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.accepted_quantity IS '受理数量';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.accepted_at IS '受理时间';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.accepted_by IS '受理人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.rejected_at IS '拒绝时间';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.rejected_by IS '拒绝人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.reject_reason IS '拒绝原因';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.protocol_type IS '通信协议类型';
COMMENT ON COLUMN xsy_v2.xsy_sorting_scale_event.agent_version IS '设备代理版本';

-- xsy_supplier
COMMENT ON COLUMN xsy_v2.xsy_supplier.payment_period_days IS '账期天数';
COMMENT ON COLUMN xsy_v2.xsy_supplier.business_category_id IS '业务分类 ID';

-- xsy_supplier_sku
COMMENT ON COLUMN xsy_v2.xsy_supplier_sku.supplier_item_code IS '供应商商品编码';

-- xsy_supplier_statement
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.supplier_id IS '供应商 ID';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.supplier_name IS '供应商名称';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.warehouse_id IS '仓库 ID';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.start_date IS '对账期间开始日期';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.end_date IS '对账期间结束日期';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.partial_scope IS '部分对账范围说明';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.generated_by_employee_id IS '生成人（员工 ID）';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.generated_at IS '生成时间';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.opening_payable IS '期初应付金额';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.payable_increase IS '本期应付增加金额';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.payable_red IS '本期应付红字金额';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.write_off_net IS '本期核销净额';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.closing_payable IS '期末应付金额';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.opening_unallocated IS '期初未核销付款金额';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.payment_net IS '本期付款净额';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement.closing_unallocated IS '期末未核销付款金额';

-- xsy_supplier_statement_item
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_item.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_item.statement_id IS '对账单 ID';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_item.line_no IS '行号';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_item.fact_type IS '事实类型';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_item.fact_id IS '事实 ID';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_item.event_at IS '业务发生时间';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_item.document_no IS '单据编号';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_item.related_no IS '关联单据编号';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_item.payable_delta IS '应付变动额';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_item.payment_delta IS '付款变动额';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_item.write_off_delta IS '核销变动额';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_item.payable_balance IS '应付余额';

-- xsy_supplier_statement_source
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_source.statement_id IS '对账单 ID';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_source.fact_type IS '事实类型';
COMMENT ON COLUMN xsy_v2.xsy_supplier_statement_source.fact_id IS '事实 ID';

-- xsy_trace_event
COMMENT ON COLUMN xsy_v2.xsy_trace_event.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_event.unit_id IS '追溯单元 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_event.lot_id IS '批次 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_event.event_type IS '事件类型';
COMMENT ON COLUMN xsy_v2.xsy_trace_event.source_document_type IS '来源单据类型';
COMMENT ON COLUMN xsy_v2.xsy_trace_event.source_document_id IS '来源单据 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_event.source_document_item_id IS '来源单据明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_event.occurred_at IS '业务发生时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_event.operator IS '操作人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_trace_event.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_trace_event.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_event.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_trace_inspection_report
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.report_no IS '检测报告编号';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.report_name IS '检测报告名称';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.lot_id IS '批次 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.inspection_date IS '检测日期';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.inspection_org IS '检测机构';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.conclusion IS '检测结论：QUALIFIED 合格 / UNQUALIFIED 不合格 / UNDETERMINED 未判定';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.expires_at IS '过期时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.status IS '报告状态：DRAFT 草稿 / PUBLISHED 已发布 / WITHDRAWN 已撤回';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.published_by IS '发布人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.published_at IS '发布时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.withdrawn_by IS '撤回人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.withdrawn_at IS '撤回时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.updated_by IS '更新人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_inspection_report.version IS '乐观锁版本号';

-- xsy_trace_lot
COMMENT ON COLUMN xsy_v2.xsy_trace_lot.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_lot.lot_no IS '批次号';
COMMENT ON COLUMN xsy_v2.xsy_trace_lot.supplier_id IS '供应商 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_lot.sku_id IS '商品规格 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_lot.origin_type IS '批次来源类型';
COMMENT ON COLUMN xsy_v2.xsy_trace_lot.first_receipt_item_id IS '首次收货明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_lot.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_lot.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_trace_public_query
COMMENT ON COLUMN xsy_v2.xsy_trace_public_query.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_public_query.unit_id IS '追溯单元 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_public_query.public_token IS '公开查询令牌';
COMMENT ON COLUMN xsy_v2.xsy_trace_public_query.queried_at IS '查询时间';

-- xsy_trace_unit
COMMENT ON COLUMN xsy_v2.xsy_trace_unit.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit.lot_id IS '批次 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit.package_type IS '包装类型';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit.quantity IS '数量';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit.unit IS '计量单位';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit.status IS '追溯单元状态';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit.remark IS '备注';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit.weight_unit IS '重量单位';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit.weight_source IS '重量来源';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit.measured_at IS '称重时间';

-- xsy_trace_unit_binding
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.unit_id IS '追溯单元 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.lot_id IS '批次 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.sorting_task_id IS '分拣任务 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.sorting_task_item_id IS '分拣任务明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.sales_order_id IS '销售订单 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.sales_order_item_id IS '销售订单明细 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.customer_id IS '客户 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.order_no_snapshot IS '订单号快照';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.bound_at IS '绑定时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.bound_by IS '绑定人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_binding.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_trace_unit_correction
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.unit_id IS '追溯单元 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.binding_id IS '绑定记录 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.reason IS '原因';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.status IS '更正状态';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.requested_by IS '申请人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.requested_at IS '申请时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.decided_by IS '审批人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.decided_at IS '审批时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.decision_reason IS '审批原因';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_correction.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_trace_unit_unbind
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_unbind.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_unbind.unit_id IS '追溯单元 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_unbind.binding_id IS '绑定记录 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_unbind.correction_id IS '追溯更正单 ID';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_unbind.reason IS '原因';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_unbind.unbound_at IS '解绑时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_unbind.unbound_by IS '解绑人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_unbind.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_trace_unit_unbind.created_by IS '创建人（用户类型:用户 ID）';

-- xsy_uom
COMMENT ON COLUMN xsy_v2.xsy_uom.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_uom.sort_order IS '排序号';
COMMENT ON COLUMN xsy_v2.xsy_uom.version IS '乐观锁版本号';
COMMENT ON COLUMN xsy_v2.xsy_uom.deleted IS '是否删除（软删标记）';
COMMENT ON COLUMN xsy_v2.xsy_uom.created_at IS '创建时间';
COMMENT ON COLUMN xsy_v2.xsy_uom.updated_at IS '更新时间';
COMMENT ON COLUMN xsy_v2.xsy_uom.created_by IS '创建人（用户类型:用户 ID）';
COMMENT ON COLUMN xsy_v2.xsy_uom.updated_by IS '更新人（用户类型:用户 ID）';

-- xsy_user_filter_preset
COMMENT ON COLUMN xsy_v2.xsy_user_filter_preset.id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.xsy_user_filter_preset.owner_key IS '所属用户键';
COMMENT ON COLUMN xsy_v2.xsy_user_filter_preset.view_key IS '列表视图键';
COMMENT ON COLUMN xsy_v2.xsy_user_filter_preset.name IS '名称';
COMMENT ON COLUMN xsy_v2.xsy_user_filter_preset.conditions IS '筛选条件（JSON）';
COMMENT ON COLUMN xsy_v2.xsy_user_filter_preset.created_at IS '创建时间';

-- SmartAdmin 底座表：补齐上一轮扫描遗漏的三列
COMMENT ON COLUMN xsy_v2.t_file_relation.relation_id IS '主键 ID';
COMMENT ON COLUMN xsy_v2.t_file_relation.create_time IS '创建时间';
COMMENT ON COLUMN xsy_v2.t_file_relation.update_time IS '更新时间';

-- ---------------------------------------------------------------------------
-- 3. 门禁：迁移结束后 xsy_v2 下不允许存在无注释的表或字段
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    missing TEXT;
BEGIN
    SELECT string_agg(c.relname || CASE WHEN a.attname IS NULL THEN '' ELSE '.' || a.attname END, ', ')
    INTO missing
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    LEFT JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
    WHERE n.nspname = 'xsy_v2' AND c.relkind = 'r'
      AND (obj_description(c.oid, 'pg_class') IS NULL
           OR (a.attname IS NOT NULL AND col_description(c.oid, a.attnum) IS NULL));
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION '仍有未注释的表或字段: %', missing;
    END IF;
END $$;
