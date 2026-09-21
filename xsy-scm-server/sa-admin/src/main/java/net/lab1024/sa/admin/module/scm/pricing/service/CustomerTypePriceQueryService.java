package net.lab1024.sa.admin.module.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.admin.module.scm.pricing.dao.CustomerTypePriceDao;
import net.lab1024.sa.admin.module.scm.pricing.domain.form.CustomerTypePriceQueryForm;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.CustomerTypePriceVO;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;

import static net.lab1024.sa.admin.module.scm.pricing.constant.PricingErrorCode.CUSTOMER_TYPE_PRICE_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

@Service
@RequiredArgsConstructor
public class CustomerTypePriceQueryService {
    private final CustomerTypePriceDao dao;

    public PageResult<CustomerTypePriceVO> query(CustomerTypePriceQueryForm form) {
        if (form.getSortItemList() != null && form.getSortItemList().stream().anyMatch(i -> !java.util.Set.of("effective_from", "effective_to", "unit_price", "updated_at").contains(i.getColumn())))
            throw new ScmBusinessException(VALIDATION_ERROR);
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty()) page.addOrder(OrderItem.desc("effective_from"), OrderItem.desc("price_id"));
        return SmartPageUtil.convert2PageResult(page, dao.queryPage(page, form));
    }

    public CustomerTypePriceVO detail(Long id) {
        var v = dao.detail(id);
        if (v == null) throw new ScmBusinessException(CUSTOMER_TYPE_PRICE_NOT_FOUND);
        return v;
    }
}
