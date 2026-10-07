package com.xsy.scm.warehouse;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;
import com.xsy.scm.warehouse.manager.WarehouseValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 仓库单条业务规则测试。
 *
 * <p>纯函数，无 Spring、无 DB。编码由服务端生成（{@code ScmBusinessNoService}），不参与表单校验；
 * 本类只覆盖名称归一化、名称必填与状态取值域三条规则。
 */
class WarehouseValidatorTest {

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
    @DisplayName("名称必填：null 表单 / 名称缺失或全空白 → 40000；名称存在即通过")
    void validatesRequiredName() {
        assertThatThrownBy(() -> WarehouseValidator.validateRequired(null))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(VALIDATION_ERROR.getCode()));

        WarehouseAddForm form = new WarehouseAddForm();
        assertThatThrownBy(() -> WarehouseValidator.validateRequired(form))
                .as("名称缺失")
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(VALIDATION_ERROR.getCode()));

        form.setName("   ");
        assertThatThrownBy(() -> WarehouseValidator.validateRequired(form))
                .as("名称全空白")
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(VALIDATION_ERROR.getCode()));

        form.setName("仓");
        WarehouseValidator.validateRequired(form);
    }
}
