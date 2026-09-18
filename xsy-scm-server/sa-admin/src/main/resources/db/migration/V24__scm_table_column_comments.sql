-- V24: 补齐表与字段注释（去品牌收尾）。
--
-- 背景：W1-W3 的 product_/customer_/supplier_ 系列表在 V6-V12 建表时未写 COMMENT，
-- 另有部分 t_* 表与字段缺注释。本迁移只做 COMMENT ON，不改任何结构或数据。
--
-- 同时修正 V5 遗留的一条上游品牌注释（t_notice.document_number）。
-- 因 V5 属冻结迁移不可改，只能在此覆盖。

SET search_path TO xsy_v2;

-- ----------------------------
-- 表注释
-- ----------------------------
COMMENT ON TABLE customer IS '客户主档';
COMMENT ON TABLE customer_agreement_price IS '客户协议价（按客户+SKU 定价）';
COMMENT ON TABLE customer_agreement_price_operation_log IS '客户协议价操作日志';
COMMENT ON TABLE customer_price_batch_audit IS '客户价格批量导入审计';
COMMENT ON TABLE customer_sku_visibility IS '客户可见 SKU 范围';
COMMENT ON TABLE customer_type IS '客户类型';
COMMENT ON TABLE customer_type_price IS '客户类型价（按客户类型+SKU 定价）';
COMMENT ON TABLE customer_type_price_operation_log IS '客户类型价操作日志';
COMMENT ON TABLE product_category IS '商品分类';
COMMENT ON TABLE product_image IS '商品图片';
COMMENT ON TABLE product_sku IS '商品 SKU';
COMMENT ON TABLE product_spu IS '商品 SPU（品种）';
COMMENT ON TABLE supplier IS '供应商主档';
COMMENT ON TABLE supplier_sku IS '供应商供货 SKU（含采购单位与快照）';
COMMENT ON TABLE flyway_schema_history IS 'Flyway 迁移执行记录（Flyway 自身账本表）';

-- ----------------------------
-- 字段注释
-- ----------------------------

-- customer
COMMENT ON COLUMN customer.id IS '主键ID';
COMMENT ON COLUMN customer.customer_code IS '客户编码（业务唯一）';
COMMENT ON COLUMN customer.name IS '客户名称';
COMMENT ON COLUMN customer.customer_type_id IS '所属客户类型ID';
COMMENT ON COLUMN customer.status IS '状态（ENABLED/DISABLED）';
COMMENT ON COLUMN customer.parent_customer_id IS '上级客户ID（连锁/门店层级）';
COMMENT ON COLUMN customer.seller_id IS '归属业务员ID';
COMMENT ON COLUMN customer.supplier_id IS '关联供应商ID';
COMMENT ON COLUMN customer.contact_name IS '联系人姓名';
COMMENT ON COLUMN customer.contact_phone IS '联系电话';
COMMENT ON COLUMN customer.address IS '联系地址';
COMMENT ON COLUMN customer.settle_mode IS '结算方式';
COMMENT ON COLUMN customer.credit_limit IS '授信额度';
COMMENT ON COLUMN customer.credit_period_type IS '账期类型';
COMMENT ON COLUMN customer.credit_amount_threshold IS '账期触发金额阈值';
COMMENT ON COLUMN customer.credit_period_value IS '账期数值';
COMMENT ON COLUMN customer.credit_period_unit IS '账期单位';
COMMENT ON COLUMN customer.settle_day IS '结算日';
COMMENT ON COLUMN customer.remark IS '备注';
COMMENT ON COLUMN customer.version IS '乐观锁版本号';
COMMENT ON COLUMN customer.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN customer.created_at IS '创建时间';
COMMENT ON COLUMN customer.updated_at IS '更新时间';
COMMENT ON COLUMN customer.created_by IS '创建人';
COMMENT ON COLUMN customer.updated_by IS '更新人';
COMMENT ON COLUMN customer.visibility_policy IS '可见范围策略';

