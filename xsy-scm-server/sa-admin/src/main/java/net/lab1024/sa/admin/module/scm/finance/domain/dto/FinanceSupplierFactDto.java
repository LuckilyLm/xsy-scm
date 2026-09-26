package net.lab1024.sa.admin.module.scm.finance.domain.dto;

import lombok.Data;

/**
 * 登记付款所需的**供应商事实**，由 {@code FinancePaymentSourceDao} 只读取得。
 *
 * <p>只取存在性与名称：D-5 与 P0 裁决 7 认定供应商主档按采购团队共享读取（无 owner 列），
 * 因此本 DTO 刻意<b>没有</b> seller / purchaser 字段，也不承载任何范围判定。
 * {@code status}（ENABLED / DISABLED）同样不在此读取 —— 已停用供应商能否结清历史债务没有裁决禁止，
 * 服务端不得自行加一条前置把它挡掉。
 */
@Data
public class FinanceSupplierFactDto {

    private Long supplierId;

    /**
     * {@code supplier.name}，登记时冻结为 {@code counterparty_name_snapshot}。
     */
    private String supplierName;
}
