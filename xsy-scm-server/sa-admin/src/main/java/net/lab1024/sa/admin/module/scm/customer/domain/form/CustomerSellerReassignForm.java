package net.lab1024.sa.admin.module.scm.customer.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 客户业务归属改派（{@code scm:customer:assign}）。
 *
 * <p>改派是独立的端点而不是普通编辑的一部分：{@code /scm/customer/update} 一律不触碰
 * {@code seller_id}（见 {@code CustomerService#update}），否则任何能编辑客户的人都可以
 * 通过在表单里回传一个 {@code sellerId} 把客户挪出或挪进别人的可见范围，行级范围就不成立。
 *
 * <p>{@code sellerId} 允许为空，语义是<b>收回为未分配</b>：未分配客户只有持分配权或全量范围的人
 * 能读到，这正是裁决第 6 条定义的中间态，因此它必须是可显式落回的状态。
 */
@Data
public class CustomerSellerReassignForm {

    @NotNull
    @Positive
    private Long customerId;

    /**
     * 新负责人（员工 id）；{@code null} 表示收回为未分配。
     */
    @Positive
    private Long sellerId;

    /**
     * 乐观锁版本：改派必须针对自己刚看到的那一行，不能覆盖别人的并发编辑。
     */
    @NotNull
    @Min(0)
    private Integer version;
}
