package net.lab1024.sa.admin.module.scm.warehouse.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建仓库（W5 Target Design §7.2）。
 *
 * <p>**不含 `status`**：按设计 §7.2 的字面字段清单，新建仓库一律为 {@code ENABLED}；
 * 设计把 `scm:warehouse:add` 定义为「保留但不授予业务角色」的端点（G-03 单仓库）。
 * 状态写入路径的缺口见验收报告 G1。
 */
@Data
public class WarehouseAddForm extends net.lab1024.sa.admin.module.scm.common.domain.ScmLocationForm {

    @NotBlank
    @Size(max = 64)
    private String warehouseCode;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 255)
    private String address;

    @Positive
    private Integer provinceCode;

    @Size(max = 32)
    private String provinceName;

    @Positive
    private Integer cityCode;

    @Size(max = 64)
    private String cityName;

    @Positive
    private Integer districtCode;

    @Size(max = 64)
    private String districtName;

    @Size(max = 500)
    private String remark;
}
