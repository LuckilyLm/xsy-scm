package com.xsy.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import com.xsy.scm.pricing.dao.CustomerTypePriceDao;
import com.xsy.scm.pricing.dao.PriceBatchAuditDao;
import com.xsy.scm.pricing.domain.form.CustomerTypePriceAddForm;
import com.xsy.scm.pricing.domain.form.PriceBatchForm;
import com.xsy.scm.pricing.domain.form.PriceBatchRowForm;
import com.xsy.scm.pricing.domain.vo.PriceBatchResultVO;
import com.xsy.scm.pricing.domain.vo.PriceBatchRowFailureVO;
import com.xsy.scm.pricing.manager.PriceValidation;
import com.xsy.scm.customer.dao.CustomerTypeDao;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import com.xsy.scm.customer.domain.entity.CustomerTypeEntity;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.pricing.constant.ScmPriceBatchResultEnum;

import static com.xsy.scm.pricing.constant.PricingErrorCode.PRICE_BATCH_KEY_DUPLICATE;

@Service
@RequiredArgsConstructor
public class PriceBatchWriter {
    private final CustomerTypePriceDao customerTypePriceDao;
    private final CustomerTypePriceService customerTypePriceService;
    private final CustomerTypeDao customerTypeDao;
    private final ProductSkuOptionDao productSkuOptionDao;
    private final PriceBatchAuditDao priceBatchAuditDao;

    public static class Rejected extends RuntimeException {
        public final List<PriceBatchRowFailureVO> failures;

        public Rejected(List<PriceBatchRowFailureVO> failures) {
            this.failures = List.copyOf(failures);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public PriceBatchResultVO write(PriceBatchForm form) {
        if (priceBatchAuditDao.successCount(form.getBatchKey()) > 0) {
            throw new ScmBusinessException(PRICE_BATCH_KEY_DUPLICATE);
        }
        var priceBatchRows = form.getRows().stream().sorted(Comparator.comparing(PriceBatchRowForm::getCustomerTypeId))
                .toList();
        priceBatchRows.stream().map(PriceBatchRowForm::getCustomerTypeId).distinct()
                .forEach(customerTypePriceDao::lockParent);
        var customerTypeById = new HashMap<Long, CustomerTypeEntity>();
        customerTypeDao
                .selectByIds(priceBatchRows.stream().map(PriceBatchRowForm::getCustomerTypeId).distinct().toList())
                .forEach(customerType -> customerTypeById.put(customerType.getId(), customerType));
        var productSkuOptionById = new HashMap<Long, ProductSkuOptionVO>();
        productSkuOptionDao.selectByIds(priceBatchRows.stream().map(PriceBatchRowForm::getSkuId).distinct().toList())
                .forEach(skuOption -> productSkuOptionById.put(skuOption.getSkuId(), skuOption));
        List<PriceBatchRowFailureVO> rowFailures = new ArrayList<>();
        for (var priceBatchRow : priceBatchRows) {
            var customerType = customerTypeById.get(priceBatchRow.getCustomerTypeId());
            if (customerType == null || !ScmEnableStatusEnum.ENABLED.name().equals(customerType.getStatus())) {
                rowFailures.add(new PriceBatchRowFailureVO(priceBatchRow.getRowNumber(),
                        priceBatchRow.getCustomerTypeId(), priceBatchRow.getSkuId(), 40431, "客户类型不存在或已停用"));
            }
            if (PriceValidation.unavailable(productSkuOptionById.get(priceBatchRow.getSkuId()), true) != null) {
                rowFailures.add(new PriceBatchRowFailureVO(priceBatchRow.getRowNumber(),
                        priceBatchRow.getCustomerTypeId(), priceBatchRow.getSkuId(), 40949, "SKU 不可售"));
            }
            if (customerTypePriceDao.countOverlapping(priceBatchRow.getCustomerTypeId(), priceBatchRow.getSkuId(),
                    priceBatchRow.getEffectiveFrom(), priceBatchRow.getEffectiveTo(), null) > 0) {
                rowFailures.add(new PriceBatchRowFailureVO(priceBatchRow.getRowNumber(),
                        priceBatchRow.getCustomerTypeId(), priceBatchRow.getSkuId(), 40935, "客户类型价有效期重叠"));
            }
        }
        if (!rowFailures.isEmpty())
            throw new Rejected(rowFailures);
        List<Long> createdPriceIds = new ArrayList<>();
        for (var priceBatchRow : priceBatchRows) {
            var customerTypePriceForm = new CustomerTypePriceAddForm();
            customerTypePriceForm.setCustomerTypeId(priceBatchRow.getCustomerTypeId());
            customerTypePriceForm.setSkuId(priceBatchRow.getSkuId());
            customerTypePriceForm.setUnitPrice(priceBatchRow.getUnitPrice());
            customerTypePriceForm.setEffectiveFrom(priceBatchRow.getEffectiveFrom());
            customerTypePriceForm.setEffectiveTo(priceBatchRow.getEffectiveTo());
            createdPriceIds.add(customerTypePriceService.add(customerTypePriceForm));
        }
        priceBatchAuditDao.insert(form.getBatchKey(), ScmPriceBatchResultEnum.SUCCESS.name(), priceBatchRows.size(),
                null, ScmOperator.current());
        return new PriceBatchResultVO(form.getBatchKey(), true, createdPriceIds.size(), createdPriceIds, List.of());
    }
}
