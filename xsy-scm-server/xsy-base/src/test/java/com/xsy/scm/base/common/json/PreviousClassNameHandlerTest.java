package com.xsy.scm.base.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.module.support.job.repository.domain.XsyJobEntity;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 包名调整后仍可读取已保存的分页、任务及数组缓存，不接收未知旧类型。 */
class PreviousClassNameHandlerTest {
    private ObjectMapper mapper() {
        return new ObjectMapper().addHandler(new PreviousClassNameHandler())
            .activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
    }

    @Test
    void readsPreviousPackageWithOriginalValues() throws Exception {
        var page = new PageResult<>();
        page.setTotal(7L);
        String previous = mapper().writeValueAsString(page)
            .replace("com.xsy.scm.base.", "net.lab1024.sa.base.");
        Object value = mapper().readValue(previous, Object.class);
        assertThat(value).isInstanceOf(PageResult.class);
        assertThat(((PageResult<?>) value).getTotal()).isEqualTo(7L);
    }

    @Test
    void readsPreviousClassNameAndArray() throws Exception {
        String job = mapper().writeValueAsString(new XsyJobEntity())
            .replace(XsyJobEntity.class.getName(),
                "net.lab1024.sa.base.module.support.job.repository.domain.SmartJobEntity");
        assertThat(mapper().readValue(job, Object.class)).isInstanceOf(XsyJobEntity.class);
        String array = mapper().writeValueAsString(new PageResult<?>[] {new PageResult<>()})
            .replace("com.xsy.scm.base.", "net.lab1024.sa.base.");
        assertThat(mapper().readValue(array, Object.class)).isInstanceOf(PageResult[].class);
    }

    @Test
    void unknownPreviousTypeStillFails() {
        assertThatThrownBy(() -> mapper().readValue("[\"net.lab1024.sa.base.MissingType\",{}]", Object.class))
            .isInstanceOf(InvalidTypeIdException.class);
    }
}
