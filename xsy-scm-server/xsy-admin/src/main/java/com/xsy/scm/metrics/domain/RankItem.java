package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;

/**
 * 排行项（客户销售排行 / 商品销售排行共用）。
 *
 * <p>
 * 名称一律取单据上保存的名称列：主数据可能已改名，排行要反映当时的情况。
 */
public record RankItem(String name, BigDecimal amount) {
}
