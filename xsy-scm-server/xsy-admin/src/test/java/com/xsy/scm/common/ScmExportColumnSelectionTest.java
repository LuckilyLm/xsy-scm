package com.xsy.scm.common;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.export.ScmExportColumnSelection;
import com.xsy.scm.common.export.ScmExportColumnSelection.Column;
import com.xsy.scm.common.scope.ScmDataScopeException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScmExportColumnSelectionTest {
    private static final List<Column> SCHEMA = List.of(
            new Column("name", "名称"), new Column("quantity", "数量"),
            new Column("cost", "成本", true));

    @Test
    void selectionCanReorderWhitelistedColumnsWithoutChangingDataSource() {
        var selected = ScmExportColumnSelection.resolve(List.of("quantity", "name"), SCHEMA, false);
        assertThat(selected.titles()).containsExactly("数量", "名称");
        assertThat(selected.project(List.of(List.of("小白菜", "10.0000", "未知"))))
                .containsExactly(List.of("10.0000", "小白菜"));
    }

    @Test
    void unauthorizedCostAndUnknownOrDuplicateColumnsFailClosed() {
        assertThatThrownBy(() -> ScmExportColumnSelection.resolve(List.of("cost"), SCHEMA, false))
                .isInstanceOf(ScmDataScopeException.class);
        assertThatThrownBy(() -> ScmExportColumnSelection.resolve(List.of("password"), SCHEMA, true))
                .isInstanceOf(ScmBusinessException.class);
        assertThatThrownBy(() -> ScmExportColumnSelection.resolve(List.of("name", "name"), SCHEMA, true))
                .isInstanceOf(ScmBusinessException.class);
        assertThatThrownBy(() -> ScmExportColumnSelection.resolve(List.of(), SCHEMA, true))
                .isInstanceOf(ScmBusinessException.class);
    }

    @Test
    void legacyDefaultKeepsAllColumnsAndUnknownValuesRemainBlank() {
        var selected = ScmExportColumnSelection.resolve(null, SCHEMA, false);
        assertThat(selected.titles()).containsExactly("名称", "数量", "成本");
        assertThat(selected.project(List.of(java.util.Arrays.asList("小白菜", "10.0000", null))))
                .containsExactly(List.of("小白菜", "10.0000", ""));
    }
}
