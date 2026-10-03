package com.xsy.scm.payment.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 支付域单号序列。
 *
 * <p>
 * 与采购 / 出库同口径：序列全局单调递增、不按日 reset，日期段只是可读性装饰。
 */
@Mapper
public interface PaymentDocumentNumberDao {

    @Select("SELECT nextval('payment_document_seq')")
    long nextDocumentNo();
}
