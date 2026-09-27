package com.xsy.scm.common.domain;

import lombok.Data;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xsy.scm.common.constant.ScmGeoCoordinateSystemEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

import java.math.BigDecimal;

@Data
public class ScmLocationForm {
    @DecimalMin(value = "-180", message = "经度必须在-180至180之间")
    @DecimalMax(value = "180", message = "经度必须在-180至180之间")
    @Digits(integer = 3, fraction = 8, message = "经度最多3位整数和8位小数")
    private BigDecimal longitude;
    @DecimalMin(value = "-90", message = "纬度必须在-90至90之间")
    @DecimalMax(value = "90", message = "纬度必须在-90至90之间")
    @Digits(integer = 2, fraction = 8, message = "纬度最多2位整数和8位小数")
    private BigDecimal latitude;
    @ScmEnumValue(enumClass = ScmGeoCoordinateSystemEnum.class, message = "坐标系必须为 GCJ02 或 WGS84")
    private String geomCrs;

    @JsonIgnore
    @AssertTrue(message = "经纬度和坐标系必须同时填写或同时清空")
    public boolean isLocationComplete() {
        return longitude == null && latitude == null && geomCrs == null
                || longitude != null && latitude != null
                && (ScmGeoCoordinateSystemEnum.GCJ02.name().equals(geomCrs)
                || ScmGeoCoordinateSystemEnum.WGS84.name().equals(geomCrs));
    }
}
