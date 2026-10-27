package com.xsy.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.pricing.dao.CustomerGroupPriceDao;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceQueryForm;
import com.xsy.scm.pricing.domain.vo.CustomerGroupPriceVO;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.pricing.constant.PricingErrorCode.GROUP_PRICE_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class CustomerGroupPriceQueryService {
    private final CustomerGroupPriceDao customerGroupPriceDao;

    public PageResult<CustomerGroupPriceVO> query(CustomerGroupPriceQueryForm form) {
        if (form.getSortItemList() != null && form.getSortItemList().stream().anyMatch(sortItem -> !java.util.Set
            .of("effective_from", "effective_to", "unit_price", "updated_at").contains(sortItem.getColumn())))
            throw new ScmBusinessException(VALIDATION_ERROR);
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty())
            page.addOrder(OrderItem.desc("effective_from"), OrderItem.desc("group_price_id"));
        return SmartPageUtil.convert2PageResult(page, customerGroupPriceDao.queryPage(page, form));
    }

    public CustomerGroupPriceVO detail(Long groupPriceId) {
        var groupPrice = customerGroupPriceDao.detail(groupPriceId);
        if (groupPrice == null)
            throw new ScmBusinessException(GROUP_PRICE_NOT_FOUND);
        return groupPrice;
    }
}
