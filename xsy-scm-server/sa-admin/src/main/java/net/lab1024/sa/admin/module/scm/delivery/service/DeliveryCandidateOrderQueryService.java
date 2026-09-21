package net.lab1024.sa.admin.module.scm.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import net.lab1024.sa.admin.module.scm.delivery.dao.DeliveryQueryDao;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryQueryForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.DeliveryCandidateVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

@Service
@RequiredArgsConstructor
public class DeliveryCandidateOrderQueryService {
    private final DeliveryQueryDao queries;
    private final DeliveryEligibilityPolicy policy;

    public PageResult<DeliveryCandidateVO> query(DeliveryQueryForm form) {
        var page = DeliveryRouteQueryService.page(form);
        return SmartPageUtil.convert2PageResult(page, queries.candidates(page, form, policy.candidateStatuses()));
    }
}
