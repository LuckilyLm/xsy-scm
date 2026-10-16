package com.xsy.scm.mall.catalog.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.catalog.dao.MallStockDao;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.domain.vo.MallStockFact;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 当前配送仓可售量只扣余额中的 reserved_quantity；没有独立冻结列，不能重复扣减同一占用。 */
@Service
@RequiredArgsConstructor
public class MallStockAvailabilityService {
    private final MallStockDao stockDao;

    public void enrich(Long warehouseId, List<MallProductVO> products) {
        if (products.isEmpty() || warehouseId == null)
            return;
        Map<Long,
                MallStockFact> facts = stockDao
                        .bySkus(warehouseId, products.stream().map(MallProductVO::getSkuId).distinct().toList())
                        .stream().collect(Collectors.toMap(MallStockFact::getSkuId, Function.identity()));
        for (MallProductVO product : products) {
            product.setWarehouseId(warehouseId);
            MallStockFact fact = facts.get(product.getSkuId());
            if (fact == null) {
                product.setAvailableQty(BigDecimal.ZERO);
                product.setStockStatus("OUT_OF_STOCK");
                continue;
            }
            BigDecimal factor = factor(product.getSaleUnit(), fact);
            if (factor == null) {
                product.setStockStatus("UNKNOWN");
                continue;
            }
            BigDecimal quantity = fact.getAvailableStockQuantity().divide(factor, 4, RoundingMode.DOWN);
            product.setAvailableQty(quantity);
            BigDecimal minimum = product.getMinOrderQty() == null ? BigDecimal.ONE : product.getMinOrderQty();
            product.setStockStatus(quantity.signum() == 0
                    ? "OUT_OF_STOCK"
                    : quantity.compareTo(minimum) < 0 ? "INSUFFICIENT" : "AVAILABLE");
        }
    }

    public Conversion requireConversion(Long warehouseId, Long skuId, String saleUnit) {
        List<MallStockFact> facts = stockDao.bySkus(warehouseId, List.of(skuId));
        if (facts.isEmpty()) {
            // 新订单允许先接单再采购；无库存余额时只能选唯一显式配置，或按同单位冻结。
            List<MallStockFact> configured = stockDao.configuredConversions(skuId, saleUnit);
            if (configured.size() > 1)
                throw new ScmBusinessException(MallErrorCode.CHECKOUT_ITEM_UNAVAILABLE);
            if (configured.isEmpty())
                return new Conversion(saleUnit, BigDecimal.ONE);
            MallStockFact config = configured.getFirst();
            return new Conversion(config.getStockUnit(), config.getStockUnitsPerSaleUnit());
        }
        MallStockFact fact = facts.getFirst();
        BigDecimal factor = factor(saleUnit, fact);
        if (factor == null || factor.signum() <= 0)
            throw new ScmBusinessException(MallErrorCode.CHECKOUT_ITEM_UNAVAILABLE);
        return new Conversion(fact.getStockUnit(), factor);
    }

    private static BigDecimal factor(String saleUnit, MallStockFact fact) {
        if (saleUnit == null || fact.getStockUnit() == null || !saleUnit.equals(fact.getSaleUnit()))
            return null;
        if (saleUnit.equals(fact.getStockUnit()))
            return BigDecimal.ONE;
        return fact.getStockUnitsPerSaleUnit();
    }

    public record Conversion(String stockUnit, BigDecimal stockUnitsPerSaleUnit) {
    }
}
