package com.xsy.scm.order.domain.dto;

import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;

/** 商城建单结果；幂等重放标记供商城适配层避免重复执行后置动作。 */
public record MallOrderCreationResult(SalesOrderDetailVO order, boolean replayed) {
}
