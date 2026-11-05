package com.xsy.scm.order;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.manager.OrderStateMachine;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateLifecycleTest {
    @Test
    void onlyDeclaredTransitionsAreAllowedAcrossAllStatuses() {
        var edges = Set.of("PENDING:CONFIRMED", "PENDING:CANCELLED", "CONFIRMED:DISPATCHED",
            "DISPATCHED:SIGNED", "DISPATCHED:EXCEPTION");
        for (var from : ScmOrderStatusEnum.values()) {
            for (var to : ScmOrderStatusEnum.values()) {
                assertThat(OrderStateMachine.canTransition(from.name(), to.name()))
                    .as("%s -> %s", from, to).isEqualTo(edges.contains(from.name() + ":" + to.name()));
            }
        }
        assertThat(OrderStateMachine.canTransition(null, "CONFIRMED")).isFalse();
        assertThat(OrderStateMachine.canTransition("CONFIRMED", null)).isFalse();
        assertThat(OrderStateMachine.canTransition("UNKNOWN", "DISPATCHED")).isFalse();
        assertThatThrownBy(() -> OrderStateMachine.transition("SIGNED", "CONFIRMED"))
            .isInstanceOf(ScmBusinessException.class);
    }

    @Test
    void editingAndQuantityEntryRespectDispatchAndTerminalStates() {
        assertThatCode(() -> OrderStateMachine.editable("DISPATCHED")).doesNotThrowAnyException();
        assertThatThrownBy(() -> OrderStateMachine.actualQuantity("DISPATCHED"))
            .isInstanceOf(ScmBusinessException.class);
        for (var status : Set.of("SIGNED", "EXCEPTION", "CANCELLED")) {
            assertThatThrownBy(() -> OrderStateMachine.editable(status)).isInstanceOf(ScmBusinessException.class);
            assertThatThrownBy(() -> OrderStateMachine.actualQuantity(status)).isInstanceOf(ScmBusinessException.class);
        }
    }

    @Test
    void confirmedTransactionReadsKeepAllSubsequentStages() {
        for (var status : ScmOrderStatusEnum.values()) {
            assertThat(ScmOrderStatusEnum.isConfirmed(status.name()))
                .isEqualTo(Set.of("CONFIRMED", "DISPATCHED", "SIGNED", "EXCEPTION").contains(status.name()));
        }
        assertThat(ScmOrderStatusEnum.isConfirmed(null)).isFalse();
    }
}
