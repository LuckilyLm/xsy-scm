package com.xsy.scm.common;

import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.test.PgITPaths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
class ScmMigrationTargetPgIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void reachesTheRequestedMigrationVersion() {
        String expected = System.getProperty("xsy.migration.expected-version", "137");
        String current = jdbc.queryForObject("SELECT version FROM flyway_schema_history "
                + "WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1", String.class);
        assertThat(current).isEqualTo(expected);
    }

    /**
     * 数据库版本达到 V128 时，必须包含券模板领取限制、订单预选券和券发放来源字段。
     * 目标版本早于 V128 时跳过检查。
     */
    @Test
    void mallClaimColumnsExistWhenTargetIs128OrLater() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 128) {
            return;
        }
        assertColumn("xsy_promotion_coupon", "claimable");
        assertColumn("xsy_promotion_coupon", "per_customer_limit");
        assertColumn("xsy_promotion_coupon", "total_limit");
        assertColumn("xsy_sales_order", "mall_coupon_instance_id");
        assertColumn("xsy_promotion_coupon_instance", "issue_source");
    }

    /**
     * 数据库版本达到 V129 时，订单必须包含结算方式列和取值约束。
     * 结算方式仅表示约定，余额、授信和收款仍由各自业务模块管理。
     */
    @Test
    void settlementMethodColumnExistsWhenTargetIs129OrLater() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 129) {
            return;
        }
        assertColumn("xsy_sales_order", "settlement_method");
        Integer allowed = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_sales_order_settlement_method'",
                Integer.class);
        assertThat(allowed).as("结算方式取值约束应随列一起建立").isEqualTo(1);
    }

    /**
     * V2 数据库禁止外键约束（见 AGENTS.md）。V127 清理最后一个外键后，
     * xsy_v2 schema 的外键数量必须保持为零。
     */
    @Test
    void noForeignKeysAcrossSchema() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint c "
                        + "WHERE c.contype = 'f' AND c.connamespace = 'xsy_v2'::regnamespace",
                Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void orderExceptionCompensationTablesExistFromV148() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 148) {
            return;
        }
        assertColumn("xsy_order_exchange_link", "return_id");
        assertColumn("xsy_order_exchange_link", "replacement_order_id");
        assertColumn("xsy_order_received_quantity_correction", "previous_received_quantity");
        assertColumn("xsy_order_received_quantity_correction", "shipped_quantity");
        Integer uniqueIndexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy_v2' "
                        + "AND indexname IN ('uk_order_exchange_return', 'uk_order_exchange_replacement')",
                Integer.class);
        assertThat(uniqueIndexes).isEqualTo(2);
    }

    @Test
    void codPermissionDefaultsToDisabledFromV130() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 130) {
            return;
        }
        assertColumn("xsy_customer", "cod_allowed");
        Integer notAllowed = jdbc.queryForObject(
                "SELECT count(*) FROM xsy_customer WHERE cod_allowed IS NULL", Integer.class);
        assertThat(notAllowed).isZero();
        String defaultExpression = jdbc.queryForObject(
                "SELECT column_default FROM information_schema.columns "
                        + "WHERE table_schema = 'xsy_v2' AND table_name = 'xsy_customer' AND column_name = 'cod_allowed'",
                String.class);
        assertThat(defaultExpression).contains("false");
    }

    @Test
    void codInstallmentOrderColumnExistsFromV132() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 132) {
            return;
        }
        assertColumn("xsy_finance_receipt", "cod_order_id");
    }

    @Test
    void mallWarehouseAndConversionSchemaExistsAt137() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 137) {
            return;
        }
        assertColumn("xsy_customer", "mall_delivery_warehouse_id");
        assertColumn("xsy_sales_order", "mall_delivery_warehouse_id");
        assertColumn("xsy_sales_order_item", "mall_stock_unit_snapshot");
        assertColumn("xsy_sales_order_item", "mall_stock_units_per_sale_unit");
        assertColumn("xsy_inventory_outbound_item", "stock_quantity");
        assertColumn("xsy_order_return_receipt_item", "stock_quantity");
        assertColumn("xsy_order_return_receipt_item", "stock_unit_snapshot");
        assertColumn("xsy_product_sku_stock_conversion", "stock_units_per_sale_unit");
        assertColumn("xsy_mall_district_warehouse", "priority");

        Integer constraints = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE connamespace = 'xsy_v2'::regnamespace "
                        + "AND conname IN ('ck_order_item_mall_stock_factor', "
                        + "'ck_order_return_receipt_stock_quantity', 'ck_order_return_receipt_stock_snapshot_pair')",
                Integer.class);
        assertThat(constraints).isEqualTo(3);
        Integer indexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname='xsy_v2' "
                        + "AND indexname IN ('uk_mall_district_warehouse_active', "
                        + "'uk_product_sku_stock_conversion_active')", Integer.class);
        assertThat(indexes).isEqualTo(2);
    }

    @Test
    void sortingPartRecordSchemaExistsAt141() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 141)
            return;
        assertColumn("xsy_sorting_part_record", "task_id");
        assertColumn("xsy_sorting_part_record", "task_item_id");
        assertColumn("xsy_sorting_part_record", "quantity_delta");
        assertColumn("xsy_sorting_part_record", "total_quantity");
        Integer checks = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE connamespace='xsy_v2'::regnamespace "
                        + "AND conname IN ('ck_sorting_part_record_event','ck_sorting_part_record_delta',"
                        + "'ck_sorting_part_record_total')", Integer.class);
        assertThat(checks).isEqualTo(3);
    }

    @Test
    void purchaseInquiryAndImmutableQuoteSchemaExistAt143() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 143)
            return;
        assertColumn("xsy_purchase_inquiry", "accepted_quote_id");
        assertColumn("xsy_purchase_inquiry", "required_quantity");
        assertColumn("xsy_purchase_inquiry_quote", "revision");
        assertColumn("xsy_purchase_inquiry_quote", "valid_until");
        Integer guards = jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname IN "
                        + "('trg_purchase_inquiry_guard', 'trg_purchase_inquiry_quote_immutable') "
                        + "AND NOT tgisinternal", Integer.class);
        assertThat(guards).isEqualTo(2);
    }

    @Test
    void personalFilterPresetSchemaExistsAt144() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 144) return;
        assertColumn("xsy_user_filter_preset", "owner_key");
        assertColumn("xsy_user_filter_preset", "view_key");
        assertColumn("xsy_user_filter_preset", "conditions");
        Integer unique = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE connamespace = 'xsy_v2'::regnamespace "
                        + "AND conname = 'uk_user_filter_preset_name'", Integer.class);
        assertThat(unique).isEqualTo(1);
    }

    @Test
    void orderTagSchemaExistsAt145() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 145) return;
        assertColumn("xsy_order_tag", "name");
        assertColumn("xsy_order_tag", "color");
        assertColumn("xsy_order_tag_link", "order_id");
        assertColumn("xsy_order_tag_link", "tag_id");
        Integer indexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname='xsy_v2' "
                        + "AND indexname IN ('uk_order_tag_name','idx_order_tag_link_tag_order')",
                Integer.class);
        assertThat(indexes).isEqualTo(2);
    }

    @Test
    void salesOrderPrintTypeAndDefaultTemplateExistAt146() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 146) return;
        Integer defaults = jdbc.queryForObject(
                "SELECT count(*) FROM xsy_print_template "
                        + "WHERE document_type='SALES_ORDER' AND template_code='SALES_ORDER_DEFAULT' "
                        + "AND default_flag=TRUE AND enabled_flag=TRUE AND deleted=FALSE",
                Integer.class);
        assertThat(defaults).isEqualTo(1);
        Integer constraints = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE connamespace='xsy_v2'::regnamespace "
                        + "AND conname IN ('ck_scm_print_template_document_type', "
                        + "'ck_scm_print_record_document_type') "
                        + "AND pg_get_constraintdef(oid) LIKE '%SALES_ORDER%'",
                Integer.class);
        assertThat(constraints).isEqualTo(2);
    }

    /**
     * V151 起采购收货可登记批次：仓库开关、批次档案、收货行批次事实、批次子账（余额 + 只追加流水）。
     * 批次是独立子账，总账唯一键不变。
     */
    @Test
    void inventoryLotSchemaExistsFromV151() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 151) {
            return;
        }
        assertColumn("xsy_warehouse", "lot_tracking_enabled");
        assertColumn("xsy_trace_lot", "lot_no");
        assertColumn("xsy_trace_lot", "source_lot_no");
        assertColumn("xsy_purchase_receipt_item_lot", "lot_id");
        assertColumn("xsy_inventory_lot_balance", "quantity");
        assertColumn("xsy_inventory_lot_movement", "before_quantity");
        assertColumn("xsy_inventory_lot_movement", "after_quantity");
        Integer uniqueIndexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy_v2' "
                        + "AND indexname IN ('uk_trace_lot_no', 'uk_inventory_lot_balance', "
                        + "'uk_purchase_receipt_item_lot')",
                Integer.class);
        assertThat(uniqueIndexes).as("批次唯一索引应随表一起建立").isEqualTo(3);
        Integer appendOnly = jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_inventory_lot_movement_append_only' "
                        + "AND NOT tgisinternal",
                Integer.class);
        assertThat(appendOnly).as("批次流水必须只追加").isEqualTo(1);
        Integer menus = jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id = 2100 AND menu_type = 2", Integer.class);
        assertThat(menus).as("批次库存页面应随迁移发布").isEqualTo(1);
        Integer perms = jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id = 2101 "
                        + "AND api_perms = 'scm:inventory:lot:query'", Integer.class);
        assertThat(perms).as("批次查询权限应随迁移发布").isEqualTo(1);
    }

    /**
     * V152 起包装码（一物一码）：单元表 + 只追加事件表，码不透明且唯一。
     */
    @Test
    void traceUnitSchemaExistsFromV152() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 152) {
            return;
        }
        assertColumn("xsy_trace_unit", "unit_code");
        assertColumn("xsy_trace_unit", "lot_id");
        assertColumn("xsy_trace_unit", "package_type");
        assertColumn("xsy_trace_event", "event_type");
        Integer uniqueIndexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy_v2' AND indexname = 'uk_trace_unit_code'",
                Integer.class);
        assertThat(uniqueIndexes).as("包装码必须全局唯一").isEqualTo(1);
        Integer appendOnly = jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_trace_event_append_only' AND NOT tgisinternal",
                Integer.class);
        assertThat(appendOnly).as("包装事件必须只追加").isEqualTo(1);
        Integer menus = jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 2110 AND 2112", Integer.class);
        assertThat(menus).as("包装码页面与两条权限应随迁移发布").isEqualTo(3);
    }

    /**
     * V153 起包装码可绑到分拣任务行：一码一物、绑定只追加。
     */
    @Test
    void traceUnitBindingSchemaExistsFromV153() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 153) {
            return;
        }
        assertColumn("xsy_trace_unit_binding", "unit_id");
        assertColumn("xsy_trace_unit_binding", "sorting_task_item_id");
        assertColumn("xsy_trace_unit_binding", "customer_name_snapshot");
        // V157 起「一码一绑」改为「同一时刻只有一条有效绑定」：唯一约束被去掉，由触发器保证，
        // 这样解绑后可以重新绑定。历史断言按目标版本分流，避免把已演进的约束当成永恒事实。
        Integer uniqueIndexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy_v2' "
                        + "AND indexname = 'uk_trace_unit_binding_unit'",
                Integer.class);
        if (target >= 157) {
            assertThat(uniqueIndexes).as("V157 起唯一约束由有效绑定触发器取代").isZero();
            Integer activeTrigger = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_trace_unit_binding_active' "
                            + "AND NOT tgisinternal", Integer.class);
            assertThat(activeTrigger).as("有效绑定唯一必须由触发器兜底").isEqualTo(1);
        } else {
            assertThat(uniqueIndexes).as("一个包装码只能绑一次").isEqualTo(1);
        }
        Integer appendOnly = jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_trace_unit_binding_append_only' "
                        + "AND NOT tgisinternal",
                Integer.class);
        assertThat(appendOnly).as("绑定事实必须只追加").isEqualTo(1);
        Integer eventTypes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_trace_event_type' "
                        + "AND pg_get_constraintdef(oid) LIKE '%BIND%'",
                Integer.class);
        assertThat(eventTypes).as("绑定事件类型应已放开").isEqualTo(1);
        Integer menus = jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id = 2113 "
                        + "AND api_perms = 'scm:inventory:unit:bind'", Integer.class);
        assertThat(menus).as("绑定权限应随迁移发布").isEqualTo(1);
    }

    /**
     * V154 起包装码标签走打印中心：单据类型放开到 TRACE_UNIT，并自带默认标签模板。
     */
    @Test
    void traceUnitPrintSchemaExistsFromV154() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 154) {
            return;
        }
        Integer templateConstraint = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_scm_print_template_document_type' "
                        + "AND pg_get_constraintdef(oid) LIKE '%TRACE_UNIT%'",
                Integer.class);
        assertThat(templateConstraint).as("模板类型应放开到包装码").isEqualTo(1);
        Integer recordConstraint = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_scm_print_record_document_type' "
                        + "AND pg_get_constraintdef(oid) LIKE '%TRACE_UNIT%'",
                Integer.class);
        assertThat(recordConstraint).as("打印记录类型应放开到包装码").isEqualTo(1);
        Integer templates = jdbc.queryForObject(
                "SELECT count(*) FROM xsy_print_template WHERE document_type = 'TRACE_UNIT' "
                        + "AND default_flag = TRUE AND enabled_flag = TRUE AND deleted = FALSE",
                Integer.class);
        assertThat(templates).as("包装码标签默认模板应存在且唯一").isEqualTo(1);
        String paper = jdbc.queryForObject(
                "SELECT model->>'paper' FROM xsy_print_template WHERE document_type = 'TRACE_UNIT' "
                        + "AND default_flag = TRUE AND deleted = FALSE",
                String.class);
        assertThat(paper).as("标签用 80mm 热敏纸").isEqualTo("TICKET_80");
    }

    /**
     * V155 起包装码可以作废：作废权限随迁移发布。
     */
    @Test
    void traceUnitVoidPermissionExistsFromV155() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 155) {
            return;
        }
        Integer menus = jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id = 2114 "
                        + "AND api_perms = 'scm:inventory:unit:void'", Integer.class);
        assertThat(menus).as("作废权限应随迁移发布").isEqualTo(1);
    }

    /**
     * V156 起数据库兜底：绑定不能挂到作废码上。
     */
    @Test
    void traceUnitBindingActiveGuardExistsFromV156() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 156) {
            return;
        }
        Integer trigger = jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_trace_unit_binding_active' "
                        + "AND NOT tgisinternal",
                Integer.class);
        assertThat(trigger).as("绑定到作废码必须被数据库拒绝").isEqualTo(1);
    }

    /**
     * V157 起更正流程：申请单 + 解绑事实，有效绑定唯一由触发器保证。
     */
    @Test
    void traceUnitCorrectionSchemaExistsFromV157() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 157) {
            return;
        }
        assertColumn("xsy_trace_unit_correction", "binding_id");
        assertColumn("xsy_trace_unit_unbind", "correction_id");
        Integer uniqueIndexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy_v2' "
                        + "AND indexname = 'uk_trace_unit_correction_pending'", Integer.class);
        assertThat(uniqueIndexes).as("一个码只能有一条待审批申请").isEqualTo(1);
        Integer dropped = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'uk_trace_unit_binding_unit'",
                Integer.class);
        assertThat(dropped).as("旧的「一码一绑」唯一约束应被移除，改由有效绑定触发器保证").isZero();
        Integer eventTypes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_trace_event_type' "
                        + "AND pg_get_constraintdef(oid) LIKE '%UNBIND%'", Integer.class);
        assertThat(eventTypes).as("解绑事件类型应已放开").isEqualTo(1);
        Integer menus = jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 2115 AND 2117", Integer.class);
        assertThat(menus).as("更正页面与两条权限应随迁移发布").isEqualTo(3);
    }

    /** V158：有效绑定共享码行锁，正式出库与解绑共享原订单行锁。 */
    @Test
    void traceUnitConsistencyGuardsExistFromV158() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 158) {
            return;
        }
        Integer triggers = jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger t "
                        + "JOIN pg_class c ON c.oid=t.tgrelid "
                        + "JOIN pg_namespace n ON n.oid=c.relnamespace "
                        + "WHERE n.nspname='xsy_v2' AND NOT t.tgisinternal AND t.tgname IN "
                        + "('trg_trace_unit_binding_active', 'trg_trace_unbind_outbound_guard', "
                        + "'trg_trace_lock_outbound_source_item', 'trg_trace_lock_outbound_confirmation', "
                        + "'trg_trace_unit_void_active_guard')",
                Integer.class);
        assertThat(triggers).as("绑定、解绑、出库、作废五个数据库触发器必须存在").isEqualTo(5);
        String bindSource = jdbc.queryForObject(
                "SELECT p.prosrc FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace "
                        + "WHERE n.nspname='xsy_v2' AND p.proname = 'xsy_assert_unit_bindable'", String.class);
        assertThat(bindSource).contains("FOR UPDATE", "xsy_inventory_outbound_item");
        String unbindSource = jdbc.queryForObject(
                "SELECT p.prosrc FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace "
                        + "WHERE n.nspname='xsy_v2' AND p.proname = 'xsy_guard_trace_unit_unbind_outbound'", String.class);
        assertThat(unbindSource).contains("FOR UPDATE", "xsy_inventory_outbound_item");
    }

    /**
     * V159 起采购单可二维码分享：令牌只存哈希、一单一条有效链接、回填只追加。
     */
    @Test
    void purchaseShareSchemaExistsFromV159() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 159) {
            return;
        }
        assertColumn("xsy_purchase_share_token", "token_hash");
        assertColumn("xsy_purchase_share_token", "expires_at");
        assertColumn("xsy_purchase_share_feedback", "purchase_order_item_id");
        Integer uniqueIndexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy_v2' "
                        + "AND indexname IN ('uk_purchase_share_token_hash', 'uk_purchase_share_active_order')",
                Integer.class);
        assertThat(uniqueIndexes).as("令牌哈希唯一且一单只有一条有效链接").isEqualTo(2);
        Integer appendOnly = jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_purchase_share_feedback_append_only' "
                        + "AND NOT tgisinternal",
                Integer.class);
        assertThat(appendOnly).as("回填事实必须只追加").isEqualTo(1);
        Integer menus = jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 2118 AND 2119", Integer.class);
        assertThat(menus).as("分享与撤销权限应随迁移发布").isEqualTo(2);
    }

    /**
     * V160 起客户可发推广二维码：一客一条有效码，扫码记录只追加。
     */
    @Test
    void customerPromotionSchemaExistsFromV160() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 160) {
            return;
        }
        assertColumn("xsy_customer_promotion_code", "code");
        assertColumn("xsy_customer_promotion_scan", "seller_id");
        Integer uniqueIndexes = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'xsy_v2' "
                        + "AND indexname IN ('uk_customer_promotion_code', 'uk_customer_promotion_active')",
                Integer.class);
        assertThat(uniqueIndexes).as("码全局唯一且一客只有一条有效码").isEqualTo(2);
        Integer appendOnly = jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_customer_promotion_scan_append_only' "
                        + "AND NOT tgisinternal",
                Integer.class);
        assertThat(appendOnly).as("扫码记录必须只追加").isEqualTo(1);
        Integer menus = jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id = 2120 "
                        + "AND api_perms = 'scm:customer:promotion:manage'", Integer.class);
        assertThat(menus).as("推广码权限应随迁移发布").isEqualTo(1);
    }

    /**
     * V161 起有时价：规格级、区间半开、价格来源放开到 TIME_PRICE。
     */
    @Test
    void timePriceSchemaExistsFromV161() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 161) {
            return;
        }
        assertColumn("xsy_pricing_time_price", "unit_price");
        assertColumn("xsy_pricing_time_price", "start_at");
        Integer draftSource = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_sales_order_item_draft_source' "
                        + "AND pg_get_constraintdef(oid) LIKE '%TIME_PRICE%'", Integer.class);
        assertThat(draftSource).as("下单价格来源应放开到时价").isEqualTo(1);
        Integer lockedSource = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_sales_order_item_locked_source' "
                        + "AND pg_get_constraintdef(oid) LIKE '%TIME_PRICE%'", Integer.class);
        assertThat(lockedSource).as("锁价来源应放开到时价").isEqualTo(1);
        Integer menus = jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id IN (2121, 2122)", Integer.class);
        assertThat(menus).as("时价页面与维护权限应随迁移发布").isEqualTo(2);
    }

    /**
     * V162 起分拣小票抬头带供应商来源，代分拣的货能直接把票交给供应商。
     */
    @Test
    void sortingTicketShowsSupplierFromV162() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 162) {
            return;
        }
        Integer tickets = jdbc.queryForObject(
                "SELECT count(*) FROM xsy_print_template WHERE document_type = 'SORTING_TICKET' "
                        + "AND default_flag = TRUE AND (model -> 'headerFields') @> jsonb_build_array('supplierName')",
                Integer.class);
        assertThat(tickets).as("分拣小票默认模板应包含供应商来源").isEqualTo(1);
    }

    /**
     * V163 起集团主体可显式授权子客户使用集团钱包结算，且只有 GROUP 客户能置真。
     */
    @Test
    void sharedSettlementGrantExistsFromV163() {
        int target = Integer.parseInt(System.getProperty("xsy.migration.expected-version", "137"));
        if (target < 163) {
            return;
        }
        assertColumn("xsy_customer", "shared_settlement_enabled");
        Integer constraint = jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_customer_shared_settlement_scope'",
                Integer.class);
        assertThat(constraint).as("授权只能在 GROUP 结算客户上成立").isEqualTo(1);
    }

    private void assertColumn(String table, String column) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'xsy_v2' AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        assertThat(count).as("%s.%s should exist", table, column).isEqualTo(1);
    }
}
