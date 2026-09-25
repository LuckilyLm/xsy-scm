package net.lab1024.sa.admin.module.scm.finance.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 应收 / 应付的方向：{@code NORMAL} 正常事实，{@code RED} 红字（冲减）事实。
 *
 * <p><b>方向编码在类型里，金额恒为正</b>（全局不变量 7，照 {@code inventory_movement} 的纪律）：
 * 红字不是负数金额，而是另一条 {@code RED} 行，净值在读时按方向相减。
 * 因此库里没有 {@code direction} 列，也不可能出现「金额为负的红字」这种自相矛盾的行。
 *
 * <p>红字是**另一张单**，不是原单上的一列调整：不修改、不删除、不软删原事实（全局不变量 1）。
 *
 * <p>收付款与核销用的是另一套 {@link ScmFinanceReverseEntryTypeEnum}（{@code NORMAL / REVERSE}），
 * 两者刻意不合并成一个枚举 —— 「红冲一张单据」与「反向一条登记/分配」是不同的业务动作，
 * 合并后 DB CHECK 就没法分别约束各自的配对规则。
 */
@Getter
@RequiredArgsConstructor
public enum ScmFinanceEntryTypeEnum {

    /**
     * 正常事实：应收来自已签收订单，应付来自已确认收货单。
     */
    NORMAL("正常"),

    /**
     * 红字事实：应收来自已批准退货（自动），应付来自手工登记。必须引用原单并填原因。
     */
    RED("红字");

    private final String desc;
}
