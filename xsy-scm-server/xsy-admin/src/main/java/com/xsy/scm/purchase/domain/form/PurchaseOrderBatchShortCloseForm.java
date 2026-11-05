package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 批量少收关单。
 *
 * <p>
 * 整批共享一个关单原因；每行携带各自的 {@code version}，被他人改过即报版本冲突。服务端在同一事务内按 id 升序 逐单执行与单张相同的校验，任一单非法即整批回滚。
 */
@Data
public class PurchaseOrderBatchShortCloseForm {

    @Valid
    @NotEmpty(message = "采购单列表不能为空")
    @Size(max = 100, message = "采购单列表不能超过100项")
    private List<PurchaseOrderVersionForm> orders;

    @NotBlank(message = "少收关单原因不能为空")
    @Size(max = 500, message = "少收关单原因不能超过500个字符")
    private String shortCloseReason;
}
