package com.xsy.scm.supplier.domain.form;

import com.xsy.scm.common.domain.ScmLocationForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新增供应商。
 *
 * <p>
 * <b>刻意不含 {@code status}</b>：新建供应商强制为 {@code ENABLED}，由 Service 显式设置，不接受客户端指定。
 *
 * <p>
 * 继承 {@link ScmLocationForm} 以获得「经纬度与坐标系同时填写或同时清空」的成组校验： 半组坐标在地图上是无法解释的，而 DB 的 {@code ck_supplier_location_complete}
 * 也会拒绝它。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierAddForm extends ScmLocationForm {

    @NotBlank(message = "供应商编码不能为空")
    @Size(max = 64, message = "供应商编码不能超过64个字符")
    private String supplierCode;

    @NotBlank(message = "供应商名称不能为空")
    @Size(max = 150, message = "供应商名称不能超过150个字符")
    private String name;

    @jakarta.validation.constraints.Min(value = 0, message = "付款账期不能小于0天")
    @jakarta.validation.constraints.Max(value = 3650, message = "付款账期不能超过3650天")
    private Integer paymentPeriodDays = 0;

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