-- customer_agreement_price
COMMENT ON COLUMN customer_agreement_price.id IS '主键ID';
COMMENT ON COLUMN customer_agreement_price.customer_id IS '客户ID';
COMMENT ON COLUMN customer_agreement_price.sku_id IS '商品SKU ID';
COMMENT ON COLUMN customer_agreement_price.unit_price IS '协议单价';
COMMENT ON COLUMN customer_agreement_price.effective_from IS '生效时间（含）';
COMMENT ON COLUMN customer_agreement_price.effective_to IS '失效时间（不含），空表示长期有效';
COMMENT ON COLUMN customer_agreement_price.version IS '乐观锁版本号';
COMMENT ON COLUMN customer_agreement_price.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN customer_agreement_price.created_at IS '创建时间';
COMMENT ON COLUMN customer_agreement_price.updated_at IS '更新时间';
COMMENT ON COLUMN customer_agreement_price.created_by IS '创建人';
COMMENT ON COLUMN customer_agreement_price.updated_by IS '更新人';

-- customer_agreement_price_operation_log
COMMENT ON COLUMN customer_agreement_price_operation_log.id IS '主键ID';
COMMENT ON COLUMN customer_agreement_price_operation_log.agreement_price_id IS '协议价记录ID';
COMMENT ON COLUMN customer_agreement_price_operation_log.operation_type IS '操作类型';
COMMENT ON COLUMN customer_agreement_price_operation_log.operator IS '操作人';
COMMENT ON COLUMN customer_agreement_price_operation_log.before_data IS '变更前数据（JSON）';
COMMENT ON COLUMN customer_agreement_price_operation_log.after_data IS '变更后数据（JSON）';
COMMENT ON COLUMN customer_agreement_price_operation_log.created_at IS '创建时间';
COMMENT ON COLUMN customer_agreement_price_operation_log.created_by IS '创建人';

-- customer_price_batch_audit
COMMENT ON COLUMN customer_price_batch_audit.id IS '主键ID';
COMMENT ON COLUMN customer_price_batch_audit.batch_key IS '批次标识';
COMMENT ON COLUMN customer_price_batch_audit.operation_type IS '操作类型';
COMMENT ON COLUMN customer_price_batch_audit.result IS '执行结果';
COMMENT ON COLUMN customer_price_batch_audit.row_count IS '影响行数';
COMMENT ON COLUMN customer_price_batch_audit.error_data IS '失败明细（JSON）';
COMMENT ON COLUMN customer_price_batch_audit.created_at IS '创建时间';
COMMENT ON COLUMN customer_price_batch_audit.created_by IS '创建人';

-- customer_sku_visibility
COMMENT ON COLUMN customer_sku_visibility.id IS '主键ID';
COMMENT ON COLUMN customer_sku_visibility.customer_id IS '客户ID';
COMMENT ON COLUMN customer_sku_visibility.sku_id IS '商品SKU ID';
COMMENT ON COLUMN customer_sku_visibility.version IS '乐观锁版本号';
COMMENT ON COLUMN customer_sku_visibility.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN customer_sku_visibility.created_at IS '创建时间';
COMMENT ON COLUMN customer_sku_visibility.updated_at IS '更新时间';
COMMENT ON COLUMN customer_sku_visibility.created_by IS '创建人';
COMMENT ON COLUMN customer_sku_visibility.updated_by IS '更新人';

-- customer_type
COMMENT ON COLUMN customer_type.id IS '主键ID';
COMMENT ON COLUMN customer_type.type_code IS '客户类型编码（业务唯一）';
COMMENT ON COLUMN customer_type.name IS '客户类型名称';
COMMENT ON COLUMN customer_type.status IS '状态（ENABLED/DISABLED）';
COMMENT ON COLUMN customer_type.version IS '乐观锁版本号';
COMMENT ON COLUMN customer_type.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN customer_type.created_at IS '创建时间';
COMMENT ON COLUMN customer_type.updated_at IS '更新时间';
COMMENT ON COLUMN customer_type.created_by IS '创建人';
COMMENT ON COLUMN customer_type.updated_by IS '更新人';

