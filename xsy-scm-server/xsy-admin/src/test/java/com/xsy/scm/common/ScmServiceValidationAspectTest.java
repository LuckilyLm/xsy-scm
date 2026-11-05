package com.xsy.scm.common;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.validation.ScmServiceValidationAspect;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import jakarta.validation.Validation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证内部服务调用同样拒绝无效枚举和数值，错误发生在任何业务写入之前。 */
class ScmServiceValidationAspectTest {
    @Test
    void invalidEnumAndAmountNeverReachBusinessCode() throws Throwable {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var aspect = new ScmServiceValidationAspect(factory.getValidator());
            var invocation = mock(ProceedingJoinPoint.class);
            CustomerAddForm form = new CustomerAddForm();
            form.setName("客户");
            form.setStatus("UNKNOWN");
            when(invocation.getArgs()).thenReturn(new Object[]{form});
            assertThatThrownBy(() -> aspect.validate(invocation)).isInstanceOf(ScmBusinessException.class)
                .hasMessageContaining("客户状态无效");
            form.setStatus(null);
            form.setCreditPeriodValue(-1);
            assertThatThrownBy(() -> aspect.validate(invocation)).isInstanceOf(ScmBusinessException.class)
                .hasMessageContaining("账期值");
            verify(invocation, never()).proceed();
        }
    }

    @Test
    void validFormAndOptionalNullContinueUnchanged() throws Throwable {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var aspect = new ScmServiceValidationAspect(factory.getValidator());
            var invocation = mock(ProceedingJoinPoint.class);
            CustomerAddForm form = new CustomerAddForm();
            form.setName("客户");
            when(invocation.getArgs()).thenReturn(new Object[]{form, null, "业务操作者"});
            when(invocation.proceed()).thenReturn(123L);
            assertThat(aspect.validate(invocation)).isEqualTo(123L);
        }
    }
}
