package net.lab1024.sa.admin.module.scm.customer;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerTypeAddForm;
import net.lab1024.sa.admin.module.scm.customer.manager.CustomerTypeValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 客户类型校验器契约测试（纯静态，无依赖）。
 */
class CustomerTypeValidatorTest {

    private static int codeOf(Throwable t) {
        return ((ScmBusinessException) t).getErrorCode().getCode();
    }

    @Test
    @DisplayName("编码归一化：trim + upper")
    void normalizesCode() {
        assertThat(CustomerTypeValidator.normalizeCode("  enterprise ")).isEqualTo("ENTERPRISE");
        assertThat(CustomerTypeValidator.normalizeCode(null)).isNull();
    }

    @Test
    @DisplayName("名称归一化：仅 trim")
    void normalizesName() {
        assertThat(CustomerTypeValidator.normalizeName("  企业  ")).isEqualTo("企业");
    }

    @Test
    @DisplayName("状态取值域：仅 ENABLED / DISABLED")
    void validatesStatusDomain() {
        assertThatCode(() -> CustomerTypeValidator.validateStatus("ENABLED")).doesNotThrowAnyException();
        assertThatCode(() -> CustomerTypeValidator.validateStatus("DISABLED")).doesNotThrowAnyException();
        assertThatThrownBy(() -> CustomerTypeValidator.validateStatus("POTENTIAL"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
        assertThatThrownBy(() -> CustomerTypeValidator.validateStatus(null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
    }

    @Test
    @DisplayName("必填校验：编码 / 名称不能为空或纯空白")
    void validatesRequiredFields() {
        assertThatCode(() -> CustomerTypeValidator.validateRequired(form("ENTERPRISE", "企业", "ENABLED")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> CustomerTypeValidator.validateRequired(form("   ", "企业", "ENABLED")))
                .isInstanceOf(ScmBusinessException.class);
        assertThatThrownBy(() -> CustomerTypeValidator.validateRequired(form("ENTERPRISE", "  ", "ENABLED")))
                .isInstanceOf(ScmBusinessException.class);
        assertThatThrownBy(() -> CustomerTypeValidator.validateRequired(form("ENTERPRISE", "企业", "NOPE")))
                .isInstanceOf(ScmBusinessException.class);
    }

    @Test
    @DisplayName("名称允许重复：不做重名检查（legacy 不变量 T2）")
    void duplicateNameIsAllowed() {
        assertThatCode(() -> CustomerTypeValidator.validateRequired(form("A", "同名", "ENABLED")))
                .doesNotThrowAnyException();
        assertThatCode(() -> CustomerTypeValidator.validateRequired(form("B", "同名", "ENABLED")))
                .doesNotThrowAnyException();
    }

    private static CustomerTypeAddForm form(String code, String name, String status) {
        CustomerTypeAddForm form = new CustomerTypeAddForm();
        form.setTypeCode(code);
        form.setName(name);
        form.setStatus(status);
        return form;
    }
}
