package com.xsy.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import com.xsy.scm.pricing.dao.AgreementPriceDao;
import com.xsy.scm.pricing.domain.form.AgreementPriceQueryForm;
import com.xsy.scm.pricing.domain.vo.AgreementPriceVO;
import com.xsy.scm.common.exception.ScmBusinessException;

import static com.xsy.scm.pricing.constant.PricingErrorCode.AGREEMENT_PRICE_NOT_FOUND;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

@Service
@RequiredArgsConstructor
public class AgreementPriceQueryService {
    private final AgreementPriceDao agreementPriceDao;

    public PageResult<AgreementPriceVO> query(AgreementPriceQueryForm form) {
        if (form.getSortItemList() != null && form.getSortItemList().stream().anyMatch(sortItem -> !java.util.Set
                .of("effective_from", "effective_to", "unit_price", "updated_at").contains(sortItem.getColumn())))
            throw new ScmBusinessException(VALIDATION_ERROR);
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty())
            page.addOrder(OrderItem.desc("effective_from"), OrderItem.desc("price_id"));
        return SmartPageUtil.convert2PageResult(page, agreementPriceDao.queryPage(page, form));
    }

    public AgreementPriceVO detail(Long agreementPriceId) {
        var agreementPrice = agreementPriceDao.detail(agreementPriceId);
        if (agreementPrice == null)
            throw new ScmBusinessException(AGREEMENT_PRICE_NOT_FOUND);
        return agreementPrice;
    }
}
