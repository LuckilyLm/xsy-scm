package com.xsy.scm.common;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.assertj.core.api.Assertions.assertThat;

/** 事实表的自定义SQL也只能追加；避免新增映射方法绕过标准Mapper限制。 */
class AppendOnlySqlContractTest {
    private static final Set<String> FACTS = Set.of(
        "xsy_inventory_movement", "xsy_inventory_lot_movement", "xsy_price_operation_log",
        "xsy_customer_balance_movement", "xsy_customer_promotion_scan", "xsy_order_bundle_component",
        "xsy_purchase_demand_calculation_batch_item", "xsy_purchase_inquiry_quote",
        "xsy_purchase_share_feedback", "xsy_purchase_account", "xsy_purchase_account_item",
        "xsy_purchase_cost_adjustment", "xsy_finance_purchase_cost_entry", "xsy_finance_customer_expense",
        "xsy_finance_customer_expense_void", "xsy_finance_receivable", "xsy_finance_receivable_item",
        "xsy_finance_payable", "xsy_finance_payable_item", "xsy_finance_receipt", "xsy_finance_payment",
        "xsy_finance_write_off", "xsy_finance_waiver", "xsy_finance_operation_log",
        "xsy_payment_refund_recovery_log", "xsy_mall_home_release", "xsy_trace_event",
        "xsy_trace_unit_binding", "xsy_trace_unit_unbind", "xsy_print_record",
        "xsy_report_negative_stock_snapshot");
    private static final Map<String, Set<String>> SNAPSHOTS = Map.of(
        "xsy_order_discount", Set.of("sales_order_id", "discount_amount", "special_discount_amount", "allocations", "activity_snapshot", "coupon_snapshot"),
        "xsy_order_promotion_gift", Set.of("sales_order_id", "activity_id", "activity_version", "sku_id", "quantity", "rule_snapshot"),
        "xsy_delivery_plan_proposal", Set.of("route_id", "input_snapshot", "result_snapshot", "provider_code", "provider_version", "estimated_flag", "rule_code", "stop_count", "total_distance", "created_at", "created_by"));

    @Test
    void customStatementsDoNotMutateFactsOrFrozenFields() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/scm/**/*.xml");
        assertThat(resources).isNotEmpty();
        Pattern mutation = Pattern.compile("(?is)\\b(?:update|delete\\s+from)\\s+(?:xsy_v2\\.)?(xsy_[a-z_]+)\\b");
        for (var resource : resources) {
            try (var input = resource.getInputStream()) {
                var document = factory.newDocumentBuilder().parse(input);
                var nodes = document.getDocumentElement().getChildNodes();
                for (int i = 0; i < nodes.getLength(); i++) {
                    var node = nodes.item(i);
                    if (!Set.of("insert", "update", "delete").contains(node.getNodeName())) {
                        continue;
                    }
                    String sql = node.getTextContent().toLowerCase(java.util.Locale.ROOT);
                    var matches = mutation.matcher(sql);
                    while (matches.find()) {
                        String table = matches.group(1);
                        assertThat(FACTS).as("%s 不得覆盖或删除事实表 %s", resource.getFilename(), table).doesNotContain(table);
                        if (SNAPSHOTS.containsKey(table) && sql.contains("set")) {
                            String assignments = sql.substring(sql.indexOf("set") + 3).split("\\bwhere\\b", 2)[0];
                            for (String field : SNAPSHOTS.get(table)) {
                                assertThat(Pattern.compile("\\b" + field + "\\s*=").matcher(assignments).find())
                                    .as("%s 不得重写冻结字段 %s.%s", resource.getFilename(), table, field).isFalse();
                            }
                        }
                    }
                }
            }
        }
    }
}
