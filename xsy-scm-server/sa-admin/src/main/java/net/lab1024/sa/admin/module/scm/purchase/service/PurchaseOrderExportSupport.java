package net.lab1024.sa.admin.module.scm.purchase.service;

import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * 采购单导出的列目录（Wave 2B §6.4 / §6.5，只读）。
 *
 * <p>列的<b>唯一事实源在这里</b>：前端「导出设置」只回传它勾选的列 key，本目录按固定顺序过滤并落表头，
 * 未知 key 直接忽略、勾选为空则导出整目录 —— 前端拿不到后端不认识的列，也不会因前端拼字段而改变导出内容。
 * 导出不触碰任何采购状态，是纯粹的读取投影。
 */
public final class PurchaseOrderExportSupport {

    private PurchaseOrderExportSupport() {
    }

    public record Column(String key, String title, Function<PurchaseOrderVO, Object> value) {
    }

    private static final List<Column> CATALOG = List.of(
            new Column("orderNo", "采购单号", PurchaseOrderVO::getOrderNo),
            new Column("supplierName", "供应商", PurchaseOrderVO::getSupplierName),
            new Column("supplierCode", "供应商编码", PurchaseOrderVO::getSupplierCode),
            new Column("purchaserName", "采购员", PurchaseOrderVO::getPurchaserName),
            new Column("warehouseName", "收货仓库", PurchaseOrderVO::getWarehouseName),
            new Column("warehouseCode", "仓库编码", PurchaseOrderVO::getWarehouseCode),
            new Column("plannedArrivalDate", "计划到货日期", PurchaseOrderVO::getPlannedArrivalDate),
            new Column("status", "状态", PurchaseOrderVO::getStatus),
            new Column("totalAmount", "采购金额", PurchaseOrderVO::getTotalAmount),
            new Column("receivedProgress", "收货进度", PurchaseOrderVO::getReceivedProgress),
            new Column("remark", "备注", PurchaseOrderVO::getRemark),
            new Column("cancelReason", "取消原因", PurchaseOrderVO::getCancelReason),
            new Column("shortCloseReason", "少收关单原因", PurchaseOrderVO::getShortCloseReason),
            new Column("submittedAt", "提交时间", PurchaseOrderVO::getSubmittedAt),
            new Column("createdAt", "创建时间", PurchaseOrderVO::getCreatedAt)
    );

    /** 勾选为空 / 全部未知时导出整目录；否则按目录固定顺序保留命中的 key。 */
    private static List<Column> resolve(List<String> selected) {
        if (selected == null || selected.isEmpty()) {
            return CATALOG;
        }
        LinkedHashSet<String> wanted = new LinkedHashSet<>(selected);
        List<Column> picked = new ArrayList<>();
        for (Column column : CATALOG) {
            if (wanted.contains(column.key())) {
                picked.add(column);
            }
        }
        return picked.isEmpty() ? CATALOG : picked;
    }

    public static List<List<String>> head(List<String> selected) {
        List<List<String>> head = new ArrayList<>();
        for (Column column : resolve(selected)) {
            head.add(List.of(column.title()));
        }
        return head;
    }

    public static List<List<Object>> rows(List<String> selected, List<PurchaseOrderVO> orders) {
        List<Column> columns = resolve(selected);
        List<List<Object>> data = new ArrayList<>(orders.size());
        for (PurchaseOrderVO order : orders) {
            List<Object> line = new ArrayList<>(columns.size());
            for (Column column : columns) {
                line.add(text(column.value().apply(order)));
            }
            data.add(line);
        }
        return data;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
