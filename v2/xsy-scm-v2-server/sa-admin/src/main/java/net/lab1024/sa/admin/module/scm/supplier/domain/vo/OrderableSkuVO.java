package net.lab1024.sa.admin.module.scm.supplier.domain.vo;

import lombok.Data;

import java.util.Map;

/**
 * 「可下单 SKU」投影：用于构造 {@code supplier_sku} 的快照。
 *
 * <p>名称取 SPU 的名称（{@code productName}）而不是 SKU 的 {@code specName}
 * （legacy 不变量 R4）；{@code specValues} 为 {@code null} 时按 {@code {}} 落库。
 *
 * <p><b>为什么不复用 W1 的 ProductSkuDao：</b>Target Design 曾假定 W1 已交付
 * {@code ProductSkuDao.selectOrderableByIds}，但实测 W1 只交付了 {@code clearDefault}。
 * 用户要求「不修改 {@code module/scm/product/**}」，因此本查询由 supplier 域自己持有——
 * 这也符合「W2 只读 product_sku / product_spu」的边界约定。
 */
@Data
public class OrderableSkuVO {

    private Long skuId;

    private String skuCode;

    private String productName;

    private Map<String, String> specValues;
}
