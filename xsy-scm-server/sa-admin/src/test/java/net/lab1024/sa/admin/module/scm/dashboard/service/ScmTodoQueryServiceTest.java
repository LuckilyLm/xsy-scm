package net.lab1024.sa.admin.module.scm.dashboard.service;

import net.lab1024.sa.admin.module.scm.dashboard.domain.vo.ScmTodoVO;
import net.lab1024.sa.admin.module.scm.delivery.service.DeliveryRouteQueryService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryLossGainQueryService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryWarningQueryService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseQueryService;
import net.lab1024.sa.admin.module.system.login.manager.LoginManager;
import net.lab1024.sa.base.common.domain.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 待办卡片门禁的纯逻辑单测（无库）：验证「待办入口权限不隐含领域权限」——
 * 无权卡片整卡省略且从不触发领域计数，有权零任务才返回 0，报损报溢审批权限按 any-of 判定。
 */
@DisplayName("业务待办卡片门禁（unit）")
class ScmTodoQueryServiceTest {

    private final LoginManager loginManager = mock(LoginManager.class);
    private final InventoryWarningQueryService warning = mock(InventoryWarningQueryService.class);
    private final PurchaseQueryService purchase = mock(PurchaseQueryService.class);
    private final InventoryLossGainQueryService lossGain = mock(InventoryLossGainQueryService.class);
    private final DeliveryRouteQueryService delivery = mock(DeliveryRouteQueryService.class);

    private final ScmTodoQueryService service =
            new ScmTodoQueryService(loginManager, warning, purchase, lossGain, delivery);

    private static <T> PageResult<T> withTotal(long total) {
        PageResult<T> p = new PageResult<>();
        p.setTotal(total);
        return p;
    }

    private static Set<String> keys(List<ScmTodoVO> todos) {
        return todos.stream().map(ScmTodoVO::getKey).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("仅待办入口权限：不返回任何领域卡片，也从不触发领域计数")
    void todoOnlyPermissionOmitsAllCardsAndNeverCounts() {
        List<ScmTodoVO> todos = service.todosFor(List.of("scm:todo:query"));

        assertThat(todos).isEmpty();
        verifyNoInteractions(warning, purchase, lossGain, delivery);
    }

    @Test
    @DisplayName("持齐领域权限：四张卡片齐全，各计数服务被调用一次")
    void fullPermissionsYieldAllFourCards() {
        when(warning.queryWarningPage(any())).thenReturn(withTotal(2));
        when(purchase.receiptQuery(any())).thenReturn(withTotal(3));
        when(lossGain.queryPage(any())).thenReturn(withTotal(1));
        when(delivery.query(any())).thenReturn(withTotal(4));

        List<ScmTodoVO> todos = service.todosFor(List.of(
                "scm:inventory:warning:query",
                "scm:purchase:receipt:query", "scm:purchase:receipt:putaway",
                "scm:inventory:loss-gain:query", "scm:inventory:loss-gain:approve",
                "scm:delivery:route:query", "scm:delivery:route:plan"));

        assertThat(keys(todos)).containsExactlyInAnyOrder(
                "inventory-warning", "receipt-putaway", "loss-gain-audit", "delivery-route-draft");
        verify(warning).queryWarningPage(any());
        verify(purchase).receiptQuery(any());
        verify(lossGain).queryPage(any());
        verify(delivery).query(any());
    }

    @Test
    @DisplayName("有权但零任务：卡片仍在且计数为 0（区别于无权整卡省略）")
    void permittedZeroTaskCardIsPresentWithZero() {
        when(warning.queryWarningPage(any())).thenReturn(withTotal(0));

        List<ScmTodoVO> todos = service.todosFor(List.of("scm:inventory:warning:query"));

        assertThat(todos).singleElement().satisfies(card -> {
            assertThat(card.getKey()).isEqualTo("inventory-warning");
            assertThat(card.getCount()).isZero();
        });
    }

    @Test
    @DisplayName("缺审批权限时报损报溢卡片省略；持有 reject 即满足 any-of")
    void lossGainCardNeedsAnyApproveOrReject() {
        List<ScmTodoVO> queryOnly =
                service.todosFor(List.of("scm:inventory:loss-gain:query"));
        assertThat(queryOnly).isEmpty();
        verifyNoInteractions(lossGain);

        List<ScmTodoVO> withReject = service.todosFor(
                List.of("scm:inventory:loss-gain:query", "scm:inventory:loss-gain:reject"));
        assertThat(keys(withReject)).containsExactly("loss-gain-audit");
    }
}
