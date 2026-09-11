package com.xianshuyuan.scm.mall.service;

import com.xianshuyuan.scm.mall.vo.MallCheckoutItemResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MallCheckoutFingerprint {
    public String create(long customerId, long addressId, List<MallCheckoutItemResponse> items) {
        String lines = items.stream().sorted(Comparator.comparing(MallCheckoutItemResponse::skuId))
                .map(item -> item.skuId() + ":" + item.quantity() + ":" + item.unitPrice() + ":"
                        + item.priceSource() + ":" + (item.priceSourceId() == null ? "" : item.priceSourceId()))
                .collect(Collectors.joining("|"));
        String source = customerId + ":" + addressId + ":" + lines;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("缺少 SHA-256 算法支持", exception);
        }
    }
}
