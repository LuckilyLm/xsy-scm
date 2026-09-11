package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.customer.dto.CustomerTypePriceBatchRequest;
import com.xianshuyuan.scm.customer.vo.CustomerTypePriceBatchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerTypePriceBatchService {
    private final CustomerTypePriceBatchWriter writer;
    private final CustomerPriceBatchAuditService audits;

    public CustomerTypePriceBatchResponse create(CustomerTypePriceBatchRequest request) {
        try {
            return writer.write(request);
        } catch (RuntimeException failure) {
            audits.recordFailed(request, failure);
            throw failure;
        }
    }
}
