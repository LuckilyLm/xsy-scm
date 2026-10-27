package com.xsy.scm.customer;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.dao.CustomerDao;
import com.xsy.scm.customer.dao.CustomerGroupDao;
import com.xsy.scm.customer.service.CustomerGroupService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 分组至少一个客户：移动客户不能把原分组留成空壳。 */
class CustomerGroupServiceTest {

    private final CustomerGroupDao groups = mock(CustomerGroupDao.class);

    private final CustomerDao customers = mock(CustomerDao.class);

    private final CustomerGroupService service = new CustomerGroupService(groups, customers);

    @Test
    void movingOnlyMemberOutOfGroupIsRejected() {
        when(customers.selectEmptiedGroupIds(List.of(3L), 9L)).thenReturn(List.of(7L));

        assertThatThrownBy(() -> service.assertCanMove(List.of(3L), 9L))
            .isInstanceOf(ScmBusinessException.class)
            .hasMessageContaining("原分组");
    }

    @Test
    void movingMemberThatLeavesOthersBehindIsAllowed() {
        when(customers.selectEmptiedGroupIds(List.of(3L), 9L)).thenReturn(List.of());

        assertThatCode(() -> service.assertCanMove(List.of(3L), 9L)).doesNotThrowAnyException();
    }

    @Test
    void emptyMoveDoesNotQuery() {
        service.assertCanMove(List.of(), 9L);

        verifyNoInteractions(customers);
    }
}
