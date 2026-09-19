package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 报损报溢单的调整类型。
 *
 * <p>取值与参考项目 `t_stock_adjust.adjust_type` 的「报损 / 报溢」一致（参考里另有
 * 「盘点调整」「规格转换」，本波次不纳入：V2 的盘点是独立模块，规格转换尚未开发）。
 *
 * <p><b>方向是单据级属性，不是行级属性</b>：一张单据要么全是报损、要么全是报溢。
 * 这样行上的 {@code quantity} 恒为正，方向只有一个来源，不可能出现
 * 「同一张单里两行方向相反」这种既难校验又难展示的状态。
 *
 * <p><b>与流水类型的映射写在本枚举里</b>（{@link #movementType()}）：
 * 方向、流水类型、DB 白名单三者的对应关系只能有一个来源，否则新增类型时必然漏掉一处。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryLossGainTypeEnum {

    /** 报损：损耗 / 变质 / 破损 / 丢失，减少库存。 */
    LOSS("报损", ScmInventoryMovementTypeEnum.LOSS_REPORT),

    /** 报溢：溢余（多出来的货），增加库存。 */
    OVERFLOW("报溢", ScmInventoryMovementTypeEnum.GAIN_REPORT);

    private final String desc;

    /** 审批通过时写入 {@code inventory_movement.movement_type} 的值。 */
    private final ScmInventoryMovementTypeEnum movementType;

    /** 方向：{@code true} = 入（余额增加），{@code false} = 出。 */
    public boolean isInbound() {
        return movementType.isInbound();
    }

    /** 该值是否允许写入 {@code inventory_loss_gain.adjust_type}（DB CHECK 白名单的同源判定）。 */
    public static boolean isSupported(String value) {
        for (ScmInventoryLossGainTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** 按持久化值取枚举；未知值返回 {@code null}（调用方自行判定为参数错误）。 */
    public static ScmInventoryLossGainTypeEnum of(String value) {
        for (ScmInventoryLossGainTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
