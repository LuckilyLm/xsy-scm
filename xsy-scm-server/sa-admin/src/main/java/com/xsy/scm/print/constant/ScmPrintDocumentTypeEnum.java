package com.xsy.scm.print.constant;

import com.xsy.scm.delivery.permission.DeliveryPermission;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 可配置打印的单据类型，以及每种类型的**字段白名单**。
 *
 * <p>
 * 白名单是「受控模板」的落点：模板只能从表头字段、列和合计里挑 key，服务端不接受的键一律
 * 拒收。这样模板里不存在「任意数据库字段」这种可能，也不需要一套模板表达式求值器。
 *
 * <p>
 * <b>金额与权限</b>：{@link #moneyPermission} 是该单据类型「看金额」所需的权限码，
 * {@code null} 表示该域本来就没有金额门禁、按既有可见性打印（采购单即如此：能看到采购单的人
 * 本来就看得到单价，打印不额外放宽也不额外收紧）。需要门禁的类型在这里声明，渲染阶段据此
 * 剔除金额字段 —— 授权信息不写进模板。
 *
 */
@Getter
@RequiredArgsConstructor
public enum ScmPrintDocumentTypeEnum {

    /**
     * 采购单。默认模型的选列与接入模板前的固定版式一致，因此接入模板不改变既有打印效果。
     */
    PURCHASE_ORDER("采购单", null, List.of(
            new ScmPrintField("orderNo", "采购单号", false, false),
            new ScmPrintField("supplierName", "供应商", false, false),
            new ScmPrintField("warehouseName", "收货仓库", false, false),
            new ScmPrintField("purchaserName", "采购员", false, false),
            new ScmPrintField("plannedArrivalDate", "计划到货", false, false),
            new ScmPrintField("remark", "备注", false, false)), List.of(
                    new ScmPrintField("productName", "商品", false, false),
                    new ScmPrintField("skuCode", "商品规格编码", false, false),
                    new ScmPrintField("skuName", "规格", false, false),
                    new ScmPrintField("purchaseUnit", "采购单位", false, false),
                    new ScmPrintField("plannedQuantity", "计划数量", false, true),
                    new ScmPrintField("receivedQuantity", "已收数量", false, true),
                    new ScmPrintField("remainingQuantity", "未收数量", false, true),
                    new ScmPrintField("purchasePrice", "采购单价", true, true),
                    new ScmPrintField("lineAmount", "行金额", true, true)),
            List.of(new ScmPrintField("totalAmount", "合计金额", true, true))),

    DELIVERY_NOTE("发货单", DeliveryPermission.AMOUNT_QUERY, List.of(
            new ScmPrintField("routeName", "线路", false, false),
            new ScmPrintField("deliveryDate", "配送日期", false, false),
            new ScmPrintField("warehouseName", "仓库", false, false),
            new ScmPrintField("driverName", "司机", false, false),
            new ScmPrintField("driverPhone", "司机电话", false, false),
            new ScmPrintField("vehicleNo", "车牌", false, false),
            new ScmPrintField("remark", "线路备注", false, false)), List.of(
                    new ScmPrintField("stopSeq", "停靠序", false, true),
                    new ScmPrintField("customerName", "客户", false, false),
                    new ScmPrintField("address", "收货地址", false, false),
                    new ScmPrintField("receiverName", "收货人", false, false),
                    new ScmPrintField("receiverPhone", "收货电话", false, false),
                    new ScmPrintField("stopRemark", "停靠备注", false, false),
                    new ScmPrintField("orderNo", "订单号", false, false),
                    new ScmPrintField("expectDeliveryTime", "期望配送", false, false),
                    new ScmPrintField("productName", "商品", false, false),
                    new ScmPrintField("specName", "规格", false, false),
                    new ScmPrintField("saleUnit", "单位", false, false),
                    new ScmPrintField("orderedQuantity", "订购数量", false, true),
                    new ScmPrintField("actualQuantity", "实重 / 实际量", false, true),
                    new ScmPrintField("orderedLineAmount", "订单行金额", true, true),
                    new ScmPrintField("settlementLineAmount", "结算行金额", true, true)),
            List.of(new ScmPrintField("totalAmount", "订单合计金额", true, true))),

    SORTING_TICKET("分拣小票", null, List.of(
            new ScmPrintField("warehouseName", "仓库", false, false),
            new ScmPrintField("assigneeName", "分拣员", false, false)), List.of(
                    new ScmPrintField("orderNo", "订单号", false, false),
                    new ScmPrintField("customerName", "客户", false, false),
                    // 来源：商品 / 赠品。满赠赠品随订单一起拣，但**不挂订单行**，
                    // 因此小票必须自己说清这一行是赠品，否则仓库会按订单量去核。
                    new ScmPrintField("sourceType", "来源", false, false),
                    new ScmPrintField("productName", "商品", false, false),
                    new ScmPrintField("skuCode", "商品规格编码", false, false),
                    new ScmPrintField("specName", "规格", false, false),
                    new ScmPrintField("saleUnit", "单位", false, false),
                    new ScmPrintField("plannedQuantity", "计划量", false, true),
                    new ScmPrintField("sortedQuantity", "实分量", false, true),
                    new ScmPrintField("result", "分拣结果", false, false),
                    new ScmPrintField("reason", "差异原因", false, false)), List.of());

    /**
     * 展示名（模板列表与错误信息用）。
     */
    private final String label;

    /**
     * 该类型看金额所需的权限码；{@code null} 表示无额外门禁。
     */
    private final String moneyPermission;

    /**
     * 表头字段白名单（单头信息）。
     */
    private final List<ScmPrintField> headerFields;

    /**
     * 明细列白名单。
     */
    private final List<ScmPrintField> columns;

    /**
     * 合计字段白名单（整单级）。
     */
    private final List<ScmPrintField> totals;

    /**
     * 是否支持该类型字符串（入参解析用；不接受 null）。
     */
    public static boolean isSupported(String documentType) {
        for (ScmPrintDocumentTypeEnum item : values()) {
            if (item.name().equals(documentType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按 key 找表头字段；不存在返回 {@code null}（调用方按「白名单外」处理）。
     */
    public ScmPrintField headerField(String key) {
        return find(headerFields, key);
    }

    /**
     * 按 key 找明细列；不存在返回 {@code null}。
     */
    public ScmPrintField column(String key) {
        return find(columns, key);
    }

    /**
     * 按 key 找合计字段；不存在返回 {@code null}。
     */
    public ScmPrintField total(String key) {
        return find(totals, key);
    }

    /**
     * 该类型是否要求金额权限。
     */
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
