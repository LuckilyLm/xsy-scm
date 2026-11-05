package com.xsy.scm.inventory;

import com.xsy.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import com.xsy.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalesReturnInventoryContractTest {
    @Test
    void salesReturnIsInboundAndHasDedicatedSource() {
        assertThat(ScmInventoryMovementTypeEnum.SALES_RETURN_IN.isInbound()).isTrue();
        assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("SALES_RETURN_RECEIPT_ITEM")).isTrue();
    }
}
