package com.xsy.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.util.XsyPageUtil;
import com.xsy.scm.pricing.dao.PriceHistoryDao;
import com.xsy.scm.pricing.domain.form.PriceHistoryQueryForm;
import com.xsy.scm.pricing.domain.vo.PriceHistoryVO;
import com.xsy.scm.pricing.constant.ScmPriceSourceEnum;

@Service
@RequiredArgsConstructor
public class PriceHistoryQueryService {
    private final PriceHistoryDao priceHistoryDao;

    public PageResult<PriceHistoryVO> query(PriceHistoryQueryForm form) {
        if (!ScmPriceSourceEnum.AGREEMENT.name().equals(form.getSource())
            && !ScmPriceSourceEnum.CUSTOMER_GROUP.name().equals(form.getSource())
            && !ScmPriceSourceEnum.TIME_PRICE.name().equals(form.getSource())) {
            form.setSource(null);
        }
        if (form.getOperationType() != null && form.getOperationType().isBlank())
            form.setOperationType(null);
        // 历史审计有固定顺序，不接受客户端指定排序。
        form.setSortItemList(java.util.List.of());
        var page = XsyPageUtil.convert2PageQuery(form);
        return XsyPageUtil.convert2PageResult(page, priceHistoryDao.query(page, form));
    }
}
