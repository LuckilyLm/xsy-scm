package com.xsy.scm.inventory.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批 / 驳回规格转换单（两个动作共用一个表单，与报损报溢一致）。
 *
 * <p><b>{@code version} 必填并参与乐观锁校验</b>：审批人必须批准自己读到的内容。
 * 若在「打开单据 → 点审批」之间录单人改了折算关系（那是**金额相关**的改动），
 * 版本已经前进，审批以 40921 失败并要求刷新。
 *
 * <p>{@code auditOpinion} 在驳回时**必填**（服务层校验，41063）。
 */
@Data
public class InventoryConversionAuditForm {

    /**
     * 审批人看到的单据版本号（{@code InventoryConversionVO.version}）。
     */
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;

    /**
     * 审核意见（驳回时必填）。
     */
    @Size(max = 500, message = "审核意见不能超过500个字符")
    private String auditOpinion;
}
