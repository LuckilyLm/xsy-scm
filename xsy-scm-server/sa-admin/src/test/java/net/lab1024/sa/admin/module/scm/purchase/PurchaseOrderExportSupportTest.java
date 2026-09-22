package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseOrderExportSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购单导出列目录（Wave 2B §6.5）的纯单元测试。
 *
 * <p>导出的正确性完全落在「列目录」这层纯函数上：前端勾选只回传 key，后端按固定顺序过滤、
 * 未知 key 忽略、空勾选回退整目录，null 单元格落成空串。这些都是无需数据库即可断言的口径，
 * 因此用普通单测覆盖，不落 IT。
 */
@DisplayName("采购单导出列目录：顺序 / 过滤 / 空值（纯单测）")
class PurchaseOrderExportSupportTest {

    private static String title(List<String> cell) {
        return cell.get(0);
    }

    private static PurchaseOrderVO order(String orderNo, String status) {
        PurchaseOrderVO vo = new PurchaseOrderVO();
        vo.setOrderNo(orderNo);
        vo.setStatus(status);
        return vo;
    }

    @Test
    @DisplayName("勾选为空 → 导出整目录，表头顺序与目录一致")
    void emptySelectionExportsFullCatalog() {
        List<String> head = PurchaseOrderExportSupport.head(List.of()).stream()
                .map(PurchaseOrderExportSupportTest::title).toList();
        assertThat(head).containsExactly(
                "采购单号", "供应商", "供应商编码", "采购员", "收货仓库", "仓库编码",
                "计划到货日期", "状态", "采购金额", "收货进度", "备注",
                "取消原因", "少收关单原因", "提交时间", "创建时间");
        assertThat(PurchaseOrderExportSupport.head(null)).hasSize(head.size());
    }

    @Test
    @DisplayName("勾选子集 → 按目录固定顺序保留命中列，而非前端传入顺序")
    void subsetKeepsCatalogOrder() {
        List<String> head = PurchaseOrderExportSupport.head(List.of("status", "orderNo")).stream()
                .map(PurchaseOrderExportSupportTest::title).toList();
        // 前端先传 status 后传 orderNo，导出仍按目录顺序（采购单号在前）
        assertThat(head).containsExactly("采购单号", "状态");
    }

    @Test
    @DisplayName("未知 key 直接忽略；勾选全部未知时回退整目录，绝不产出空表")
    void unknownKeysIgnoredAndFallbackToFullCatalog() {
        List<String> subset = PurchaseOrderExportSupport.head(List.of("orderNo", "notAColumn")).stream()
                .map(PurchaseOrderExportSupportTest::title).toList();
        assertThat(subset).containsExactly("采购单号");

        assertThat(PurchaseOrderExportSupport.head(List.of("ghost")))
                .hasSameSizeAs(PurchaseOrderExportSupport.head(List.of()));
    }

    @Test
    @DisplayName("行数据：null 单元格落空串，且每行单元格数与表头严格相等")
    void rowsAlignWithHeadAndNullBecomesEmpty() {
        // 目录顺序：orderNo → supplierName → status（前端勾选顺序不改变列顺序）
        List<List<Object>> rows = PurchaseOrderExportSupport.rows(
                List.of("orderNo", "status", "supplierName"),
                List.of(order("PO-1", "SUBMITTED"), order(null, "RECEIVED")));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("PO-1", "", "SUBMITTED");
        // orderNo 为 null → 空串，不是字符串 "null"；supplierName 未赋值同样落空串
        assertThat(rows.get(1)).containsExactly("", "", "RECEIVED");
        assertThat(rows.getFirst()).hasSameSizeAs(PurchaseOrderExportSupport.head(
                List.of("orderNo", "status", "supplierName")));
    }
}
