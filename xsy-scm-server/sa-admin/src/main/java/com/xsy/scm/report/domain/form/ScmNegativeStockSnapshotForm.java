package com.xsy.scm.report.domain.form;

import java.time.LocalDate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

public final class ScmNegativeStockSnapshotForm {
    private ScmNegativeStockSnapshotForm() {
    }
    @Data
    public static class Create {
        @NotNull(message = "请选择留存仓库")
        @Positive(message = "仓库编号无效")
        private Long warehouseId;
        @Size(max = 500, message = "备注不能超过500字")
        private String remark;
    }
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Query extends PageParam {
        @NotNull(message = "请选择留存月份")
        private LocalDate month;
        @Positive(message = "仓库编号无效")
        private Long warehouseId;
    }
}
