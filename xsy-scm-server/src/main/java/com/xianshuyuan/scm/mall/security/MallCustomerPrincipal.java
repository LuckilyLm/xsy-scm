package com.xianshuyuan.scm.mall.security;

/**
 * 商城客户身份。与后台员工身份完全隔离，不携带任何员工权限。
 */
public record MallCustomerPrincipal(Long accountId, Long customerId, String customerCode, String customerName,
                                    String username, boolean wechatBound) {
}
