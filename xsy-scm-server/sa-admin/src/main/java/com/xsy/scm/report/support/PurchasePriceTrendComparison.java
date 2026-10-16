package com.xsy.scm.report.support;

import com.xsy.scm.report.domain.vo.PurchaseReportVO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购价格波动仅比较筛选窗口内同 SKU、同采购单位的相邻有效业务日。缺失数据不等同零；采购承诺价也不等同实际入库成本。
 */
public final class PurchasePriceTrendComparison {

    private PurchasePriceTrendComparison() {
    }

    private record SeriesKey(Long skuId, String purchaseUnit) {
    }

    public static List<PurchaseReportVO.PriceTrendPoint> populate(List<PurchaseReportVO.PriceTrendPoint> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        Map<SeriesKey, List<PurchaseReportVO.PriceTrendPoint>> groups = new HashMap<>();
        for (PurchaseReportVO.PriceTrendPoint point : points) {
            groups.computeIfAbsent(new SeriesKey(point.getSkuId(), point.getPurchaseUnit()), key -> new ArrayList<>())
                    .add(point);
        }
        for (List<PurchaseReportVO.PriceTrendPoint> group : groups.values()) {
            group.sort(Comparator.comparing(PurchaseReportVO.PriceTrendPoint::getBizDate));
            BigDecimal previous = null;
            for (PurchaseReportVO.PriceTrendPoint point : group) {
                BigDecimal current = point.getWeightedAvgPrice();
                point.setPreviousWeightedAvgPrice(null);
                point.setPriceChangeAmount(null);
                point.setPriceChangePercent(null);
                if (current == null) {
                    continue;
                }
                if (previous != null) {
                    BigDecimal difference = current.subtract(previous).setScale(4, RoundingMode.HALF_UP);
                    point.setPreviousWeightedAvgPrice(previous);
                    point.setPriceChangeAmount(difference);
                    if (previous.signum() != 0) {
                        point.setPriceChangePercent(
                                difference.multiply(BigDecimal.valueOf(100)).divide(previous, 4, RoundingMode.HALF_UP));
                    }
                }
                previous = current;
            }
        }
        return points;
    }
}
