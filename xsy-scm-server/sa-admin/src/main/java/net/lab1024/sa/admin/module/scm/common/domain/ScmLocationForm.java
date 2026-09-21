package net.lab1024.sa.admin.module.scm.common.domain;

import lombok.Data;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;

@Data
public class ScmLocationForm {
    @DecimalMin("-180")
    @DecimalMax("180")
    @Digits(integer = 3, fraction = 8)
    private BigDecimal longitude;
    @DecimalMin("-90")
    @DecimalMax("90")
    @Digits(integer = 2, fraction = 8)
    private BigDecimal latitude;
    @Pattern(regexp = "GCJ02|WGS84")
    private String geomCrs;

    @JsonIgnore
    @AssertTrue(message = "经纬度和坐标系必须同时填写或同时清空")
    public boolean isLocationComplete() {
        return longitude == null && latitude == null && geomCrs == null
                || longitude != null && latitude != null && ("GCJ02".equals(geomCrs) || "WGS84".equals(geomCrs));
    }
}
