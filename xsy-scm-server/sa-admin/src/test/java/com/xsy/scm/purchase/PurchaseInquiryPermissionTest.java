package com.xsy.scm.purchase;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.purchase.controller.PurchaseInquiryController;
import com.xsy.scm.purchase.domain.form.PurchaseInquiryForm;
import com.xsy.scm.purchase.permission.PurchasePermission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseInquiryPermissionTest {
    @Test
    void eachInquiryOperationHasAnIndependentFunctionPermission() throws Exception {
        assertThat(PurchaseInquiryController.class.getMethod("byBatch", PurchaseInquiryForm.Batch.class)
                .getAnnotation(SaCheckPermission.class).value()).containsExactly(PurchasePermission.INQUIRY_QUERY);
        assertThat(PurchaseInquiryController.class.getMethod("candidates", PurchaseInquiryForm.Batch.class)
                .getAnnotation(SaCheckPermission.class).value()).containsExactly(PurchasePermission.INQUIRY_QUERY);
        assertThat(PurchaseInquiryController.class.getMethod("create", PurchaseInquiryForm.Create.class, String.class)
                .getAnnotation(SaCheckPermission.class).value()).containsExactly(PurchasePermission.INQUIRY_CREATE);
        assertThat(PurchaseInquiryController.class.getMethod("quote", PurchaseInquiryForm.Quote.class, String.class)
                .getAnnotation(SaCheckPermission.class).value()).containsExactly(PurchasePermission.INQUIRY_QUOTE);
        assertThat(PurchaseInquiryController.class.getMethod("accept", PurchaseInquiryForm.Accept.class, String.class)
                .getAnnotation(SaCheckPermission.class).value()).containsExactly(PurchasePermission.INQUIRY_ACCEPT);
    }
}
