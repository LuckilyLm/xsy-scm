package com.xsy.scm.preference.filter;

import com.xsy.scm.common.filter.ScmAdvancedFilterCatalog.Condition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

public final class UserFilterPresetModel {
    private UserFilterPresetModel() {}

    @Data
    public static class View {
        @NotBlank(message = "筛选页面不能为空")
        private String viewKey;
    }

    @Data
    public static class Create extends View {
        @NotBlank(message = "方案名称不能为空")
        @Size(max = 50, message = "方案名称最多50个字符")
        private String name;
        @NotEmpty(message = "至少保存一个筛选条件")
        @Size(max = 8, message = "最多保存8个筛选条件")
        private List<@NotNull(message = "筛选条件不能包含空项") @Valid Condition> conditions;
    }

    @Data
    public static class Remove extends View {
        @NotNull(message = "筛选方案不能为空")
        private Long id;
    }

    @Data
    public static class Row {
        private Long id;
        private String name;
        private String conditionsJson;
        private OffsetDateTime createdAt;
    }

    public record Preset(Long id, String name, List<Condition> conditions,
            boolean available, OffsetDateTime createdAt) {}
}
