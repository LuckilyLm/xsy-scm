package com.xsy.scm.finance.domain.dto;

import lombok.Data;

/**
 * 登记付款所需的<b>供应商事实</b>，由 {@code FinancePaymentSourceDao} 只读取得。
 *
 * <p>
 * 只取存在性与名称：供应商主档按采购团队共享读取（无 owner 列），因此本 DTO 刻意<b>没有</b> seller / purchaser 字段，也不承载任何范围判定。 {@code status}（ENABLED /
 * DISABLED）不在此读取；停用供应商仍可能需要结清历史债务。
 */
@Data
public class FinanceSupplierFactDto {

    private Long supplierId;

    /**
     * {@code supplier.name}，登记时冻结为 {@code counterparty_name_snapshot}。
     */
    private String supplierName;
}
