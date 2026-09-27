package com.xsy.scm.warehouse.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建仓库。
 *
 * <p><b>不含 {@code status}</b>：新建仓库由服务端设为 {@code ENABLED}；状态变更走专用命令。
 */
@Data
public class WarehouseAddForm extends com.xsy.scm.common.domain.ScmLocationForm {

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