-- customer_type_price
COMMENT ON COLUMN customer_type_price.id IS '主键ID';
COMMENT ON COLUMN customer_type_price.customer_type_id IS '客户类型ID';
COMMENT ON COLUMN customer_type_price.sku_id IS '商品SKU ID';
COMMENT ON COLUMN customer_type_price.unit_price IS '类型单价';
COMMENT ON COLUMN customer_type_price.effective_from IS '生效时间（含）';
COMMENT ON COLUMN customer_type_price.effective_to IS '失效时间（不含），空表示长期有效';
COMMENT ON COLUMN customer_type_price.version IS '乐观锁版本号';
COMMENT ON COLUMN customer_type_price.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN customer_type_price.created_at IS '创建时间';
COMMENT ON COLUMN customer_type_price.updated_at IS '更新时间';
COMMENT ON COLUMN customer_type_price.created_by IS '创建人';
COMMENT ON COLUMN customer_type_price.updated_by IS '更新人';

-- customer_type_price_operation_log
COMMENT ON COLUMN customer_type_price_operation_log.id IS '主键ID';
COMMENT ON COLUMN customer_type_price_operation_log.customer_type_price_id IS '客户类型价记录ID';
COMMENT ON COLUMN customer_type_price_operation_log.operation_type IS '操作类型';
COMMENT ON COLUMN customer_type_price_operation_log.operator IS '操作人';
COMMENT ON COLUMN customer_type_price_operation_log.before_data IS '变更前数据（JSON）';
COMMENT ON COLUMN customer_type_price_operation_log.after_data IS '变更后数据（JSON）';
COMMENT ON COLUMN customer_type_price_operation_log.created_at IS '创建时间';
COMMENT ON COLUMN customer_type_price_operation_log.created_by IS '创建人';

-- flyway_schema_history
COMMENT ON COLUMN flyway_schema_history.installed_rank IS '迁移执行序号（主键，按安装顺序递增）';
COMMENT ON COLUMN flyway_schema_history.version IS '迁移版本号，可重复执行(R)的脚本为 NULL';
COMMENT ON COLUMN flyway_schema_history.description IS '迁移描述（取自脚本文件名）';
COMMENT ON COLUMN flyway_schema_history.type IS '迁移类型（SQL / JDBC / BASELINE …）';
COMMENT ON COLUMN flyway_schema_history.script IS '迁移脚本文件名';
COMMENT ON COLUMN flyway_schema_history.checksum IS '脚本校验和，用于校验已应用迁移是否被改动';
COMMENT ON COLUMN flyway_schema_history.installed_by IS '执行该迁移的数据库用户';
COMMENT ON COLUMN flyway_schema_history.installed_on IS '迁移执行时间';
COMMENT ON COLUMN flyway_schema_history.execution_time IS '执行耗时（毫秒）';
COMMENT ON COLUMN flyway_schema_history.success IS '是否执行成功';

-- idempotency_record
COMMENT ON COLUMN idempotency_record.updated_by IS '更新人';

-- order_refund
COMMENT ON COLUMN order_refund.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN order_refund.updated_by IS '更新人';

-- order_return
COMMENT ON COLUMN order_return.rejected_at IS '驳回时间';
COMMENT ON COLUMN order_return.cancelled_at IS '取消时间';
COMMENT ON COLUMN order_return.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN order_return.updated_by IS '更新人';

-- order_return_item
COMMENT ON COLUMN order_return_item.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN order_return_item.updated_by IS '更新人';

-- product_category
COMMENT ON COLUMN product_category.id IS '主键ID';
COMMENT ON COLUMN product_category.parent_id IS '上级分类ID，空表示顶级';
COMMENT ON COLUMN product_category.category_code IS '分类编码（业务唯一）';
COMMENT ON COLUMN product_category.name IS '分类名称';
COMMENT ON COLUMN product_category.level IS '层级（从1开始）';
COMMENT ON COLUMN product_category.sort_order IS '排序号';
COMMENT ON COLUMN product_category.status IS '状态（ENABLED/DISABLED）';
COMMENT ON COLUMN product_category.version IS '乐观锁版本号';
COMMENT ON COLUMN product_category.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN product_category.created_at IS '创建时间';
COMMENT ON COLUMN product_category.updated_at IS '更新时间';
COMMENT ON COLUMN product_category.created_by IS '创建人';
COMMENT ON COLUMN product_category.updated_by IS '更新人';

