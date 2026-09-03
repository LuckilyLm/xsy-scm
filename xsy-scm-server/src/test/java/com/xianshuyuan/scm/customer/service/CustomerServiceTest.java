package com.xianshuyuan.scm.customer.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.dto.*;
import com.xianshuyuan.scm.customer.entity.*;
import com.xianshuyuan.scm.customer.mapper.*;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class CustomerServiceTest {
    private final CustomerMapper customers = mock(CustomerMapper.class);
    private final CustomerTypeMapper types = mock(CustomerTypeMapper.class);
    private final CustomerSkuVisibilityMapper visibility = mock(CustomerSkuVisibilityMapper.class);
    private final ProductSkuMapper skus = mock(ProductSkuMapper.class);
    private final CustomerService service = new CustomerService(customers, types, visibility, skus);

    @Test void rejectsDisabledCustomerType() {
        var type = new CustomerTypeEntity();
        type.setId(2L); type.setStatus(EnabledStatus.DISABLED); type.setDeleted(false);
        given(types.selectById(2L)).willReturn(type);

        assertThatThrownBy(() -> service.create(request(VisibilityPolicy.ALLOWLIST, List.of())))
            .isInstanceOf(BusinessException.class).hasMessageContaining("客户类型不存在");
    }

    @Test void rejectsVisibilityRowsForAllEnabledPolicy() {
        var type = new CustomerTypeEntity();
        type.setId(2L); type.setStatus(EnabledStatus.ENABLED); type.setDeleted(false);
        given(types.selectById(2L)).willReturn(type);

        assertThatThrownBy(() -> service.create(request(VisibilityPolicy.ALL_ENABLED,
            List.of(new CustomerSkuVisibilityRequest(null, null, 7L)))))
            .isInstanceOf(BusinessException.class).hasMessageContaining("不能提交可见性明细");
        verifyNoInteractions(skus);
    }

    @Test void customerDeleteUsesAtomicIdVersionPredicate() {
        var customer = new CustomerEntity();
        customer.setId(1L); customer.setVersion(3); customer.setDeleted(false);
        given(customers.selectById(1L)).willReturn(customer);
        given(visibility.selectActiveByCustomerId(1L)).willReturn(List.of());
        given(customers.softDelete(1L, 3)).willReturn(0);

        assertThatThrownBy(() -> service.delete(1L, 3))
            .isInstanceOf(BusinessException.class).hasMessageContaining("刷新后重试");
        verify(customers).softDelete(1L, 3);
    }

    private CustomerSaveRequest request(VisibilityPolicy policy, List<CustomerSkuVisibilityRequest> rows) {
        return new CustomerSaveRequest(null, "C-1", "Customer", 2L, EnabledStatus.ENABLED, policy, rows);
    }
}
