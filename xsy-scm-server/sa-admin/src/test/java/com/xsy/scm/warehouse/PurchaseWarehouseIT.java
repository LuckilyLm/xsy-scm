package com.xsy.scm.warehouse;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.warehouse.constant.ScmWarehouseStatusEnum;
import com.xsy.scm.warehouse.domain.form.WarehouseUpdateForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 仓库域 PostgreSQL 集成测试。
 *
 * <p>覆盖：V15 种子、服务端编码生成（{@code WH######}）与不重号、停用语义与 {@code ck_warehouse_status}。
 *
 * <p><b>「停用后不能用于新采购单」这一用例</b>需要 {@code PurchaseOrderService}
 * 与 {@code PurchaseWarehouseReferenceGuard} 同时存在，属采购侧规则（40987）。
 */
class PurchaseWarehouseIT extends ScmW5PgITBase {

    @Test
    @DisplayName("V15 播种 WH001 且为 ENABLED，仓库表只有这一行种子")
    void seedsSingleEnabledWarehouse() {
        Long id = seedWarehouseId();
        assertThat(id).isNotNull();

        String status = jdbc.queryForObject(
                "SELECT status FROM warehouse WHERE id = ?", String.class, id);
        assertThat(status).isEqualTo(ScmWarehouseStatusEnum.ENABLED.name());
        assertThat(warehouseService.enabled(id)).isTrue();

        // G-03 单仓库口径：种子表达「系统仅维护一个启用仓库」
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM warehouse WHERE warehouse_code = ? AND deleted = FALSE",
                Long.class, SEED_WAREHOUSE_CODE)).isEqualTo(1L);
    }

    @Test
    @DisplayName("编码由服务端生成（WH######）：同名两次创建都成功且不重号；编辑不触碰编码；过期版本 → 40921")
    void generatesUniqueCodesAndEnforcesOptimisticLock() {
        Long first = warehouseService.create(addForm("一号仓"));
        Long second = warehouseService.create(addForm("一号仓"));

        String firstCode = warehouseCode(first);
        String secondCode = warehouseCode(second);
        assertThat(firstCode).matches("^WH\\d{6,}$");
        assertThat(secondCode).matches("^WH\\d{6,}$").isNotEqualTo(firstCode);

        // 过期版本
        WarehouseUpdateForm stale = updateForm(first, "一号仓改名", 99);
        expectCode(() -> warehouseService.update(stale), VERSION_CONFLICT.getCode());

        // 正常编辑成功；编码不可改，服务端不读写表单里的编码
        warehouseService.update(updateForm(first, "一号仓改名", 0));
        assertThat(jdbc.queryForObject("SELECT name FROM warehouse WHERE id = ?", String.class, first))
                .isEqualTo("一号仓改名");
        assertThat(jdbc.queryForObject("SELECT version FROM warehouse WHERE id = ?", Integer.class, first))
                .isEqualTo(1);
        assertThat(warehouseCode(first)).as("编辑不触碰编码").isEqualTo(firstCode);
    }

    @Test
    @DisplayName("停用：enabled 为 false 但仍可读；非法状态被 ck_warehouse_status 拒绝")
    void disableKeepsRowReadableAndRejectsIllegalStatus() {
        Long id = newWarehouse("DIS");
        assertThat(warehouseService.enabled(id)).isTrue();

        disableWarehouse(id);
        assertThat(warehouseService.enabled(id)).isFalse();
        // 停用后仍然可读（管理页要能看到它），不是 404
        assertThat(warehouseService.require(id).getStatus())
                .isEqualTo(ScmWarehouseStatusEnum.DISABLED.name());

        // 取值域由 DB CHECK 兜住，不只靠 Java 枚举
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE warehouse SET status = 'BROKEN' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------

    private String warehouseCode(Long id) {
        return jdbc.queryForObject("SELECT warehouse_code FROM warehouse WHERE id = ?", String.class, id);
    }

    private static com.xsy.scm.warehouse.domain.form.WarehouseAddForm addForm(String name) {
        var form = new com.xsy.scm.warehouse.domain.form.WarehouseAddForm();
        form.setName(name);
        return form;
    }

    private static WarehouseUpdateForm updateForm(Long id, String name, Integer version) {
        var form = new WarehouseUpdateForm();
        form.setId(id);
        form.setName(name);
        form.setVersion(version);
        return form;
    }
}
