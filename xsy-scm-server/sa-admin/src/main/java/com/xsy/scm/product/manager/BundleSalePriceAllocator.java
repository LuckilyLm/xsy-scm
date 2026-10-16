package com.xsy.scm.product.manager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 按冻结的组成规格市场价金额占比拆分组合商品独立销售总价。
 *
 * <p>这里只做销售金额分摊，不产生独立收费行，也不替代实际库存成本归集。
 * 上游必须冻结组成 SKU、销售单位用量、市场参考单价及分摊结果。
 */
public final class BundleSalePriceAllocator {

    private static final int SCALE = 4;
    private static final BigDecimal MAX = new BigDecimal("99999999999999.9999");

    private BundleSalePriceAllocator() {
    }

    public record Component(Long skuId, BigDecimal saleQuantity, BigDecimal marketUnitPrice) {
    }

    public record Share(Long skuId, BigDecimal referenceAmount, BigDecimal allocatedSaleAmount) {
    }

    /**
     * @param bundleLineAmount 组合订单行总含税金额；不应传套餐单价
     * @param components 冻结后的组成行，须为稳定业务顺序；数量应包含所购组合套数
     */
    public static List<Share> allocate(BigDecimal bundleLineAmount, List<Component> components) {
        if (bundleLineAmount == null || bundleLineAmount.signum() < 0
                || bundleLineAmount.scale() > SCALE || bundleLineAmount.compareTo(MAX) > 0
                || components == null || components.isEmpty()) {
            throw new IllegalArgumentException("Invalid bundle line amount or components");
        }
        BigDecimal referenceTotal = BigDecimal.ZERO;
        List<BigDecimal> references = new ArrayList<>(components.size());
        Set<Long> skuIds = new HashSet<>();
        for (Component component : components) {
            if (component == null || component.skuId() == null
                    || !skuIds.add(component.skuId())
                    || component.saleQuantity() == null || component.saleQuantity().signum() <= 0
                    || component.marketUnitPrice() == null || component.marketUnitPrice().signum() <= 0) {
                throw new IllegalArgumentException("Bundle component has no valid market amount");
            }
            BigDecimal referenceAmount = component.saleQuantity().multiply(component.marketUnitPrice());
            references.add(referenceAmount);
            referenceTotal = referenceTotal.add(referenceAmount);
        }
        List<Share> shares = new ArrayList<>(components.size());
        BigDecimal remaining = bundleLineAmount.setScale(SCALE, RoundingMode.UNNECESSARY);
        for (int index = 0; index < components.size(); index++) {
            BigDecimal allocated;
            if (index == components.size() - 1) {
                // 最后一条收口尾差，保证组成行金额严格等于独立组合总价。
                allocated = remaining;
            } else {
                allocated = bundleLineAmount.multiply(references.get(index))
                        .divide(referenceTotal, SCALE, RoundingMode.HALF_UP);
                // 组合售价低到只有一个最小金额单位时，仍不能给最后一行分出负价。
                allocated = allocated.min(remaining);
            }
            shares.add(new Share(components.get(index).skuId(), references.get(index), allocated));
            remaining = remaining.subtract(allocated);
        }
        return List.copyOf(shares);
    }
}
