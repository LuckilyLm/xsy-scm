package com.xsy.scm.delivery.support;

import java.math.BigDecimal;

/** 距离计算只需要坐标与坐标系，名称和业务单号由展示层处理。同一距离矩阵中的坐标系必须一致。 */
public record GeoPoint(BigDecimal longitude, BigDecimal latitude, String geomCrs) {
}
