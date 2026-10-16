package com.xsy.scm.mall;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.mall.address.dao.MallCustomerAddressDao;
import com.xsy.scm.mall.address.domain.vo.MallAddressVO;
import com.xsy.scm.mall.order.dao.MallWarehouseDao;
import com.xsy.scm.mall.order.domain.vo.MallWarehouseOptionVO;
import com.xsy.scm.mall.order.service.MallWarehouseService;
import com.xsy.scm.warehouse.domain.entity.WarehouseEntity;
import com.xsy.scm.warehouse.service.WarehouseService;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MallWarehouseServiceTest {
    private final CustomerService customers = mock(CustomerService.class);
    private final WarehouseService warehouses = mock(WarehouseService.class);
    private final MallCustomerAddressDao addresses = mock(MallCustomerAddressDao.class);
    private final MallWarehouseDao rules = mock(MallWarehouseDao.class);
    private final MallWarehouseService service = new MallWarehouseService(customers, warehouses, addresses, rules);

    @Test
    void boundCustomerMustNotSwitchToADistrictWarehouse() {
        CustomerEntity customer = new CustomerEntity();
        customer.setMallDeliveryWarehouseId(10L);
        when(customers.require(7L)).thenReturn(customer);
        when(addresses.detailById(7L, 8L)).thenReturn(address());
        WarehouseEntity bound = new WarehouseEntity();
        bound.setId(10L);
        bound.setName("绑定仓");
        when(warehouses.require(10L)).thenReturn(bound);
        when(warehouses.enabled(10L)).thenReturn(true);
        assertThat(service.options(7L, 8L)).extracting(MallWarehouseOptionVO::getWarehouseId).containsExactly(10L);
        assertThat(service.require(7L, 8L, 10L)).isEqualTo(10L);
        assertThatThrownBy(() -> service.require(7L, 8L, 20L)).isInstanceOf(ScmBusinessException.class);
        verify(rules, never()).byDistrict(440305);
    }

    @Test
    void unboundCustomerCanChooseConfiguredDistrictWarehouse() {
        when(customers.require(7L)).thenReturn(new CustomerEntity());
        when(addresses.detailById(7L, 8L)).thenReturn(address());
        MallWarehouseOptionVO option = new MallWarehouseOptionVO();
        option.setWarehouseId(20L);
        option.setWarehouseName("区县仓");
        when(rules.byDistrict(440305)).thenReturn(List.of(option));
        assertThat(service.preferred(7L, 8L)).isEqualTo(20L);
    }

    private static MallAddressVO address() {
        MallAddressVO address = new MallAddressVO();
        address.setDistrictCode(440305);
        return address;
    }
}
