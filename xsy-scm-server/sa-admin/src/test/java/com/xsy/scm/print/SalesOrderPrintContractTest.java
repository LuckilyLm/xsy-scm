package com.xsy.scm.print;

import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.print.domain.model.ScmPrintTemplateModel;
import com.xsy.scm.print.support.SalesOrderPrintSourceProvider;
import com.xsy.scm.print.support.ScmPrintTemplateValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesOrderPrintContractTest {
    @Test
    void salesOrderUsesTheExistingScopedOrderQueryPermission() {
        var provider = new SalesOrderPrintSourceProvider(null);
        assertThat(provider.documentType()).isEqualTo(ScmPrintDocumentTypeEnum.SALES_ORDER);
        assertThat(provider.queryPermission()).isEqualTo(OrderPermission.QUERY);
    }

    @Test
    void templateAcceptsKnownFieldsAndRejectsUnlistedPriceOrExpressionKeys() {
        var type = ScmPrintDocumentTypeEnum.SALES_ORDER;
        var model = new ScmPrintTemplateModel();
        model.setTitle("销售订单");
        model.setPaper("A4");
        model.setOrientation("PORTRAIT");
        model.setHeaderFields(List.of("customerName", "orderNo"));
        model.setColumns(List.of("productName", "lockedUnitPrice", "orderedQuantity"));
        model.setShowTotals(true);
        assertThat(ScmPrintTemplateValidator.validate(type, model).getColumns())
                .containsExactly("productName", "lockedUnitPrice", "orderedQuantity");

        model.setColumns(List.of("productName", "customerSecretExpression"));
        assertThatThrownBy(() -> ScmPrintTemplateValidator.validate(type, model))
                .isInstanceOf(com.xsy.scm.common.exception.ScmBusinessException.class);
    }

    @Test
    void monetaryFieldsAreExplicitlyClassifiedAndOriginalStatesAreKeptDistinct() {
        var type = ScmPrintDocumentTypeEnum.SALES_ORDER;
        assertThat(type.column("draftUnitPrice").money()).isTrue();
        assertThat(type.column("lockedUnitPrice").money()).isTrue();
        assertThat(type.column("orderedQuantity").money()).isFalse();
        assertThat(type.total("orderedTotalAmount").money()).isTrue();
        assertThat(type.total("settlementTotalAmount").money()).isTrue();
        assertThat(type.column("lockedUnitPrice").label()).isNotEqualTo(type.column("draftUnitPrice").label());
    }
}
