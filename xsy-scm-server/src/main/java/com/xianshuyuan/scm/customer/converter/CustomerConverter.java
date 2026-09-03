package com.xianshuyuan.scm.customer.converter;
import com.xianshuyuan.scm.customer.dto.*; import com.xianshuyuan.scm.customer.entity.*;
public final class CustomerConverter { private CustomerConverter(){}
 public static CustomerEntity toCustomer(CustomerSaveRequest r){var e=new CustomerEntity();e.setVersion(r.version());e.setCustomerCode(r.customerCode().trim());e.setName(r.name().trim());e.setCustomerTypeId(r.customerTypeId());e.setStatus(r.status());e.setVisibilityPolicy(r.visibilityPolicy());e.setUpdatedBy("SYSTEM");return e;}
 public static CustomerTypeEntity toType(CustomerTypeSaveRequest r){var e=new CustomerTypeEntity();e.setVersion(r.version());e.setTypeCode(r.typeCode().trim());e.setName(r.name().trim());e.setStatus(r.status());e.setUpdatedBy("SYSTEM");return e;}
 public static CustomerAgreementPriceEntity toPrice(AgreementPriceSaveRequest r){var e=new CustomerAgreementPriceEntity();e.setVersion(r.version());e.setCustomerId(r.customerId());e.setSkuId(r.skuId());e.setUnitPrice(r.unitPrice());e.setEffectiveFrom(r.effectiveFrom());e.setEffectiveTo(r.effectiveTo());e.setUpdatedBy("SYSTEM");return e;}
}
