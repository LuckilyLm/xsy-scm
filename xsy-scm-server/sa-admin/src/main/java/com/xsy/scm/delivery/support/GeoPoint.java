package com.xsy.scm.delivery.support;

import java.math.BigDecimal;

/**
 * 距离矩阵的输入点。
 *
 * <p>
 * 只有坐标与坐标系：矩阵 provider 不需要知道这是仓库还是客户 —— 名称、单号那些是<b>展示</b>信息，由建议快照自己带着，掺进 provider 契约只会让可替换实现被迫认识业务字段。
 *
 * <p>
 * {@code geomCrs} 在矩阵内所有点必须一致，否则距离没有意义。
 */
public record GeoPoint(BigDecimal longitude, BigDecimal latitude, String geomCrs) {
}
