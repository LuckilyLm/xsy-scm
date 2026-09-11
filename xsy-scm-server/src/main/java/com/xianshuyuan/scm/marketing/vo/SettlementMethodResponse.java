package com.xianshuyuan.scm.marketing.vo;

import com.xianshuyuan.scm.marketing.entity.SettlementMethod;

/**
 * 结算方式视图。需求约定的四种：账期支付、货到付款、在线支付、余额充值。
 */
public record SettlementMethodResponse(SettlementMethod method, String label, String description) {
}
