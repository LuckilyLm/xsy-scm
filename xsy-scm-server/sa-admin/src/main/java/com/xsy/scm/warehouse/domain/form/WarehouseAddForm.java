package com.xsy.scm.warehouse.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建仓库时，编码由服务端生成、状态默认启用。状态变更必须使用专用操作。
 */
@Data
public class WarehouseAddForm extends com.xsy.scm.common.domain.ScmLocationForm {

    @NotBlank(message = "仓库名称不能为空")
    @Size(max = 150, message = "仓库名称不能超过150个字符")
    private String name;

    @Size(max = 255, message = "仓库地址不能超过255个字符")
    private String address;

    @Positive(message = "省份编码必须大于0")
    private Integer provinceCode;

    @Size(max = 32, message = "省份名称不能超过32个字符")
    private String provinceName;

    @Positive(message = "城市编码必须大于0")
    private Integer cityCode;

    @Size(max = 64, message = "城市名称不能超过64个字符")
    private String cityName;

    @Positive(message = "区县编码必须大于0")
    private Integer districtCode;

    @Size(max = 64, message = "区县名称不能超过64个字符")
    private String districtName;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    /** 是否启用批次追踪；只在新建时设置，编辑时不可改。 */
    private Boolean lotTrackingEnabled;
}
