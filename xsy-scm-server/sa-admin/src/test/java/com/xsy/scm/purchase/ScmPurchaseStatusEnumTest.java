package com.xsy.scm.purchase;

import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.manager.PurchaseOrderStateMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购「已提交」口径的派生契约。
 *
 * <p>这份清单是首页、大屏、报表采购指标的唯一状态来源。两条规则必须成立：清单由<b>排除法</b>派生（新增状态默认算进来），
 * 且被排除的两个状态确实不该计入采购额。
 */
class ScmPurchaseStatusEnumTest {

    @Test
    @DisplayName("已提交清单 = 全部状态去掉 DRAFT 与 CANCELLED")
    void committedNamesAreDerivedByExclusion() {
        // 期望值在这里独立算一遍，不复用 committed()：实现若被改成硬编码的正列举，新增状态时会与这里对不上。
        List<String> expected = Arrays.stream(ScmPurchaseStatusEnum.values()).map(Enum::name)
                .filter(name -> !Set.of("DRAFT", "CANCELLED").contains(name)).toList();
        assertThat(ScmPurchaseStatusEnum.committedNames()).containsExactlyElementsOf(expected);
        assertThat(ScmPurchaseStatusEnum.committedNames())
                .containsExactlyInAnyOrder("SUBMITTED", "PARTIALLY_RECEIVED", "RECEIVED", "SHORT_CLOSED");
    }

    @Test
    @DisplayName("DRAFT 与 CANCELLED 不计入采购额：草稿是本地单，取消的已退出履约链路")
    void draftAndCancelledAreNotCommitted() {
        assertThat(ScmPurchaseStatusEnum.DRAFT.committed()).isFalse();
        assertThat(ScmPurchaseStatusEnum.CANCELLED.committed()).isFalse();
        for (ScmPurchaseStatusEnum status : ScmPurchaseStatusEnum.values()) {
            if (status == ScmPurchaseStatusEnum.DRAFT || status == ScmPurchaseStatusEnum.CANCELLED) {
                continue;
            }
            assertThat(status.committed()).as("%s 已进入履约链路", status).isTrue();
        }
    }

    @Test
    @DisplayName("「已提交后又被取消」只发生在一票未收的单上")
    void cancellationOnlyHappensBeforeAnyReceipt() {
        // 这是「当前有效口径」成立的前提：取消会让历史采购额变小，但那些单从未产生收货，
        // 所以抹掉它们不会让「收了多少货」与「采购了多少」对不上。
        assertThat(PurchaseOrderStateMachine.canTransition("DRAFT", "CANCELLED")).isTrue();
        assertThat(PurchaseOrderStateMachine.canTransition("SUBMITTED", "CANCELLED")).isTrue();
        assertThat(PurchaseOrderStateMachine.canTransition("PARTIALLY_RECEIVED", "CANCELLED"))
                .as("部分收货不可取消，只能 shortClose").isFalse();
        assertThat(PurchaseOrderStateMachine.canTransition("RECEIVED", "CANCELLED")).isFalse();
        assertThat(PurchaseOrderStateMachine.canTransition("SHORT_CLOSED", "CANCELLED")).isFalse();
    }
}
