package net.lab1024.sa.admin.module.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.admin.module.scm.pricing.dao.PriceHistoryDao;
import net.lab1024.sa.admin.module.scm.pricing.domain.form.PriceHistoryQueryForm;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.PriceHistoryVO;

@Service
@RequiredArgsConstructor
public class PriceHistoryQueryService {
    private final PriceHistoryDao dao;

    public PageResult<PriceHistoryVO> query(PriceHistoryQueryForm form) {
        if (!"AGREEMENT".equals(form.getSource()) && !"CUSTOMER_TYPE".equals(form.getSource())) form.setSource(null);
        if (form.getOperationType() != null && form.getOperationType().isBlank()) form.setOperationType(null);
        // History has a stable audit ordering; user supplied ordering is not accepted.
        form.setSortItemList(java.util.List.of());
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page, dao.query(page, form));
    }
}
