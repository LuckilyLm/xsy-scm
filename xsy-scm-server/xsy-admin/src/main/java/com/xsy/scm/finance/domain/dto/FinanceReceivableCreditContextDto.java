package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 客户级红字（独立退货，{@code original_receivable_id IS NULL}）对某张应收的抵扣上下文。
 *
 * <p>
 * {@code poolAmount} 是该客户该结算主体下全部未分配红字之和；{@code priorOpenAmount} 是按「事件时间、单据 ID」排序在本单之前的所有正常应收 剩余未结额之和。 本单可被抵扣的上限 =
 * min(本单未结, pool - priorOpen)。
 */
@Data
public class FinanceReceivableCreditContextDto {

    private BigDecimal poolAmount;

    private BigDecimal priorOpenAmount;
}
