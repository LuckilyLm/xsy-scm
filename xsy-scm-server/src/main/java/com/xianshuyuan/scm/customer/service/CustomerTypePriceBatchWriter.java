package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.customer.dto.CustomerTypePriceBatchRequest;
import com.xianshuyuan.scm.customer.vo.CustomerTypePriceBatchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import com.xianshuyuan.scm.common.exception.BusinessException;

@Service
@RequiredArgsConstructor
public class CustomerTypePriceBatchWriter {
    private final CustomerTypePriceService prices;
    private final CustomerPriceBatchAuditService audits;

    @Transactional
    public CustomerTypePriceBatchResponse write(CustomerTypePriceBatchRequest request) {
        List<Long> ids = new ArrayList<>(request.rows().size());
        for (int index = 0; index < request.rows().size(); index++) {
            var row = request.rows().get(index);
            try {
                ids.add(prices.create(row.toSaveRequest()));
            } catch (BusinessException failure) {
                throw new CustomerTypePriceBatchRowException(row.rowNumber(), row.skuId(), failure);
            }
        }
        audits.recordSuccess(request);
        return new CustomerTypePriceBatchResponse(request.batchKey().trim(), ids.size(), List.copyOf(ids));
    }
}
