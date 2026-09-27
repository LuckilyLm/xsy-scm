package com.xsy.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import com.xsy.scm.pricing.dao.PriceBatchAuditDao;
import com.xsy.scm.pricing.domain.vo.PriceBatchRowFailureVO;
import com.xsy.scm.pricing.constant.ScmPriceBatchResultEnum;
import com.xsy.scm.common.constant.ScmOperator;

@Service
@RequiredArgsConstructor
public class PriceBatchAuditService {
    private final PriceBatchAuditDao priceBatchAuditDao;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void failed(String batchKey, int submittedRowCount, List<
            PriceBatchRowFailureVO> rowFailures) {
        try {
            priceBatchAuditDao.insert(batchKey, ScmPriceBatchResultEnum.FAILED.name(), submittedRowCount,
                    objectMapper.writeValueAsString(Map.of("failures", rowFailures, "failedCount", rowFailures.size(),
                            "submittedCount", submittedRowCount)),
                    ScmOperator.current());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize batch audit", e);
        }
    }
}