-- product_image
COMMENT ON COLUMN product_image.id IS '主键ID';
COMMENT ON COLUMN product_image.spu_id IS '所属商品SPU ID';
COMMENT ON COLUMN product_image.file_key IS '对象存储 fileKey';
COMMENT ON COLUMN product_image.file_url IS '文件访问地址';
COMMENT ON COLUMN product_image.file_name IS '原始文件名';
COMMENT ON COLUMN product_image.file_size IS '文件大小（字节）';
COMMENT ON COLUMN product_image.is_primary IS '是否主图';
COMMENT ON COLUMN product_image.sort_order IS '排序号';
COMMENT ON COLUMN product_image.version IS '乐观锁版本号';
COMMENT ON COLUMN product_image.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN product_image.created_at IS '创建时间';
COMMENT ON COLUMN product_image.updated_at IS '更新时间';
COMMENT ON COLUMN product_image.created_by IS '创建人';
COMMENT ON COLUMN product_image.updated_by IS '更新人';

-- product_sku
COMMENT ON COLUMN product_sku.id IS '主键ID';
COMMENT ON COLUMN product_sku.spu_id IS '所属商品SPU ID';
COMMENT ON COLUMN product_sku.sku_code IS 'SKU 编码（业务唯一）';
COMMENT ON COLUMN product_sku.barcode IS '条形码';
COMMENT ON COLUMN product_sku.spec_name IS '规格名称';
COMMENT ON COLUMN product_sku.spec_values IS '规格值（JSON）';
COMMENT ON COLUMN product_sku.sale_unit IS '销售单位';
COMMENT ON COLUMN product_sku.product_type IS '商品类型';
COMMENT ON COLUMN product_sku.market_price IS '市场价';
COMMENT ON COLUMN product_sku.status IS '状态（ENABLED/DISABLED）';
COMMENT ON COLUMN product_sku.is_default IS '是否默认SKU';
COMMENT ON COLUMN product_sku.sort_order IS '排序号';
COMMENT ON COLUMN product_sku.version IS '乐观锁版本号';
COMMENT ON COLUMN product_sku.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN product_sku.created_at IS '创建时间';
COMMENT ON COLUMN product_sku.updated_at IS '更新时间';
COMMENT ON COLUMN product_sku.created_by IS '创建人';
COMMENT ON COLUMN product_sku.updated_by IS '更新人';

-- product_spu
COMMENT ON COLUMN product_spu.id IS '主键ID';
COMMENT ON COLUMN product_spu.spu_code IS 'SPU 编码（业务唯一）';
COMMENT ON COLUMN product_spu.name IS '商品名称';
COMMENT ON COLUMN product_spu.alias IS '商品别名';
COMMENT ON COLUMN product_spu.category_id IS '所属分类ID';
COMMENT ON COLUMN product_spu.description IS '商品描述';
COMMENT ON COLUMN product_spu.status IS '状态（ENABLED/DISABLED）';
COMMENT ON COLUMN product_spu.version IS '乐观锁版本号';
COMMENT ON COLUMN product_spu.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN product_spu.created_at IS '创建时间';
COMMENT ON COLUMN product_spu.updated_at IS '更新时间';
COMMENT ON COLUMN product_spu.created_by IS '创建人';
COMMENT ON COLUMN product_spu.updated_by IS '更新人';

-- sales_order
COMMENT ON COLUMN sales_order.updated_by IS '更新人';

-- sales_order_item
COMMENT ON COLUMN sales_order_item.updated_by IS '更新人';

-- supplier
COMMENT ON COLUMN supplier.id IS '主键ID';
COMMENT ON COLUMN supplier.supplier_code IS '供应商编码（业务唯一）';
COMMENT ON COLUMN supplier.name IS '供应商名称';
COMMENT ON COLUMN supplier.status IS '状态（ENABLED/DISABLED）';
COMMENT ON COLUMN supplier.contact_name IS '联系人姓名';
COMMENT ON COLUMN supplier.contact_phone IS '联系电话';
COMMENT ON COLUMN supplier.address IS '联系地址';
COMMENT ON COLUMN supplier.remark IS '备注';
COMMENT ON COLUMN supplier.version IS '乐观锁版本号';
COMMENT ON COLUMN supplier.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN supplier.created_at IS '创建时间';
COMMENT ON COLUMN supplier.updated_at IS '更新时间';
COMMENT ON COLUMN supplier.created_by IS '创建人';
COMMENT ON COLUMN supplier.updated_by IS '更新人';

