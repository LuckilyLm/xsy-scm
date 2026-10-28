package com.xsy.scm.order.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.order.constant.ScmOrderReturnPriceSourceEnum;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.vo.OrderReturnPricePreviewVO;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import com.xsy.scm.pricing.service.PriceResolver;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_INDEPENDENT_PRICE_REQUIRED;
import static com.xsy.scm.order.constant.OrderErrorCode.ORDER_RETURN_INDEPENDENT_UNIT_INVALID;

/**
 * 独立退货取价：先取该客户该商品该单位最近一次成交价，再回退到订单同一套取价链； 两者都没有价格时交给人工填价并在建单时校验原因。
 */
@Service
@RequiredArgsConstructor
public class OrderReturnPriceService {

    /**
     * 只取最近 20 张包含该 SKU 的订单找同单位成交价：更早的价格对现场没有参考意义，也避免为一次退货扫全表。
     */
    private static final int RECENT_ORDER_LIMIT = 20;

    private final SalesOrderItemDao salesOrderItemDao;
    private final ProductSkuOptionDao productSkuOptionDao;
    private final PriceResolver priceResolver;

    public record ReturnPrice(BigDecimal unitPrice, String source) {
    }

    /**
     * 批量读取商品档案，退货明细的商品名称、销售单位快照都以此为准。
     */
    public Map<Long, ProductSkuOptionVO> skus(List<Long> skuIds) {
        return productSkuOptionDao.selectByIds(skuIds).stream()
            .collect(Collectors.toMap(ProductSkuOptionVO::getSkuId, Function.identity()));
    }

    /**
     * 校验退货单位：缺省取商品销售单位，显式提交时必须一致，不做跨单位换算。
     */
    public String saleUnit(ProductSkuOptionVO sku, String requestedUnit) {
        if (requestedUnit == null || requestedUnit.isBlank()) {
            return sku.getSaleUnit();
        }
        if (!requestedUnit.trim().equals(sku.getSaleUnit())) {
            throw new ScmBusinessException(ORDER_RETURN_INDEPENDENT_UNIT_INVALID);
        }
        return sku.getSaleUnit();
    }

    /**
     * 建单前的取价预览：每个 SKU 给出销售单位、建议单价与来源；取不到价格时 priced=false。
     */
    public List<OrderReturnPricePreviewVO> preview(Long customerId, List<Long> skuIds) {
        var skus = skus(skuIds);
        return skuIds.stream().map(skuId -> {
            var sku = skus.get(skuId);
            var preview = new OrderReturnPricePreviewVO();
            preview.setSkuId(skuId);
            if (sku == null) {
                return preview;
            }
            preview.setProductName(sku.getProductName());
            preview.setSpecName(sku.getSpecName());
            preview.setSaleUnit(sku.getSaleUnit());
            ReturnPrice suggested = suggest(customerId, skuId, sku.getSaleUnit());
            if (suggested != null) {
                preview.setUnitPrice(suggested.unitPrice());
                preview.setPriceSource(suggested.source());
                preview.setPriced(true);
            }
            return preview;
        }).toList();
    }

    /**
     * 无人工价格时的取价：最近成交价优先，其次定价链；都取不到返回 null，由调用方要求人工填价。
     */
    public ReturnPrice suggest(Long customerId, Long skuId, String saleUnit) {
        ReturnPrice recent = recentTransaction(customerId, skuId, saleUnit);
        return recent != null ? recent : chain(customerId, skuId, saleUnit);
    }

    /**
     * 要求有可用价格：没有人工填价且取价链也给不出价格时失败，绝不按 0 元建退货行。
     */
    public ReturnPrice require(Long customerId, Long skuId, String saleUnit, BigDecimal manualPrice) {
        if (manualPrice != null) {
            return new ReturnPrice(manualPrice, ScmOrderReturnPriceSourceEnum.MANUAL.name());
        }
        ReturnPrice suggested = suggest(customerId, skuId, saleUnit);
        if (suggested == null) {
            throw new ScmBusinessException(ORDER_RETURN_INDEPENDENT_PRICE_REQUIRED);
        }
        return suggested;
    }

    private ReturnPrice recentTransaction(Long customerId, Long skuId, String saleUnit) {
        return salesOrderItemDao.recentPrices(customerId, skuId, RECENT_ORDER_LIMIT).stream()
            .filter(row -> saleUnit.equals(row.getSaleUnit()) && row.getUnitPrice() != null).findFirst()
            .map(row -> new ReturnPrice(row.getUnitPrice(), ScmOrderReturnPriceSourceEnum.RECENT_TRANSACTION.name()))
            .orElse(null);
    }

    private ReturnPrice chain(Long customerId, Long skuId, String saleUnit) {
        var resolved = priceResolver.resolve(customerId, List.of(skuId), OffsetDateTime.now());
        if (resolved.isEmpty()) {
            return null;
        }
        var price = resolved.getFirst();
        // 退货不是销售：商品已停售或对客户隐藏时仍可能退货，因此这里只要求「有价格」，不套可售性判断。
        if (price.getUnitPrice() == null || price.getPriceSource() == null || !saleUnit.equals(price.getSaleUnit())) {
            return null;
        }
        return new ReturnPrice(price.getUnitPrice(), price.getPriceSource().name());
    }
}
