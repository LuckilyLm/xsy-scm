package com.xianshuyuan.scm.order.service;

import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public final class AfterSalesErrorCodes {
    public static final ErrorCode RETURN_NOT_FOUND = new ErrorCode(40450, HttpStatus.NOT_FOUND, "退货单不存在");
    public static final ErrorCode REFUND_NOT_FOUND = new ErrorCode(40451, HttpStatus.NOT_FOUND, "退款单不存在");
    public static final ErrorCode ORDER_NOT_CONFIRMED = new ErrorCode(40950, HttpStatus.CONFLICT, "仅已确认订单可申请退货");
    public static final ErrorCode RETURN_STATUS_INVALID = new ErrorCode(40951, HttpStatus.CONFLICT, "当前退货状态不允许此操作");
    public static final ErrorCode REFUND_STATUS_INVALID = new ErrorCode(40952, HttpStatus.CONFLICT, "当前退款状态不允许此操作");
    public static final ErrorCode RETURN_QUANTITY_EXCEEDED = new ErrorCode(40953, HttpStatus.CONFLICT, "退货数量超过可退数量");
    public static final ErrorCode VERSION_CONFLICT = new ErrorCode(40954, HttpStatus.CONFLICT, "数据已被其他操作修改，请刷新后重试");
    public static final ErrorCode ORDER_ITEM_INVALID = new ErrorCode(40050, HttpStatus.BAD_REQUEST, "退货行不属于原订单或订单行不可退");
    public static final ErrorCode APPROVAL_INVALID = new ErrorCode(40051, HttpStatus.BAD_REQUEST, "批准数量无效或未批准任何商品");
    public static final ErrorCode IDEMPOTENCY_REQUIRED = new ErrorCode(40052, HttpStatus.BAD_REQUEST, "缺少 Idempotency-Key");

    private AfterSalesErrorCodes() {
    }
}
