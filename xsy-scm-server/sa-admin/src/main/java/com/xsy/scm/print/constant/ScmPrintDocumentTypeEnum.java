package com.xsy.scm.print.constant;

import com.xsy.scm.delivery.permission.DeliveryPermission;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 每种打印单据只能使用允许的字段。金额列按业务查看权限剔除，模板本身不能放宽权限。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPrintDocumentTypeEnum {

    /** 销售单使用正式订单详情；订单查询权限本身允许查看订单金额。 */
    SALES_ORDER("销售订单", null, List.of(new ScmPrintField("orderNo", "销售订单号", false, false),
        new ScmPrintField("customerName", "客户", false, false), new ScmPrintField("customerCode", "客户编码", false, false),
        new ScmPrintField("orderStatus", "订单状态", false, false), new ScmPrintField("orderSource", "订单来源", false, false),
        new ScmPrintField("expectDeliveryTime", "期望配送时间", false, false),
        new ScmPrintField("receiverName", "收货人", false, false),
        new ScmPrintField("receiverPhone", "收货电话", false, false), new ScmPrintField("address", "收货地址", false, false),
        new ScmPrintField("remark", "备注", false, false)),
        List.of(new ScmPrintField("productName", "商品", false, false),
            new ScmPrintField("skuCode", "商品规格编码", false, false), new ScmPrintField("specName", "规格", false, false),
            new ScmPrintField("saleUnit", "销售单位", false, false),
            new ScmPrintField("orderedQuantity", "订购数量", false, true),
            new ScmPrintField("actualQuantity", "实数量", false, true),
            new ScmPrintField("draftUnitPrice", "草稿单价", true, true),
            new ScmPrintField("lockedUnitPrice", "锁定单价", true, true),
            new ScmPrintField("orderedLineAmount", "下单行金额", true, true),
            new ScmPrintField("settlementLineAmount", "结算行金额", true, true)),
        List.of(new ScmPrintField("orderedTotalAmount", "下单总金额", true, true),
            new ScmPrintField("settlementTotalAmount", "结算总金额", true, true))),

    PURCHASE_ORDER("采购单", null, List.of(new ScmPrintField("orderNo", "采购单号", false, false),
        new ScmPrintField("supplierName", "供应商", false, false),
        new ScmPrintField("warehouseName", "收货仓库", false, false),
        new ScmPrintField("purchaserName", "采购员", false, false),
        new ScmPrintField("plannedArrivalDate", "计划到货", false, false), new ScmPrintField("remark", "备注", false, false)),
        List.of(new ScmPrintField("productName", "商品", false, false),
            new ScmPrintField("skuCode", "商品规格编码", false, false), new ScmPrintField("skuName", "规格", false, false),
            new ScmPrintField("purchaseUnit", "采购单位", false, false),
            new ScmPrintField("plannedQuantity", "计划数量", false, true),
            new ScmPrintField("receivedQuantity", "已收数量", false, true),
            new ScmPrintField("remainingQuantity", "未收数量", false, true),
            new ScmPrintField("purchasePrice", "采购单价", true, true), new ScmPrintField("lineAmount", "行金额", true, true)),
        List.of(new ScmPrintField("totalAmount", "合计金额", true, true))),

    DELIVERY_NOTE("发货单", DeliveryPermission.AMOUNT_QUERY, List.of(new ScmPrintField("routeName", "线路", false, false),
        new ScmPrintField("deliveryDate", "配送日期", false, false), new ScmPrintField("warehouseName", "仓库", false, false),
        new ScmPrintField("driverName", "司机", false, false), new ScmPrintField("driverPhone", "司机电话", false, false),
        new ScmPrintField("vehicleNo", "车牌", false, false), new ScmPrintField("remark", "线路备注", false, false)),
        List.of(new ScmPrintField("stopSeq", "停靠序", false, true), new ScmPrintField("customerName", "客户", false, false),
            new ScmPrintField("address", "收货地址", false, false), new ScmPrintField("receiverName", "收货人", false, false),
            new ScmPrintField("receiverPhone", "收货电话", false, false),
            new ScmPrintField("stopRemark", "停靠备注", false, false), new ScmPrintField("orderNo", "订单号", false, false),
            new ScmPrintField("expectDeliveryTime", "期望配送", false, false),
            new ScmPrintField("productName", "商品", false, false), new ScmPrintField("specName", "规格", false, false),
            new ScmPrintField("saleUnit", "单位", false, false),
            new ScmPrintField("orderedQuantity", "订购数量", false, true),
            new ScmPrintField("actualQuantity", "实重 / 实际量", false, true),
            new ScmPrintField("orderedLineAmount", "订单行金额", true, true),
            new ScmPrintField("settlementLineAmount", "结算行金额", true, true)),
        List.of(new ScmPrintField("totalAmount", "订单合计金额", true, true))),

    SORTING_TICKET("分拣小票", null,
        List.of(new ScmPrintField("warehouseName", "仓库", false, false),
            new ScmPrintField("assigneeName", "分拣员", false, false),
            // 供应商来源：货由供应商代分拣时，小票要能直接交给对方，不必再口头交代
            new ScmPrintField("supplierName", "供应商来源", false, false)),
        List.of(new ScmPrintField("orderNo", "订单号", false, false),
            new ScmPrintField("customerName", "客户", false, false),
            // 来源：商品 / 赠品。赠品随订单一起拣但不挂订单行，小票要自己标清楚，否则仓库会按订单量核。
            new ScmPrintField("sourceType", "来源", false, false), new ScmPrintField("productName", "商品", false, false),
            new ScmPrintField("skuCode", "商品规格编码", false, false), new ScmPrintField("specName", "规格", false, false),
            new ScmPrintField("saleUnit", "单位", false, false), new ScmPrintField("plannedQuantity", "计划量", false, true),
            new ScmPrintField("sortedQuantity", "实分量", false, true), new ScmPrintField("result", "分拣结果", false, false),
            new ScmPrintField("reason", "差异原因", false, false)),
        List.of()),

    TRACE_UNIT("包装码标签", null,
        List.of(new ScmPrintField("unitCode", "包装码", false, false), new ScmPrintField("lotNo", "批次号", false, false),
            new ScmPrintField("sourceLotNo", "来源生产批号", false, false),
            new ScmPrintField("productName", "商品", false, false), new ScmPrintField("skuName", "规格", false, false),
            new ScmPrintField("packageType", "包装类型", false, false), new ScmPrintField("quantity", "数量", false, true),
            new ScmPrintField("unit", "单位", false, false), new ScmPrintField("customerName", "客户", false, false),
            new ScmPrintField("orderNo", "订单号", false, false),
            // 只用于生成二维码，不在标签上印 URL 文本
            new ScmPrintField("traceUrl", "溯源二维码", false, false)),
        List.of(), List.of());

    /** 展示名（模板列表与错误信息用）。 */
    private final String label;

    /** 查看金额所需的权限码；{@code null} 表示无额外限制。 */
    private final String moneyPermission;

    /** 表头允许字段（单据头信息）。 */
    private final List<ScmPrintField> headerFields;

    /** 明细允许字段。 */
    private final List<ScmPrintField> columns;

    /** 合计允许字段（整单级）。 */
    private final List<ScmPrintField> totals;

    /** 是否支持该类型字符串（入参解析用，不接受 null）。 */
    public static boolean isSupported(String documentType) {
        for (ScmPrintDocumentTypeEnum item : values()) {
            if (item.name().equals(documentType)) {
                return true;
            }
        }
        return false;
    }

    /** 按 key 找表头字段；不存在返回 {@code null}，调用方按「字段不支持」处理。 */
    public ScmPrintField headerField(String key) {
        return find(headerFields, key);
    }

    /** 按 key 找明细列；不存在返回 {@code null}。 */
    public ScmPrintField column(String key) {
        return find(columns, key);
    }

    /** 按 key 找合计字段；不存在返回 {@code null}。 */
    public ScmPrintField total(String key) {
        return find(totals, key);
    }

    /** 该类型是否要求金额权限。 */
    public boolean requiresMoneyPermission() {
        return moneyPermission != null;
    }

    private static ScmPrintField find(List<ScmPrintField> fields, String key) {
        for (ScmPrintField field : fields) {
            if (field.key().equals(key)) {
                return field;
            }
        }
        return null;
    }
}
