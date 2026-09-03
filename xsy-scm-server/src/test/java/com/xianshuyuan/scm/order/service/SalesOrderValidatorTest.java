package com.xianshuyuan.scm.order.service;

import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.order.dto.SalesOrderItemSaveRequest;
import com.xianshuyuan.scm.order.dto.SalesOrderSaveRequest;
import com.xianshuyuan.scm.order.entity.OrderSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SalesOrderValidatorTest {
    private final SalesOrderValidator validator = new SalesOrderValidator();

    @Test void supplementRequiresReason() {
        var request = new SalesOrderSaveRequest(null, 1L, OrderSource.SUPPLEMENT, null, null,
            List.of(new SalesOrderItemSaveRequest(null, null, 2L, "1.0000", null, false, null)));
        assertThatThrownBy(() -> validator.validateDraft(request)).isInstanceOf(BusinessException.class)
            .hasMessage("补单原因不能为空");
    }

    @Test void manualPriceRequiresPriceAndReason() {
        var request = new SalesOrderSaveRequest(null, 1L, OrderSource.NORMAL, null, null,
            List.of(new SalesOrderItemSaveRequest(null, null, 2L, "1.0000", null, true, " ")));
        assertThatThrownBy(() -> validator.validateDraft(request)).isInstanceOf(BusinessException.class)
            .hasMessage("人工改价必须填写价格和原因");
    }
}
