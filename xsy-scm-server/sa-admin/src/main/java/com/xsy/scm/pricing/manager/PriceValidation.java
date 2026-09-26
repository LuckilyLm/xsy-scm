package com.xsy.scm.pricing.manager;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import com.xsy.scm.pricing.constant.ScmUnavailableReasonEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.util.ScmDecimalStrings;

import static com.xsy.scm.pricing.constant.PricingErrorCode.*;

@Component
@RequiredArgsConstructor
public class PriceValidation {
    private final ProductSkuOptionDao skus;

    public static void amountAndPeriod(String amount, OffsetDateTime from, OffsetDateTime to) {
        if (amount == null || !amount.matches(ScmDecimalStrings.PATTERN)) throw new ScmBusinessException(PRICE_INVALID);
        if (from == null || (to != null && !to.isAfter(from))) throw new ScmBusinessException(PERIOD_INVALID);
    }

    public void requireSellable(Long id) {
        if (id == null) throw new ScmBusinessException(SKU_NOT_SELLABLE);
        var rows = skus.selectByIds(List.of(id));
        if (unavailable(rows.isEmpty() ? null : rows.getFirst(), true) != null)
            throw new ScmBusinessException(SKU_NOT_SELLABLE);
    }

    public static ScmUnavailableReasonEnum unavailable(ProductSkuOptionVO sku, boolean visible) {
        if (sku == null) return ScmUnavailableReasonEnum.SKU_NOT_FOUND;
        if (!"ON_SHELF".equals(sku.getStatus())) return ScmUnavailableReasonEnum.SKU_OFF_SHELF;
        if (!"ON_SHELF".equals(sku.getSpuStatus())) return ScmUnavailableReasonEnum.SPU_OFF_SHELF;
        if (!"ENABLED".equals(sku.getCategoryStatus())) return ScmUnavailableReasonEnum.CATEGORY_DISABLED;
        return visible ? null : ScmUnavailableReasonEnum.NOT_VISIBLE;
    }
}
