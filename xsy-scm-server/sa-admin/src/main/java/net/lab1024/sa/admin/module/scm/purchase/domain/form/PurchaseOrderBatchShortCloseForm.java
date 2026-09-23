package net.lab1024.sa.admin.module.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 批量少收关单（Wave 2B §6.3）。
 *
 * <p>整批共享一个关单原因；{@code orders} 每行携带各自的 {@code version}，被他人改过即显式版本冲突。
 * 服务侧在同一事务内按 id 升序逐单套用与单单少收关单完全相同的状态机与「至少一行已收且一行未收齐」校验，
 * 任一单非法即整批回滚（§6.8：不允许中间部分成功）。
 */
@Data
public class PurchaseOrderBatchShortCloseForm {

    @Valid
    @NotEmpty
    @Size(max = 100)
    private List<PurchaseOrderVersionForm> orders;

    @NotBlank
    @Size(max = 500)
    private String shortCloseReason;
}
