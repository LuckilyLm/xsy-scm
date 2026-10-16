package com.xsy.scm.mall.address.domain.vo;

import com.xsy.scm.common.constant.ScmGeoCoordinateSystemEnum;
import java.math.BigDecimal;
import lombok.Data;

/** 高德地点搜索与逆地理编码统一返回 GCJ-02 坐标。 */
@Data
public class MallMapLocationVO {
    private String name;
    private String address;
    private Integer provinceCode;
    private String provinceName;
    private Integer cityCode;
    private String cityName;
    private Integer districtCode;
    private String districtName;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String geomCrs = ScmGeoCoordinateSystemEnum.GCJ02.name();
}
