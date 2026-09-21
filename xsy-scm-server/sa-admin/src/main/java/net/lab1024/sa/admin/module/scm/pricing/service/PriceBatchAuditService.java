package net.lab1024.sa.admin.module.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import net.lab1024.sa.admin.module.scm.pricing.dao.PriceBatchAuditDao;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.PriceBatchRowFailureVO;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;

@Service
@RequiredArgsConstructor
public class PriceBatchAuditService {
    private final PriceBatchAuditDao dao;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void failed(String key, int submitted, List<PriceBatchRowFailureVO> failures) {
        try {
            dao.insert(key, "FAILED", submitted, json.writeValueAsString(Map.of("failures", failures, "failedCount", failures.size(), "submittedCount", submitted)), ScmOperator.current());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize batch audit", e);
        }
    }
}
