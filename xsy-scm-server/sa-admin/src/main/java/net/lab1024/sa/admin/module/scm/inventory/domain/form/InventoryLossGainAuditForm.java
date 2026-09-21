package net.lab1024.sa.admin.module.scm.inventory.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批 / 驳回报损报溢单（两个动作共用一个表单，与参考项目一致）。
 *
 * <p><b>{@code version} 必填并参与乐观锁校验</b>：审批人看到的内容必须与审批的内容一致。
 * 若在「打开单据 → 点审批」之间录单人改了明细，{@code version} 已经前进，
 * 审批会以 40921 失败并要求刷新 —— 否则审批人可能批准了一个自己没看过的数量，
 * 而这正是审批制度要防的事。
 *
 * <p>{@code auditOpinion} 在驳回时**必填**（服务层校验，见 41037）：
 * 驳回是唯一会把「为什么不行」传达给录单人的渠道。
 */
@Data
public class InventoryLossGainAuditForm {

    /**
     * 审批人看到的单据版本号（{@code InventoryLossGainVO.version}）。
     *
     * <p>允许为 0（新建单据的初始版本），因此用 {@code @Min(0)} 而不是 {@code @Positive}。
     */
    @NotNull
    @Min(0)
    private Integer version;

    /**
     * 审核意见（驳回时必填）。
     */
    @Size(max = 500)
    private String auditOpinion;
}
