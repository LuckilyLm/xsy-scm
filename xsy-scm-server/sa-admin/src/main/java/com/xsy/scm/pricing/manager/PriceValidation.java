package com.xsy.scm.pricing.manager;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.constant.ScmShelfStatusEnum;
import com.xsy.scm.pricing.constant.ScmUnavailableReasonEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.util.ScmDecimalStrings;

import static com.xsy.scm.pricing.constant.PricingErrorCode.PERIOD_INVALID;
import static com.xsy.scm.pricing.constant.PricingErrorCode.PRICE_INVALID;
import static com.xsy.scm.pricing.constant.PricingErrorCode.SKU_NOT_SELLABLE;

@Component
@RequiredArgsConstructor
public class PriceValidation {
    private final ProductSkuOptionDao productSkuOptionDao;

    public static void amountAndPeriod(String unitPrice, OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo) {
        if (unitPrice == null || !unitPrice.matches(ScmDecimalStrings.PATTERN)) {
            throw new ScmBusinessException(PRICE_INVALID);
        }
        if (effectiveFrom == null || (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom))) {
            throw new ScmBusinessException(PERIOD_INVALID);
        }
    }

    public void requireSellable(Long skuId) {
        if (skuId == null) throw new ScmBusinessException(SKU_NOT_SELLABLE);
        var skuOptions = productSkuOptionDao.selectByIds(List.of(skuId));
        if (unavailable(skuOptions.isEmpty() ? null : skuOptions.getFirst(), true) != null)
            throw new ScmBusinessException(SKU_NOT_SELLABLE);
    }

    public static ScmUnavailableReasonEnum unavailable(ProductSkuOptionVO skuOption, boolean visibleToCustomer) {
        if (skuOption == null) return ScmUnavailableReasonEnum.SKU_NOT_FOUND;
        if (!ScmShelfStatusEnum.ON_SHELF.name().equals(skuOption.getStatus())) {
            return ScmUnavailableReasonEnum.SKU_OFF_SHELF;
        }
        if (!ScmShelfStatusEnum.ON_SHELF.name().equals(skuOption.getSpuStatus())) {
            return ScmUnavailableReasonEnum.SPU_OFF_SHELF;
        }
        if (!ScmEnableStatusEnum.ENABLED.name().equals(skuOption.getCategoryStatus())) {
            return ScmUnavailableReasonEnum.CATEGORY_DISABLED;
        }
        return visibleToCustomer ? null : ScmUnavailableReasonEnum.NOT_VISIBLE;
    }
}
