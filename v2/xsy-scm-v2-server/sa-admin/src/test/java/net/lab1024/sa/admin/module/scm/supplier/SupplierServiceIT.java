package net.lab1024.sa.admin.module.scm.supplier;

import net.lab1024.sa.admin.module.scm.common.ScmW2PgITBase;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierAddForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierDeleteForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuItemForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuReplaceForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierStatusForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierUpdateForm;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierService;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierSkuService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 供应商写路径在真实 PostgreSQL 上的行为（T11）。
 *
 * <p>两条 legacy 不变量在这里被端到端证明：
 * <ul>
 *   <li><b>S7</b>：新建供应商强制 {@code ENABLED}，客户端无法指定初始状态；</li>
 *   <li><b>S6</b>：{@code update} 不触碰 {@code status}——先停用再编辑，状态必须保持停用。</li>
 * </ul>
 */
@DisplayName("供应商：写路径（PG IT）")
class SupplierServiceIT extends ScmW2PgITBase {

    @Autowired
    private SupplierService service;

    @Autowired
    private SupplierSkuService skuService;

    private SupplierAddForm form(String suffix) {
        SupplierAddForm form = new SupplierAddForm();
        form.setSupplierCode(" " + prefix + "-" + suffix + " ");
        form.setName("供应商" + suffix);
        return form;
    }

    private SupplierUpdateForm updateForm(Long id, int version, String suffix) {
        SupplierUpdateForm form = new SupplierUpdateForm();
        form.setSupplierId(id);
        form.setVersion(version);
        form.setSupplierCode(prefix + "-" + suffix);
        form.setName("改名" + suffix);
        return form;
    }

    @Test
    @DisplayName("新建强制 ENABLED（S7），编码归一化，审计写入操作人")
    void addForcesEnabledAndNormalizesCode() {
        Long id = service.add(form("a"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT supplier_code, status, version, deleted, created_by FROM supplier WHERE id = ?", id);
        assertThat(row.get("supplier_code")).isEqualTo((prefix + "-a").toUpperCase(Locale.ROOT));
        assertThat(row.get("status")).isEqualTo("ENABLED");
        assertThat(((Number) row.get("version")).intValue()).isZero();
        assertThat(row.get("deleted")).isEqualTo(false);
        assertThat(row.get("created_by")).isEqualTo("1:1");
    }

    @Test
    @DisplayName("活动编码唯一：重复 40944；软删后编码可复用")
    void duplicateCodeRejectedAndReusableAfterDelete() {
        Long id = service.add(form("b"));
        expectCode(() -> service.add(form("b")), 40944);

        SupplierDeleteForm deletion = new SupplierDeleteForm();
        deletion.setSupplierId(id);
        deletion.setVersion(0);
        service.delete(deletion);

        assertThat(service.add(form("b"))).isNotEqualTo(id);
    }

    @Test
    @DisplayName("update 不触碰 status：先停用再编辑，状态保持 DISABLED（S6）")
    void updateNeverTouchesStatus() {
        Long id = service.add(form("c"));

        SupplierStatusForm status = new SupplierStatusForm();
        status.setSupplierId(id);
        status.setVersion(0);
        status.setStatus("DISABLED");
        service.updateStatus(status);

        service.update(updateForm(id, 1, "c"));

        assertThat(jdbc.queryForObject("SELECT status FROM supplier WHERE id = ?", String.class, id))
                .isEqualTo("DISABLED");
        assertThat(jdbc.queryForObject("SELECT name FROM supplier WHERE id = ?", String.class, id))
                .isEqualTo("改名c");
    }

    @Test
    @DisplayName("旧版本提交 → 40921；停用供应商不可用于采购（40940）")
    void staleVersionConflictsAndDisabledSupplierIsNotUsable() {
        Long id = service.add(form("d"));
        SupplierUpdateForm update = updateForm(id, 0, "d");
        service.update(update);
        expectCode(() -> service.update(update), 40921);

        SupplierStatusForm status = new SupplierStatusForm();
        status.setSupplierId(id);
        status.setVersion(0);
        status.setStatus("DISABLED");
        expectCode(() -> service.updateStatus(status), 40921);

        status.setVersion(1);
        service.updateStatus(status);
        expectCode(() -> service.requireEnabled(id), 40940);
    }

    @Test
    @DisplayName("被活动商品关联引用时拒绝删除（40947）；清空关联后即可删除")
    void deleteIsBlockedWhileSkuRelationsExist() {
        Long id = service.add(form("e"));
        Long skuId = newOnShelfSku("E1");

        SupplierSkuReplaceForm replace = new SupplierSkuReplaceForm();
        replace.setSupplierId(id);
        SupplierSkuItemForm item = new SupplierSkuItemForm();
        item.setSkuId(skuId);
        item.setPurchaseUnit("kg");
        replace.setItems(List.of(item));
        skuService.replace(replace);

        SupplierDeleteForm deletion = new SupplierDeleteForm();
        deletion.setSupplierId(id);
        deletion.setVersion(0);
        expectCode(() -> service.delete(deletion), 40947);

        replace.setItems(List.of());
        skuService.replace(replace);

        service.delete(deletion);
        assertThat(jdbc.queryForObject("SELECT deleted FROM supplier WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    @DisplayName("不存在 / 已删除供应商统一 40440")
    void missingSupplierIs40440() {
        expectCode(() -> service.require(-1L), 40440);
        expectCode(() -> service.require(null), 40440);
    }
}
