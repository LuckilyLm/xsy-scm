package com.xianshuyuan.scm.mall.vo;

/**
 * 商城客户身份。只暴露客户自身信息，不返回后台员工或权限字段。
 */
public record MallProfileResponse(Long accountId, Long customerId, String customerCode, String customerName,
                                  String username, boolean wechatBound) {
}
