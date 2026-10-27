package com.xsy.scm.print;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.print.domain.form.ScmPrintBatchForm;
import com.xsy.scm.print.domain.vo.ScmPrintBatchResultVO;
import com.xsy.scm.print.service.ScmPrintBatchService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 20:00 客户分拣单/送货单批量打印。
 *
 * <p>按配送日期取当日已确认订单，逐单独立成功或失败；同一批次键重试按单幂等，不重复生成打印记录。
 */
class ScmPrintBatchIT extends ScmW5PgITBase {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private ScmPrintBatchService scmPrintBatchService;

    private MockedStatic<StpUtil> permissions;

    @BeforeEach
    void grantPermissions() {
        permissions = mockStatic(StpUtil.class);
        permissions.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
        permissions.when(() -> StpUtil.checkPermission(anyString())).thenAnswer(invocation -> null);
    }

    @AfterEach
    void releasePermissions() {
        permissions.close();
    }

    @Test
    @DisplayName("#1 按配送日期批量预览/打印两个客户，同一批次键重试不重复生成记录")
    void batchPrintByDeliveryDate() {
        LocalDate businessDate = LocalDate.now(ZONE).plusDays(1);
        Long firstCustomer = newCustomer();
        Long secondCustomer = newCustomer();
        Long firstOrder = confirmedSalesOrder(firstCustomer, newOnShelfSku("PB1"), "3.0000", "3.0000");
        Long secondOrder = confirmedSalesOrder(secondCustomer, newOnShelfSku("PB2"), "2.0000", "2.0000");
        moveDeliveryDate(firstOrder, businessDate);
        moveDeliveryDate(secondOrder, businessDate);

        ScmPrintBatchForm form = new ScmPrintBatchForm();
        form.setDocumentType("CUSTOMER_SORTING_SHEET");
        form.setBusinessDate(businessDate);
        form.setCustomerIds(List.of(firstCustomer, secondCustomer));

        ScmPrintBatchResultVO preview = scmPrintBatchService.preview(form);
        assertThat(preview.getTotal()).isEqualTo(2);
        assertThat(preview.getSucceeded()).isEqualTo(2);
        assertThat(preview.getFailed()).isZero();
        assertThat(preview.getRenders()).extracting(render -> render.getDocumentType())
            .containsOnly("CUSTOMER_SORTING_SHEET");
        assertThat(preview.getItems()).extracting(item -> item.getOrderId())
            .containsExactlyInAnyOrder(firstOrder, secondOrder);

        form.setBatchKey(prefix + ":batch");
        ScmPrintBatchResultVO printed = scmPrintBatchService.print(form);
        assertThat(printed.getSucceeded()).isEqualTo(2);
        assertThat(printed.getItems()).extracting(item -> item.getRecordId()).doesNotContainNull();
        int records = printRecords(firstOrder) + printRecords(secondOrder);
        assertThat(records).isEqualTo(2);

        // 同一次点击的重试沿用同一个批量键，逐单命中幂等重放
        scmPrintBatchService.print(form);
        assertThat(printRecords(firstOrder) + printRecords(secondOrder)).isEqualTo(records);
    }

    @Test
    @DisplayName("#2 一张失败不影响其他单；不支持的单据类型整批拒绝（41313）")
    void oneFailureDoesNotRollBackTheBatch() {
        Long customerId = newCustomer();
        Long orderId = confirmedSalesOrder(customerId, newOnShelfSku("PB3"), "1.0000", "1.0000");

        ScmPrintBatchForm form = new ScmPrintBatchForm();
        form.setDocumentType("CUSTOMER_DELIVERY_NOTE");
        form.setBusinessDate(LocalDate.now(ZONE));
        form.setOrderIds(List.of(orderId, 987_654_321L));

        ScmPrintBatchResultVO result = scmPrintBatchService.preview(form);
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getSucceeded()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getItems()).filteredOn(item -> !item.isSuccess())
            .singleElement().satisfies(item -> {
                assertThat(item.getOrderId()).isEqualTo(987_654_321L);
                assertThat(item.getMessage()).isNotBlank();
            });

        ScmPrintBatchForm unsupported = new ScmPrintBatchForm();
        unsupported.setDocumentType("PURCHASE_ORDER");
        unsupported.setBusinessDate(LocalDate.now(ZONE));
        expectCode(() -> scmPrintBatchService.preview(unsupported), 41313);
    }

    /** 批量取数按 expect_delivery_time 的北京时间自然日；夹具确认后回写目标配送时间。 */
    private void moveDeliveryDate(Long orderId, LocalDate businessDate) {
        OffsetDateTime deliveryAt = businessDate.atTime(10, 0).atOffset(ZoneOffset.ofHours(8));
        assertThat(jdbc.update("UPDATE xsy_sales_order SET expect_delivery_time = ? WHERE id = ?",
            deliveryAt, orderId)).isEqualTo(1);
    }

    private int printRecords(Long orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM xsy_print_record WHERE business_id = ? "
            + "AND document_type IN ('CUSTOMER_SORTING_SHEET', 'CUSTOMER_DELIVERY_NOTE')",
            Integer.class, orderId);
    }
}
