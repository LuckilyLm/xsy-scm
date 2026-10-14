package com.xsy.scm.mall.order.constant;

/**
 * 集团共享结算的商城准入策略。
 *
 * <p>
 * <b>这是「成员能否用集团共享资金下单」的唯一授权判定点。</b> 共享钱包里的钱属于集团结算主体， 未经授权就让成员代扣，等于花掉别家主体的资金，因此本策略与「金额可见性」分开：
 * 金额在共享结算下一律不展示（{@code CustomerValidator.sharedSettlement}）， 而「能不能用」由这里统一回答。
 *
 * <p>
 * 当前恒为 {@code false}：系统还没有「成员可用共享余额 / 共享账期」的授权模型（客户级开关或权限项）。 要放开时，请在此接入正式授权（例如客户级开关 + 后台审批留痕），
 * <b>不要在结算服务里直接读余额或授信判断</b>，也不要在前端放行 —— 否则同一规则会散落到多处。
 */
public final class ScmSharedSettlementPolicy {

    private ScmSharedSettlementPolicy() {
    }

    /**
     * 是否允许客户在商城直接使用集团共享结算资金（共享钱包 / 集团账期）下单。
     *
     * @return 当前恒为 {@code false}，即共享结算下商城只记录约定、不代扣共享资金
     */
    public static boolean sharedCheckoutAllowed() {
        return false;
    }
}
