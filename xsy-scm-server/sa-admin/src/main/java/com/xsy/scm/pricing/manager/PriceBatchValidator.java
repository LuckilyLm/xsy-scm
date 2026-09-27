package com.xsy.scm.pricing.manager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.xsy.scm.pricing.domain.form.PriceBatchRowForm;
import com.xsy.scm.pricing.domain.vo.PriceBatchRowFailureVO;
import com.xsy.scm.common.exception.ScmBusinessException;

public final class PriceBatchValidator {
    private PriceBatchValidator() {
    }

    public static List<PriceBatchRowFailureVO> validate(List<PriceBatchRowForm> rows) {
        List<PriceBatchRowFailureVO> failures = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<Integer> numbers = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            var priceBatchRow = rows.get(i);
            int rowNumber = priceBatchRow == null || priceBatchRow.getRowNumber() == null
                    ? i + 1 : priceBatchRow.getRowNumber();
            if (priceBatchRow == null || priceBatchRow.getCustomerTypeId() == null
                    || priceBatchRow.getSkuId() == null || rowNumber < 1 || !numbers.add(rowNumber)) {
                failures.add(new PriceBatchRowFailureVO(rowNumber,
                        priceBatchRow == null ? null : priceBatchRow.getCustomerTypeId(),
                        priceBatchRow == null ? null : priceBatchRow.getSkuId(), 40035, "批量行标识不合法"));
                continue;
            }
            priceBatchRow.setRowNumber(rowNumber);
            if (!keys.add(priceBatchRow.getCustomerTypeId() + ":" + priceBatchRow.getSkuId()))
                failures.add(new PriceBatchRowFailureVO(rowNumber, priceBatchRow.getCustomerTypeId(),
                        priceBatchRow.getSkuId(), 40035, "请求内客户类型与 SKU 重复"));
            try {
                PriceValidation.amountAndPeriod(priceBatchRow.getUnitPrice(), priceBatchRow.getEffectiveFrom(),
                        priceBatchRow.getEffectiveTo());
            } catch (ScmBusinessException e) {
                failures.add(new PriceBatchRowFailureVO(rowNumber, priceBatchRow.getCustomerTypeId(),
                        priceBatchRow.getSkuId(), e.getErrorCode().getCode(), e.getErrorCode().getMsg()));
            }
        }
        return failures;
    }
}
