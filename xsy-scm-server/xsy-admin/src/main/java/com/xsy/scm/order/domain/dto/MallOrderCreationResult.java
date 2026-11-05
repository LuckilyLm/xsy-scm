package com.xsy.scm.order.domain.dto;

import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;

/**
 * 商城建单结果；重复请求返回原结果标记供商城适配层避免重复执行后置动作。
 */
public record MallOrderCreationResult(SalesOrderDetailVO order, boolean replayed) {
}
