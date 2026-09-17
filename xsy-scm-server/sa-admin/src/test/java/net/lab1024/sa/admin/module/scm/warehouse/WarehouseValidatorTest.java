package net.lab1024.sa.admin.module.scm.warehouse;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseAddForm;
import net.lab1024.sa.admin.module.scm.warehouse.manager.WarehouseValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 仓库单条业务规则测试（W5 Target Design §11.1）。
 *
 * <p>纯函数，无 Spring、无 DB。「编码重复」不在这里 —— 它由唯一索引 + Service 查重共同保证，
 * 属 {@code PurchaseWarehouseIT} 的覆盖范围。
 */
class WarehouseValidatorTest {

    @Test
    @DisplayName("编码归一化：去空白 + 转大写；空值透传")
    void normalizesCode() {
        assertThat(WarehouseValidator.normalizeCode("  wh001  ")).isEqualTo("WH001");
        assertThat(WarehouseValidator.normalizeCode("Wh-001")).isEqualTo("WH-001");
        assertThat(WarehouseValidator.normalizeCode(null)).isNull();
        assertThat(WarehouseValidator.normalizeCode("   ")).isEmpty();
    }

    @Test
    @DisplayName("名称归一化：只去首尾空白，不改大小写")
    void normalizesName() {
        assertThat(WarehouseValidator.normalizeName("  默认仓库 ")).isEqualTo("默认仓库");
        assertThat(WarehouseValidator.normalizeName("Main WH")).isEqualTo("Main WH");
        assertThat(WarehouseValidator.normalizeName(null)).isNull();
    }

    @Test
    @DisplayName("状态取值域：ENABLED / DISABLED 通过，其余与 null 拒绝")
    void validatesStatusDomain() {
        WarehouseValidator.validateStatus("ENABLED");
        WarehouseValidator.validateStatus("DISABLED");
        assertThatThrownBy(() -> WarehouseValidator.validateStatus("BROKEN"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(VALIDATION_ERROR.getCode()));
        assertThatThrownBy(() -> WarehouseValidator.validateStatus(null))
                .isInstanceOf(ScmBusinessException.class);
        assertThatThrownBy(() -> WarehouseValidator.validateStatus("enabled"))
                .as("取值域大小写敏感：小写不是合法枚举名")
                .isInstanceOf(ScmBusinessException.class);
    }

    @Test
    @DisplayName("必填校验：编码 / 名称缺失或空白 → 40000；非法状态 → 40000")
    void validatesRequiredFields() {
        assertThatThrownBy(() -> WarehouseValidator.validateRequired(null))
                .isInstanceOf(ScmBusinessException.class);

        WarehouseAddForm form = new WarehouseAddForm();
        assertThatThrownBy(() -> WarehouseValidator.validateRequired(form))
                .isInstanceOf(ScmBusinessException.class);

        form.setWarehouseCode("WH-X");
        assertThatThrownBy(() -> WarehouseValidator.validateRequired(form))
                .as("名称缺失")
                .isInstanceOf(ScmBusinessException.class);

        form.setName("   ");
        assertThatThrownBy(() -> WarehouseValidator.validateRequired(form))
                .as("名称全空白")
                .isInstanceOf(ScmBusinessException.class);

        form.setName("仓");
        WarehouseValidator.validateRequired(form);
    }
}
