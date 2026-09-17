package net.lab1024.sa.admin.module.scm.warehouse;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.warehouse.constant.ScmWarehouseStatusEnum;
import net.lab1024.sa.admin.module.scm.warehouse.constant.WarehouseErrorCode;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseUpdateForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 仓库域 PostgreSQL 集成测试（W5 Target Design §11.2）。
 *
 * <p>覆盖：V15 种子、编码唯一（部分唯一索引 + 归一化）、停用语义与 {@code ck_warehouse_status}。
 *
 * <p><b>第 4 个用例「停用后不能用于新采购单」在 T12 补入</b>：它需要 {@code PurchaseOrderService}
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
    @DisplayName("编码唯一：归一化后重复 → 40996；跨仓库重复 → 40996；过期版本 → 40921")
    void enforcesUniqueCodeAndOptimisticLock() {
        String code = prefix + "-UNIQ";
        Long first = warehouseService.create(addForm(code, "一号仓"));

        // 同码再建
        expectCode(() -> warehouseService.create(addForm(code, "二号仓")),
                WarehouseErrorCode.WAREHOUSE_CODE_DUPLICATE.getCode());
        // 大小写不同但归一化后同码（normalizeCode 会 trim + upper）
        expectCode(() -> warehouseService.create(addForm(code.toLowerCase(), "三号仓")),
                WarehouseErrorCode.WAREHOUSE_CODE_DUPLICATE.getCode());

        // 编辑到已被占用的编码
        Long second = warehouseService.create(addForm(prefix + "-OTHER", "四号仓"));
        WarehouseUpdateForm clash = updateForm(second, code, "四号仓", 0);
        expectCode(() -> warehouseService.update(clash),
                WarehouseErrorCode.WAREHOUSE_CODE_DUPLICATE.getCode());

        // 过期版本
        WarehouseUpdateForm stale = updateForm(first, code, "一号仓改名", 99);
        expectCode(() -> warehouseService.update(stale), VERSION_CONFLICT.getCode());

        // 正常编辑成功
        warehouseService.update(updateForm(first, code, "一号仓改名", 0));
        assertThat(jdbc.queryForObject("SELECT name FROM warehouse WHERE id = ?", String.class, first))
                .isEqualTo("一号仓改名");
        assertThat(jdbc.queryForObject("SELECT version FROM warehouse WHERE id = ?", Integer.class, first))
                .isEqualTo(1);
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

    private static net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseAddForm addForm(
            String code, String name) {
        var form = new net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseAddForm();
        form.setWarehouseCode(code);
        form.setName(name);
        return form;
    }

    private static WarehouseUpdateForm updateForm(Long id, String code, String name, Integer version) {
        var form = new WarehouseUpdateForm();
        form.setId(id);
        form.setWarehouseCode(code);
        form.setName(name);
        form.setVersion(version);
        return form;
    }
}
