package com.xsy.scm.mall;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.mall.order.domain.form.MallCustomerWarehouseBindingForm;
import com.xsy.scm.mall.order.service.MallWarehouseAdminService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/** 客户配送仓管理入口必须经过客户域写命令并保留数据库乐观锁。 */
class MallWarehouseAdminPgIT extends ScmW5PgITBase {

    @Autowired
    private MallWarehouseAdminService adminService;

    @Test
    void bindingAndClearingWarehousePersistThroughCustomerDomain() {
        Long customerId = newCustomer();
        Long warehouseId = seedWarehouseId();
        int version = jdbc.queryForObject("SELECT version FROM xsy_customer WHERE id=?", Integer.class, customerId);

        MallCustomerWarehouseBindingForm bind = new MallCustomerWarehouseBindingForm();
        bind.setCustomerId(customerId);
        bind.setWarehouseId(warehouseId);
        bind.setVersion(version);
        adminService.bindCustomer(bind);
        Map<String, Object> bound = jdbc.queryForMap(
                "SELECT mall_delivery_warehouse_id, version FROM xsy_customer WHERE id=?", customerId);
        assertThat(bound.get("mall_delivery_warehouse_id")).isEqualTo(warehouseId);
        assertThat((Integer) bound.get("version")).isEqualTo(version + 1);

        MallCustomerWarehouseBindingForm clear = new MallCustomerWarehouseBindingForm();
        clear.setCustomerId(customerId);
        clear.setVersion(version + 1);
        adminService.bindCustomer(clear);
        Map<String, Object> cleared = jdbc.queryForMap(
                "SELECT mall_delivery_warehouse_id, version FROM xsy_customer WHERE id=?", customerId);
        assertThat(cleared.get("mall_delivery_warehouse_id")).isNull();
        assertThat((Integer) cleared.get("version")).isEqualTo(version + 2);
    }
}
