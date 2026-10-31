package com.xsy.scm.common.domain;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户与供应商共用的往来方联系信息：联系人、电话、地址、行政区划与备注。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmPartyForm extends ScmLocationForm {

    @Size(max = 100, message = "联系人姓名不能超过100个字符")
    private String contactName;

    @Size(max = 32, message = "联系电话不能超过32个字符")
    private String contactPhone;

    @Size(max = 255, message = "联系地址不能超过255个字符")
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
