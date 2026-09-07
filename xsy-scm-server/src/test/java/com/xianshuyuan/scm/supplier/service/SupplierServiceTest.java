package com.xianshuyuan.scm.supplier.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import com.xianshuyuan.scm.supplier.dto.MasterDataStatusRequest;
import com.xianshuyuan.scm.supplier.dto.SupplierSaveRequest;
import com.xianshuyuan.scm.supplier.entity.SupplierEntity;
import com.xianshuyuan.scm.supplier.entity.WarehouseEntity;
import com.xianshuyuan.scm.supplier.mapper.SupplierMapper;
import com.xianshuyuan.scm.supplier.mapper.WarehouseMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SupplierServiceTest {
    private final SupplierMapper suppliers = mock(SupplierMapper.class);
    private final WarehouseMapper warehouses = mock(WarehouseMapper.class);
    private final SupplierService service = new SupplierService(suppliers);
    private final WarehouseService warehouseService = new WarehouseService(warehouses);

    @Test
    void rejectsDisabledSupplierForEnabledRequirement() {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(1L);
        entity.setStatus(EnabledStatus.DISABLED);
        entity.setDeleted(false);
        given(suppliers.selectById(1L)).willReturn(entity);

        assertThatThrownBy(() -> service.requireEnabledSupplier(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    void updatesSupplierWithoutChangingStatus() {
        SupplierEntity existing = new SupplierEntity();
        existing.setId(1L);
        existing.setStatus(EnabledStatus.DISABLED);
        existing.setVersion(2);
        existing.setDeleted(false);
        given(suppliers.selectById(1L)).willReturn(existing);
        given(suppliers.updateById(any(SupplierEntity.class))).willReturn(1);

        service.updateSupplier(1L, new SupplierSaveRequest("S-2", "新供应商", "备注", 2));

        var captured = org.mockito.ArgumentCaptor.forClass(SupplierEntity.class);
        verify(suppliers).updateById(captured.capture());
        org.assertj.core.api.Assertions.assertThat(captured.getValue().getStatus()).isNull();
        org.assertj.core.api.Assertions.assertThat(captured.getValue().getVersion()).isEqualTo(2);
    }

    @Test
    void mapsStaleSupplierVersionToConflict() {
        SupplierEntity existing = new SupplierEntity();
        existing.setId(1L);
        existing.setDeleted(false);
        given(suppliers.selectById(1L)).willReturn(existing);
        given(suppliers.updateById(any(SupplierEntity.class))).willReturn(0);

        assertThatThrownBy(() -> service.updateSupplier(1L,
                new SupplierSaveRequest("S-2", "供应商", null, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新后重试");
    }

    @Test
    void updatesWarehouseStatusWithVersion() {
        WarehouseEntity existing = new WarehouseEntity();
        existing.setId(4L);
        existing.setStatus(EnabledStatus.ENABLED);
        existing.setVersion(3);
        existing.setDeleted(false);
        given(warehouses.selectById(4L)).willReturn(existing);
        given(warehouses.updateById(any(WarehouseEntity.class))).willReturn(1);

        warehouseService.updateWarehouseStatus(4L,
                new MasterDataStatusRequest(3, EnabledStatus.DISABLED));

        verify(warehouses).updateById(any(WarehouseEntity.class));
        org.assertj.core.api.Assertions.assertThat(existing.getStatus()).isEqualTo(EnabledStatus.DISABLED);
        org.assertj.core.api.Assertions.assertThat(existing.getVersion()).isEqualTo(3);
    }
}
