package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.dto.CustomerTypePriceBatchRequest;
import com.xianshuyuan.scm.customer.dto.CustomerTypePriceBatchRowRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CustomerTypePriceBatchServiceTest {
    @Test void failedBatchIsAuditedAfterTransactionalWriterRollsBack() {
        CustomerTypePriceBatchWriter writer = mock(CustomerTypePriceBatchWriter.class);
        CustomerPriceBatchAuditService audits = mock(CustomerPriceBatchAuditService.class);
        CustomerTypePriceBatchRequest request = request();
        BusinessException failure = new BusinessException(CustomerErrorCodes.CUSTOMER_TYPE_PRICE_OVERLAP);
        when(writer.write(request)).thenThrow(failure);
        CustomerTypePriceBatchService service = new CustomerTypePriceBatchService(writer, audits);

        assertThatThrownBy(() -> service.create(request)).isSameAs(failure);

        verify(audits).recordFailed(request, failure);
    }

    private CustomerTypePriceBatchRequest request() {
        return new CustomerTypePriceBatchRequest("BATCH-20260911-01", List.of(
                new CustomerTypePriceBatchRowRequest(17, 3L, 8L, new BigDecimal("6.2500"),
                        OffsetDateTime.parse("2026-09-11T00:00:00+08:00"), null)));
    }
}
