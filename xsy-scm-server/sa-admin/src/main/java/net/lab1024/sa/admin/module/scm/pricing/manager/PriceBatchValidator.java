package net.lab1024.sa.admin.module.scm.pricing.manager;

import java.util.*;

import net.lab1024.sa.admin.module.scm.pricing.domain.form.*;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;

public final class PriceBatchValidator {
    private PriceBatchValidator() {
    }

    public static List<PriceBatchRowFailureVO> validate(List<PriceBatchRowForm> rows) {
        List<PriceBatchRowFailureVO> failures = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<Integer> numbers = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            int number = r == null || r.getRowNumber() == null ? i + 1 : r.getRowNumber();
            if (r == null || r.getCustomerTypeId() == null || r.getSkuId() == null || number < 1 || !numbers.add(number)) {
                failures.add(new PriceBatchRowFailureVO(number, r == null ? null : r.getCustomerTypeId(), r == null ? null : r.getSkuId(), 40035, "批量行标识不合法"));
                continue;
            }
            r.setRowNumber(number);
            if (!keys.add(r.getCustomerTypeId() + ":" + r.getSkuId()))
                failures.add(new PriceBatchRowFailureVO(number, r.getCustomerTypeId(), r.getSkuId(), 40035, "请求内客户类型与 SKU 重复"));
            try {
                PriceValidation.amountAndPeriod(r.getUnitPrice(), r.getEffectiveFrom(), r.getEffectiveTo());
            } catch (ScmBusinessException e) {
                failures.add(new PriceBatchRowFailureVO(number, r.getCustomerTypeId(), r.getSkuId(), e.getErrorCode().getCode(), e.getErrorCode().getMsg()));
            }
        }
        return failures;
    }
}
