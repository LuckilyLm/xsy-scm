package com.xianshuyuan.scm.customer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.dto.CustomerTypePriceBatchRequest;
import com.xianshuyuan.scm.customer.entity.CustomerPriceBatchAuditEntity;
import com.xianshuyuan.scm.customer.mapper.CustomerPriceBatchAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerPriceBatchAuditService {
    private final CustomerPriceBatchAuditMapper audits;
    private final ObjectMapper json;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(CustomerTypePriceBatchRequest request, RuntimeException failure) {
        Integer code = failure instanceof BusinessException business ? business.getErrorCode().code() : null;
        java.util.LinkedHashMap<String, Object> error = new java.util.LinkedHashMap<>();
        error.put("code", code == null ? 50000 : code);
        error.put("message", failure.getMessage() == null ? "批量调价失败" : failure.getMessage());
        if (failure instanceof CustomerTypePriceBatchRowException row) {
            error.put("rowNumber", row.getRowNumber());
            error.put("skuId", row.getSkuId());
        }
        insert(request, "FAILED", json.valueToTree(error));
    }

    void recordSuccess(CustomerTypePriceBatchRequest request) {
        insert(request, "SUCCESS", null);
    }

    private void insert(CustomerTypePriceBatchRequest request, String result, com.fasterxml.jackson.databind.JsonNode error) {
        CustomerPriceBatchAuditEntity entity = new CustomerPriceBatchAuditEntity();
        entity.setBatchKey(request.batchKey().trim());
        entity.setOperationType("CUSTOMER_TYPE_PRICE_CREATE");
        entity.setResult(result);
        entity.setRowCount(request.rows().size());
        entity.setErrorData(error);
        entity.setCreatedBy("SYSTEM");
        audits.insert(entity);
    }
}
