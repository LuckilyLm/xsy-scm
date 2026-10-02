package com.xsy.scm.print.constant;

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
 * <p>
 * 本轮只接入采购单；发货单与分拣小票的字段目录与数据源在后续补齐，接入方式是加一个枚举值
 * 加一个数据源实现，不改模板、渲染与冻结这三层。
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
                    new ScmPrintField("skuCode", "SKU 编码", false, false),
                    new ScmPrintField("skuName", "规格", false, false),
                    new ScmPrintField("purchaseUnit", "采购单位", false, false),
                    new ScmPrintField("plannedQuantity", "计划数量", false, true),
                    new ScmPrintField("receivedQuantity", "已收数量", false, true),
                    new ScmPrintField("remainingQuantity", "未收数量", false, true),
                    new ScmPrintField("purchasePrice", "采购单价", true, true),
                    new ScmPrintField("lineAmount", "行金额", true, true)),
            List.of(new ScmPrintField("totalAmount", "合计金额", true, true)));

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
