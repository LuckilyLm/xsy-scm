package com.xsy.scm.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.order.domain.entity.OrderOperationLogEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SalesOrderOperatorNameTest {

    @Test
    void mallAccountIdIsNeverResolvedAsAnEmployee() {
        String name = SalesOrderQueryService.operatorName("2:44",
                Map.of(44L, "海岸城门店（商城客户）"), ignored -> "员工甲");

        assertThat(name).isEqualTo("海岸城门店（商城客户）");
    }

    @Test
    void groupsIdsByOperatorTypeAndKeepsUnknownValuesReadable() {
        OrderOperationLogEntity employee = log("1:7");
        OrderOperationLogEntity mall = log("2:7");
        OrderOperationLogEntity system = log("SYSTEM");

        assertThat(SalesOrderQueryService.operatorIds(List.of(employee, mall, system), "1:")).containsExactly(7L);
        assertThat(SalesOrderQueryService.operatorIds(List.of(employee, mall, system), "2:")).containsExactly(7L);
        assertThat(SalesOrderQueryService.operatorName("2:8", Map.of(), operator -> operator))
                .isEqualTo("商城客户（账号 8）");
        assertThat(SalesOrderQueryService.operatorName("SYSTEM", Map.of(), operator -> operator)).isEqualTo("SYSTEM");
    }

    private static OrderOperationLogEntity log(String operator) {
        OrderOperationLogEntity row = new OrderOperationLogEntity();
        row.setOperator(operator);
        return row;
    }
}
