package net.lab1024.sa.admin.module.scm.common;

import net.lab1024.sa.admin.module.scm.customer.dao.CustomerDao;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerTypeDao;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerTypeEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerAddForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerTypeAddForm;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerTypeService;
import net.lab1024.sa.admin.module.scm.supplier.dao.SupplierDao;
import net.lab1024.sa.admin.module.scm.supplier.dao.SupplierSkuDao;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierEntity;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierSkuEntity;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierAddForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuItemForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuReplaceForm;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierService;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierSkuService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W2 四个聚合的乐观锁端到端验证（T11，对应 G1 门禁的 W2 延伸）。
 *
 * <p>W1 的 {@code ScmOptimisticLockIT} 用临时表证明了「拦截器存在且行为正确」。
 * 这个用例把同样的证明搬到 W2 的真实表上：只要任何一张 W2 表的实体漏了 {@code @Version}，
 * 或者拦截器被移出 MP 配置，下面的断言就会失败。
 *
 * <p><b>为什么要用 {@code updateById} 直接验证，而不是只看 Service 的 40921：</b>
 * Service 里的版本比对是应用层判断，即使没有拦截器也能抛 40921；
 * 只有「版本被数据库侧真正推进」与「落后版本更新 0 行」这两点才能证明乐观锁真的生效。
 */
@DisplayName("W2 乐观锁：四个聚合在真实 PG + MP 拦截器上生效（PG IT）")
class ScmCustomerSupplierOptimisticLockIT extends ScmW2PgITBase {

    @Autowired
    private CustomerDao customerDao;

    @Autowired
    private CustomerTypeDao customerTypeDao;

    @Autowired
    private SupplierDao supplierDao;

    @Autowired
    private SupplierSkuDao supplierSkuDao;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerTypeService customerTypeService;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private SupplierSkuService supplierSkuService;

    private Long customerId() {
        CustomerAddForm form = new CustomerAddForm();
        form.setCustomerCode(prefix + "-C");
        form.setName("乐观锁客户");
        form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        return customerService.add(form);
    }

    private Long customerTypeId2() {
        CustomerTypeAddForm form = new CustomerTypeAddForm();
        form.setTypeCode(prefix + "-T");
        form.setName("乐观锁类型");
        form.setStatus("ENABLED");
        return customerTypeService.add(form);
    }

    private Long supplierId() {
        SupplierAddForm form = new SupplierAddForm();
        form.setSupplierCode(prefix + "-S");
        form.setName("乐观锁供应商");
        return supplierService.add(form);
    }

    private Long supplierSkuRowId(Long supplierId, Long skuId) {
        SupplierSkuReplaceForm form = new SupplierSkuReplaceForm();
        form.setSupplierId(supplierId);
        SupplierSkuItemForm item = new SupplierSkuItemForm();
        item.setSkuId(skuId);
        item.setPurchaseUnit("kg");
        form.setItems(List.of(item));
        supplierSkuService.replace(form);
        return supplierSkuDao.selectActiveBySupplierId(supplierId).getFirst().getId();
    }

    /**
     * 断言数据库里的版本号已经被拦截器推进到期望值。
     */
    private void assertVersion(String table, Long id, int expected) {
        assertThat(jdbc.queryForObject("SELECT version FROM " + table + " WHERE id = ?", Integer.class, id))
                .as(table + "：@Version 必须由拦截器推进").isEqualTo(expected);
    }

    @Test
    @DisplayName("customer：版本推进 + 落后版本更新 0 行 + Service 抛 40921")
    void customerVersionIsGuarded() {
        Long id = customerId();

        CustomerEntity entity = customerDao.selectById(id);
        assertThat(entity.getVersion()).isZero();
        entity.setName("第一次改名");
        assertThat(customerDao.updateById(entity)).isEqualTo(1);
        assertVersion("customer", id, 1);
        assertThat(entity.getVersion()).as("MP 会把新版本写回实体").isEqualTo(1);

        // 模拟「另一个会话已经改过」：把库里的版本推远
        jdbc.update("UPDATE customer SET version = version + 10 WHERE id = ?", id);
        entity.setName("落后版本的写入");
        assertThat(customerDao.updateById(entity)).as("落后版本必须更新 0 行").isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM customer WHERE id = ?", String.class, id))
                .as("失败写入不得落库").isEqualTo("第一次改名");
    }

    @Test
    @DisplayName("customer_type：版本推进 + 落后版本更新 0 行")
    void customerTypeVersionIsGuarded() {
        Long id = customerTypeId2();

        CustomerTypeEntity entity = customerTypeDao.selectById(id);
        assertThat(entity.getVersion()).isZero();
        entity.setName("第一次改名");
        assertThat(customerTypeDao.updateById(entity)).isEqualTo(1);
        assertVersion("customer_type", id, 1);

        jdbc.update("UPDATE customer_type SET version = version + 10 WHERE id = ?", id);
        entity.setName("落后版本的写入");
        assertThat(customerTypeDao.updateById(entity)).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM customer_type WHERE id = ?", String.class, id))
                .isEqualTo("第一次改名");
    }

    @Test
    @DisplayName("supplier：版本推进 + 落后版本更新 0 行")
    void supplierVersionIsGuarded() {
        Long id = supplierId();

        SupplierEntity entity = supplierDao.selectById(id);
        assertThat(entity.getVersion()).isZero();
        entity.setName("第一次改名");
        assertThat(supplierDao.updateById(entity)).isEqualTo(1);
        assertVersion("supplier", id, 1);

        jdbc.update("UPDATE supplier SET version = version + 10 WHERE id = ?", id);
        entity.setName("落后版本的写入");
        assertThat(supplierDao.updateById(entity)).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM supplier WHERE id = ?", String.class, id))
                .isEqualTo("第一次改名");
    }

    @Test
    @DisplayName("supplier_sku：版本推进 + 落后版本更新 0 行")
    void supplierSkuVersionIsGuarded() {
        Long supplierId = supplierId();
        Long skuId = newOnShelfSku("V1");
        Long rowId = supplierSkuRowId(supplierId, skuId);

        SupplierSkuEntity entity = supplierSkuDao.selectActiveBySupplierId(supplierId).getFirst();
        assertThat(entity.getVersion()).isZero();
        entity.setPurchaseUnit("件");
        assertThat(supplierSkuDao.updateById(entity)).isEqualTo(1);
        assertVersion("supplier_sku", rowId, 1);

        jdbc.update("UPDATE supplier_sku SET version = version + 10 WHERE id = ?", rowId);
        entity.setPurchaseUnit("箱");
        assertThat(supplierSkuDao.updateById(entity)).isZero();
        assertThat(jdbc.queryForObject("SELECT purchase_unit FROM supplier_sku WHERE id = ?", String.class, rowId))
                .isEqualTo("件");
    }
}