-- supplier_sku
COMMENT ON COLUMN supplier_sku.id IS '主键ID';
COMMENT ON COLUMN supplier_sku.supplier_id IS '供应商ID';
COMMENT ON COLUMN supplier_sku.sku_id IS '商品SKU ID';
COMMENT ON COLUMN supplier_sku.supplier_code_snapshot IS '供应商编码快照';
COMMENT ON COLUMN supplier_sku.supplier_name_snapshot IS '供应商名称快照';
COMMENT ON COLUMN supplier_sku.sku_code_snapshot IS 'SKU 编码快照';
COMMENT ON COLUMN supplier_sku.sku_name_snapshot IS 'SKU 名称快照';
COMMENT ON COLUMN supplier_sku.spec_values_snapshot IS '规格值快照（JSON）';
COMMENT ON COLUMN supplier_sku.purchase_unit IS '采购单位（库存记账单位以此为准）';
COMMENT ON COLUMN supplier_sku.reference_price IS '参考采购价';
COMMENT ON COLUMN supplier_sku.purchaser_id IS '对接采购员ID';
COMMENT ON COLUMN supplier_sku.is_default IS '是否默认供应商';
COMMENT ON COLUMN supplier_sku.status IS '状态（ENABLED/DISABLED）';
COMMENT ON COLUMN supplier_sku.version IS '乐观锁版本号';
COMMENT ON COLUMN supplier_sku.deleted IS '软删除标记（false=未删除）';
COMMENT ON COLUMN supplier_sku.created_at IS '创建时间';
COMMENT ON COLUMN supplier_sku.updated_at IS '更新时间';
COMMENT ON COLUMN supplier_sku.created_by IS '创建人';
COMMENT ON COLUMN supplier_sku.updated_by IS '更新人';

-- t_config
COMMENT ON COLUMN t_config.config_id IS '配置ID';
COMMENT ON COLUMN t_config.remark IS '备注';
COMMENT ON COLUMN t_config.update_time IS '更新时间';
COMMENT ON COLUMN t_config.create_time IS '创建时间';

-- t_data_tracer
COMMENT ON COLUMN t_data_tracer.data_tracer_id IS '数据追踪ID';

-- t_department
COMMENT ON COLUMN t_department.department_id IS '部门ID';
COMMENT ON COLUMN t_department.department_name IS '部门名称';
COMMENT ON COLUMN t_department.sort IS '排序号';
COMMENT ON COLUMN t_department.update_time IS '更新时间';
COMMENT ON COLUMN t_department.create_time IS '创建时间';

-- t_dict
COMMENT ON COLUMN t_dict.dict_id IS '字典ID';
COMMENT ON COLUMN t_dict.remark IS '备注';
COMMENT ON COLUMN t_dict.create_time IS '创建时间';
COMMENT ON COLUMN t_dict.update_time IS '更新时间';

-- t_dict_data
COMMENT ON COLUMN t_dict_data.dict_data_id IS '字典数据ID';
COMMENT ON COLUMN t_dict_data.remark IS '备注';
COMMENT ON COLUMN t_dict_data.disabled_flag IS '禁用标记（false=启用）';
COMMENT ON COLUMN t_dict_data.create_time IS '创建时间';
COMMENT ON COLUMN t_dict_data.update_time IS '更新时间';

-- t_employee
COMMENT ON COLUMN t_employee.avatar IS '头像地址';
COMMENT ON COLUMN t_employee.phone IS '手机号';
COMMENT ON COLUMN t_employee.email IS '邮箱';
COMMENT ON COLUMN t_employee.remark IS '备注';
COMMENT ON COLUMN t_employee.update_time IS '更新时间';
COMMENT ON COLUMN t_employee.create_time IS '创建时间';

