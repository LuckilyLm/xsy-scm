package com.xianshuyuan.scm.supplier.service;

import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import com.xianshuyuan.scm.supplier.entity.SupplierEntity;
import com.xianshuyuan.scm.supplier.entity.SupplierSkuEntity;
import com.xianshuyuan.scm.supplier.entity.WarehouseEntity;
import com.xianshuyuan.scm.supplier.mapper.SupplierMapper;
import com.xianshuyuan.scm.supplier.mapper.SupplierSkuMapper;
import com.xianshuyuan.scm.supplier.mapper.WarehouseMapper;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProcurementOptimisticLockIT {

    @Autowired
    private SupplierMapper suppliers;

    @Autowired
    private WarehouseMapper warehouses;

    @Autowired
    private SupplierSkuMapper supplierSkus;

    @Autowired
    private SqlSession sqlSession;

    @Test
    void rejectsStaleSupplierUpdate() {
        SupplierEntity entity = supplier();
        suppliers.insert(entity);

        SupplierEntity first = suppliers.selectById(entity.getId());
        sqlSession.clearCache();
        SupplierEntity stale = suppliers.selectById(entity.getId());
        first.setName("first");
        stale.setName("stale");

        assertThat(suppliers.updateById(first)).isEqualTo(1);
        assertThat(suppliers.updateById(stale)).isZero();
    }

    @Test
    void rejectsStaleWarehouseUpdate() {
        WarehouseEntity entity = warehouse();
        warehouses.insert(entity);

        WarehouseEntity first = warehouses.selectById(entity.getId());
        sqlSession.clearCache();
        WarehouseEntity stale = warehouses.selectById(entity.getId());
        first.setName("first");
        stale.setName("stale");

        assertThat(warehouses.updateById(first)).isEqualTo(1);
        assertThat(warehouses.updateById(stale)).isZero();
    }

    @Test
    void rejectsStaleSupplierSkuUpdate() {
        SupplierSkuEntity entity = supplierSku();
        supplierSkus.insert(entity);

        SupplierSkuEntity first = supplierSkus.selectById(entity.getId());
        sqlSession.clearCache();
        SupplierSkuEntity stale = supplierSkus.selectById(entity.getId());
        first.setPurchaseUnit("袋");
        stale.setPurchaseUnit("筐");

        assertThat(supplierSkus.updateById(first)).isEqualTo(1);
        assertThat(supplierSkus.updateById(stale)).isZero();
    }

    private SupplierEntity supplier() {
        SupplierEntity entity = new SupplierEntity();
        entity.setSupplierCode("IT-S-" + suffix());
        entity.setName("集成测试供应商");
        entity.setStatus(EnabledStatus.ENABLED);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy("SYSTEM");
        return entity;
    }

    private WarehouseEntity warehouse() {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setWarehouseCode("IT-W-" + suffix());
        entity.setName("集成测试仓库");
        entity.setStatus(EnabledStatus.ENABLED);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy("SYSTEM");
        return entity;
    }

    private SupplierSkuEntity supplierSku() {
        String suffix = suffix();
        SupplierSkuEntity entity = new SupplierSkuEntity();
        entity.setSupplierId(Long.parseLong(suffix, 16));
        entity.setSkuId(Long.parseLong(suffix, 16));
        entity.setSupplierCodeSnapshot("IT-S-" + suffix);
        entity.setSupplierNameSnapshot("集成测试供应商");
        entity.setSkuCodeSnapshot("IT-SKU-" + suffix);
        entity.setSkuNameSnapshot("集成测试商品");
        entity.setSpecValuesSnapshot(Map.of("规格", "大"));
        entity.setPurchaseUnit("箱");
        entity.setReferencePrice(new BigDecimal("12.3400"));
        entity.setDefaultSupplier(false);
        entity.setStatus(EnabledStatus.ENABLED);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy("SYSTEM");
        return entity;
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
