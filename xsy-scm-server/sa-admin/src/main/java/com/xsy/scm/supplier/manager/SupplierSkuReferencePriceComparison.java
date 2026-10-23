package com.xsy.scm.supplier.manager;

import com.xsy.scm.supplier.domain.vo.SupplierSkuComparisonVO;
import com.xsy.scm.supplier.domain.vo.SupplierSkuVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 比较同一个 SKU 的供应商参考价；不能把“箱”和“公斤”价格直接相比。 缺失参考价不按零计；仅有一个可报价供应商时不产生没有意义的溢价。
 */
public final class SupplierSkuReferencePriceComparison {

    private SupplierSkuReferencePriceComparison() {
    }

    public static List<SupplierSkuComparisonVO> compare(List<SupplierSkuVO> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        Map<String, List<SupplierSkuVO>> byUnit = new HashMap<>();
        for (SupplierSkuVO source : sources) {
            if (source.getPurchaseUnit() != null && !source.getPurchaseUnit().isBlank()) {
                byUnit.computeIfAbsent(source.getPurchaseUnit(), ignored -> new ArrayList<>()).add(source);
            }
        }
        List<SupplierSkuComparisonVO> compared = new ArrayList<>(sources.size());
        for (SupplierSkuVO source : sources) {
            List<SupplierSkuVO> peers = byUnit.getOrDefault(source.getPurchaseUnit(), List.of());
            List<BigDecimal> quotes = peers.stream().map(SupplierSkuVO::getReferencePrice)
                .filter(price -> price != null && price.signum() >= 0).toList();
            BigDecimal lowest = quotes.stream().min(BigDecimal::compareTo).orElse(null);
            SupplierSkuComparisonVO row = new SupplierSkuComparisonVO();
            row.setSupplierId(source.getSupplierId());
            row.setSupplierCode(source.getSupplierCodeSnapshot());
            row.setSupplierName(source.getSupplierNameSnapshot());
            row.setSkuId(source.getSkuId());
            row.setPurchaseUnit(source.getPurchaseUnit());
            row.setDefaultFlag(source.getDefaultFlag());
            row.setReferencePrice(source.getReferencePrice());
            row.setLowestReferencePrice(lowest);
            row.setPricedSupplierCount(quotes.size());
            if (quotes.size() >= 2 && source.getReferencePrice() != null && lowest != null
                && source.getReferencePrice().signum() >= 0) {
                BigDecimal gap = source.getReferencePrice().subtract(lowest).setScale(4, RoundingMode.HALF_UP);
                row.setPriceGapAmount(gap);
                if (lowest.signum() > 0) {
                    row.setPriceGapPercent(
                        gap.multiply(BigDecimal.valueOf(100)).divide(lowest, 4, RoundingMode.HALF_UP));
                }
            }
            compared.add(row);
        }
        compared.sort(Comparator
            .comparing(SupplierSkuComparisonVO::getPurchaseUnit, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SupplierSkuComparisonVO::getReferencePrice, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SupplierSkuComparisonVO::getSupplierId, Comparator.nullsLast(Comparator.naturalOrder())));
        return compared;
    }
}