-- t_help_doc
COMMENT ON COLUMN t_help_doc.help_doc_id IS '帮助文档ID';
COMMENT ON COLUMN t_help_doc.update_time IS '更新时间';
COMMENT ON COLUMN t_help_doc.create_time IS '创建时间';

-- t_help_doc_catalog
COMMENT ON COLUMN t_help_doc_catalog.create_time IS '创建时间';
COMMENT ON COLUMN t_help_doc_catalog.update_time IS '更新时间';

-- t_help_doc_relation
COMMENT ON COLUMN t_help_doc_relation.create_time IS '创建时间';
COMMENT ON COLUMN t_help_doc_relation.update_time IS '更新时间';

-- t_help_doc_view_record
COMMENT ON COLUMN t_help_doc_view_record.create_time IS '创建时间';
COMMENT ON COLUMN t_help_doc_view_record.update_time IS '更新时间';

-- t_login_fail
COMMENT ON COLUMN t_login_fail.login_fail_id IS '登录失败记录ID';
COMMENT ON COLUMN t_login_fail.user_id IS '用户ID';
COMMENT ON COLUMN t_login_fail.user_type IS '用户类型';
COMMENT ON COLUMN t_login_fail.login_name IS '登录名';
COMMENT ON COLUMN t_login_fail.login_fail_count IS '连续失败次数';
COMMENT ON COLUMN t_login_fail.login_lock_begin_time IS '锁定开始时间';
COMMENT ON COLUMN t_login_fail.create_time IS '创建时间';
COMMENT ON COLUMN t_login_fail.update_time IS '更新时间';

-- t_login_log
COMMENT ON COLUMN t_login_log.login_log_id IS '登录日志ID';
COMMENT ON COLUMN t_login_log.user_id IS '用户ID';
COMMENT ON COLUMN t_login_log.user_type IS '用户类型';
COMMENT ON COLUMN t_login_log.user_name IS '用户姓名';
COMMENT ON COLUMN t_login_log.login_ip IS '登录IP';
COMMENT ON COLUMN t_login_log.login_ip_region IS '登录IP归属地';
COMMENT ON COLUMN t_login_log.user_agent IS '浏览器 User-Agent';
COMMENT ON COLUMN t_login_log.login_device IS '登录设备';
COMMENT ON COLUMN t_login_log.remark IS '备注';
COMMENT ON COLUMN t_login_log.update_time IS '更新时间';
COMMENT ON COLUMN t_login_log.create_time IS '创建时间';

-- t_mail_template
COMMENT ON COLUMN t_mail_template.template_code IS '邮件模板编码';

-- t_menu
COMMENT ON COLUMN t_menu.menu_id IS '菜单ID';
COMMENT ON COLUMN t_menu.menu_name IS '菜单名称';
COMMENT ON COLUMN t_menu.sort IS '排序号';
COMMENT ON COLUMN t_menu.path IS '路由路径';
COMMENT ON COLUMN t_menu.icon IS '图标';
COMMENT ON COLUMN t_menu.frame_url IS '内嵌页面地址';
COMMENT ON COLUMN t_menu.create_user_id IS '创建人ID';
COMMENT ON COLUMN t_menu.create_time IS '创建时间';
COMMENT ON COLUMN t_menu.update_user_id IS '更新人ID';
COMMENT ON COLUMN t_menu.update_time IS '更新时间';

-- t_notice
COMMENT ON COLUMN t_notice.notice_id IS '通知公告ID';
COMMENT ON COLUMN t_notice.deleted_flag IS '软删除标记（false=未删除）';
COMMENT ON COLUMN t_notice.update_time IS '更新时间';
COMMENT ON COLUMN t_notice.create_time IS '创建时间';

-- t_notice_type
COMMENT ON COLUMN t_notice_type.create_time IS '创建时间';
COMMENT ON COLUMN t_notice_type.update_time IS '更新时间';

-- t_notice_view_record
COMMENT ON COLUMN t_notice_view_record.create_time IS '创建时间';
COMMENT ON COLUMN t_notice_view_record.update_time IS '更新时间';

