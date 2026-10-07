package com.xsy.scm.warehouse.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建仓库。
 *
 * <p>
 * <b>不含 {@code status}</b>：新建仓库由服务端设为 {@code ENABLED}；状态变更走专用命令。
 *
 * <p>
 * <b>不含 {@code warehouseCode}</b>：仓库编码由服务端生成（{@code ScmBusinessNoService}），客户端传入的值不会被采信。
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
}
