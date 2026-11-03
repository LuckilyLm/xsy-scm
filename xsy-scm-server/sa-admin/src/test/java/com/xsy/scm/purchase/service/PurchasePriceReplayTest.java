package com.xsy.scm.purchase.service;

import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.purchase.dao.PurchaseAccountDao;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.dao.PurchaseOrderItemDao;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import com.xsy.scm.purchase.domain.form.PurchasePriceForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 采购改派后的回放也必须按当前归属授权，已记账不能阻断原命令的成功回放。 */
@ExtendWith(MockitoExtension.class)
class PurchasePriceReplayTest {
    @Mock
    private PurchaseOrderDao orderDao;
    @Mock
    private PurchaseOrderItemDao itemDao;
    @Mock
    private PurchaseAccountDao accountDao;
    @Mock
    private PurchaseOwnerResolver ownerResolver;
    @Mock
    private PurchaseIdempotencyService idempotency;
    @InjectMocks
    private PurchasePriceService prices;

    @BeforeEach
    void prepare() {
        var item = new PurchaseOrderItemEntity();
        item.setId(11L);
        item.setPurchaseOrderId(22L);
        var order = new PurchaseOrderEntity();
        order.setId(22L);
        order.setPurchaserId(33L);
        order.setStatus("ACCOUNTED");
        when(itemDao.selectById(11L)).thenReturn(item);
        when(orderDao.lock(22L)).thenReturn(order);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fill", "adjust"})
    void revokedOwnershipRejectsReplayBeforeReadingTheSnapshot(String command) {
        doThrow(new ScmDataScopeException()).when(ownerResolver).requireVisible(33L);

        assertThatThrownBy(() -> execute(command, form())).isInstanceOf(ScmDataScopeException.class);

        verifyNoInteractions(idempotency, accountDao);
        verify(itemDao, never()).lock(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"fill", "adjust"})
    void authorizedReplayReturnsOriginalResultBeforeCheckingMutableState(String command) {
        var form = form();
        var claim = new PurchaseIdempotencyService.Claim(null, true);
        var snapshot = new PurchaseOrderVO();
        when(idempotency.claim(eq("PURCHASE_PRICE_" + command.toUpperCase(java.util.Locale.ROOT) + ":11"),
                eq("original-key"), eq(form))).thenReturn(claim);
        when(idempotency.replay(claim, PurchaseOrderVO.class)).thenReturn(snapshot);

        assertThat(execute(command, form)).isSameAs(snapshot);

        var sequence = inOrder(ownerResolver, idempotency);
        sequence.verify(ownerResolver).requireVisible(33L);
        sequence.verify(idempotency).claim(any(), eq("original-key"), eq(form));
        sequence.verify(idempotency).replay(claim, PurchaseOrderVO.class);
        verifyNoInteractions(accountDao);
        verify(itemDao, never()).lock(any());
    }

    private PurchaseOrderVO execute(String command, PurchasePriceForm form) {
        return "fill".equals(command) ? prices.fill(11L, form, "original-key")
                : prices.adjust(11L, form, "original-key");
    }

    private PurchasePriceForm form() {
        var form = new PurchasePriceForm();
        form.setPrice("3.0000");
        form.setReason("核对采购价");
        return form;
    }
}