-- t_notice_visible_range
COMMENT ON COLUMN t_notice_visible_range.create_time IS '创建时间';

-- t_password_log
COMMENT ON COLUMN t_password_log.id IS '主键ID';
COMMENT ON COLUMN t_password_log.user_id IS '用户ID';
COMMENT ON COLUMN t_password_log.user_type IS '用户类型';
COMMENT ON COLUMN t_password_log.old_password IS '旧口令（加密存储）';
COMMENT ON COLUMN t_password_log.new_password IS '新口令（加密存储）';
COMMENT ON COLUMN t_password_log.update_time IS '更新时间';
COMMENT ON COLUMN t_password_log.create_time IS '创建时间';

-- t_position
COMMENT ON COLUMN t_position.position_id IS '职务ID';
COMMENT ON COLUMN t_position.position_name IS '职务名称';
COMMENT ON COLUMN t_position.position_level IS '职级';
COMMENT ON COLUMN t_position.sort IS '排序号';
COMMENT ON COLUMN t_position.remark IS '备注';
COMMENT ON COLUMN t_position.deleted_flag IS '软删除标记（false=未删除）';
COMMENT ON COLUMN t_position.create_time IS '创建时间';
COMMENT ON COLUMN t_position.update_time IS '更新时间';

-- t_role
COMMENT ON COLUMN t_role.role_id IS '角色ID';
COMMENT ON COLUMN t_role.role_name IS '角色名称';
COMMENT ON COLUMN t_role.role_code IS '角色编码';
COMMENT ON COLUMN t_role.remark IS '备注';
COMMENT ON COLUMN t_role.update_time IS '更新时间';
COMMENT ON COLUMN t_role.create_time IS '创建时间';

-- t_role_data_scope
COMMENT ON COLUMN t_role_data_scope.id IS '主键ID';
COMMENT ON COLUMN t_role_data_scope.data_scope_type IS '数据范围类型';
COMMENT ON COLUMN t_role_data_scope.view_type IS '可见范围类型';
COMMENT ON COLUMN t_role_data_scope.role_id IS '角色ID';
COMMENT ON COLUMN t_role_data_scope.update_time IS '更新时间';
COMMENT ON COLUMN t_role_data_scope.create_time IS '创建时间';

-- t_role_employee
COMMENT ON COLUMN t_role_employee.id IS '主键ID';
COMMENT ON COLUMN t_role_employee.role_id IS '角色ID';
COMMENT ON COLUMN t_role_employee.employee_id IS '员工ID';
COMMENT ON COLUMN t_role_employee.update_time IS '更新时间';
COMMENT ON COLUMN t_role_employee.create_time IS '创建时间';

-- t_role_menu
COMMENT ON COLUMN t_role_menu.role_menu_id IS '主键ID';
COMMENT ON COLUMN t_role_menu.role_id IS '角色ID';
COMMENT ON COLUMN t_role_menu.menu_id IS '菜单ID';
COMMENT ON COLUMN t_role_menu.update_time IS '更新时间';
COMMENT ON COLUMN t_role_menu.create_time IS '创建时间';

-- t_smart_job
COMMENT ON COLUMN t_smart_job.create_time IS '创建时间';
COMMENT ON COLUMN t_smart_job.update_time IS '更新时间';

-- t_smart_job_log
COMMENT ON COLUMN t_smart_job_log.log_id IS '日志ID';
COMMENT ON COLUMN t_smart_job_log.execute_result IS '执行结果';
COMMENT ON COLUMN t_smart_job_log.create_time IS '创建时间';

-- t_table_column
COMMENT ON COLUMN t_table_column.table_column_id IS '主键ID';
COMMENT ON COLUMN t_table_column.create_time IS '创建时间';
COMMENT ON COLUMN t_table_column.update_time IS '更新时间';

-- ----------------------------
-- 覆盖 V5 遗留的上游品牌注释
-- ----------------------------
-- 该列在 V5 已有注释，但内容是上游参考项目的字样；V5 属冻结迁移不可改，故在此覆盖。
COMMENT ON COLUMN t_notice.document_number IS '文号，如：鲜蔬源〔2026〕字第36号';

