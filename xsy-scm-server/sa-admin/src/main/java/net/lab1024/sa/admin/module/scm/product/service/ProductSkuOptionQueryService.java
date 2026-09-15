package net.lab1024.sa.admin.module.scm.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuOptionDao;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuOptionQueryForm;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSkuOptionListVO;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
@Service @RequiredArgsConstructor public class ProductSkuOptionQueryService {
    private final ProductSkuOptionDao dao;
    public ProductSkuOptionListVO optionList(ProductSkuOptionQueryForm form) {
        if(form.getLimit()==null || form.getLimit()<1 || form.getLimit()>200) throw new ScmBusinessException(VALIDATION_ERROR);
        var rows=dao.options(form,form.getLimit()+1);
        boolean truncated=rows.size()>form.getLimit();
        return new ProductSkuOptionListVO(truncated ? rows.subList(0,form.getLimit()) : rows,truncated);
    }
}
