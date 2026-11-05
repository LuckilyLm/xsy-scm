package com.xsy.scm.common;

import com.xsy.scm.common.dao.AppendOnlyMapper;
import java.io.Serializable;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/** 标准Mapper的便捷、批量与软删入口都不能绕过追加记录约束。 */
class AppendOnlyMapperTest {
    @Test
    @SuppressWarnings("unchecked")
    void standardMutationEntrypointsRejectBeforeAnySql() {
        AppendOnlyMapper<Object> mapper = mock(AppendOnlyMapper.class, CALLS_REAL_METHODS);
        Object fact = new Object();
        assertThatThrownBy(() -> mapper.deleteById((Serializable) 1L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapper.deleteById(fact)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapper.deleteById(1L, true)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapper.delete(null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapper.deleteByIds(List.of(1L), true)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapper.updateById(fact)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapper.update(fact, null)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapper.updateById(List.of(fact), 50)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapper.insertOrUpdate(fact)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapper.insertOrUpdate(List.of(fact), 50)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapper.insertOrUpdate(List.of(fact), (session, row) -> true, 50))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
