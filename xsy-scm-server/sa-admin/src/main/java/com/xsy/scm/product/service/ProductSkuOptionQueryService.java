package com.xsy.scm.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.form.ProductSkuOptionQueryForm;
import com.xsy.scm.product.domain.vo.ProductSkuOptionListVO;
import com.xsy.scm.common.exception.ScmBusinessException;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

@Service
@RequiredArgsConstructor
public class ProductSkuOptionQueryService {
    private final ProductSkuOptionDao productSkuOptionDao;

    public ProductSkuOptionListVO optionList(ProductSkuOptionQueryForm form) {
        if (form.getLimit() == null || form.getLimit() < 1 || form.getLimit() > 200)
            throw new ScmBusinessException(VALIDATION_ERROR);
        var rows = productSkuOptionDao.options(form, form.getLimit() + 1);
        boolean truncated = rows.size() > form.getLimit();
        return new ProductSkuOptionListVO(truncated ? rows.subList(0, form.getLimit()) : rows, truncated);
    }
}
