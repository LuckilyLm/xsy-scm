package com.xianshuyuan.scm.mall.vo;

public record MallAddressResponse(Long id, String receiverName, String phone, String region, String detailAddress,
                                  boolean defaultAddress) {
}
