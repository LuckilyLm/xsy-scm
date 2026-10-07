package com.xsy.scm.metrics.domain;

import java.math.BigDecimal;

/**
 * 排行项（客户销售排行 / 商品销售排行共用）。
 *
 * <p>
 * 名称一律取单据上的快照列：历史单据对应的主数据可能已改名，排行要忠于当时。
 */
public record RankItem(String name, BigDecimal amount) {
}
