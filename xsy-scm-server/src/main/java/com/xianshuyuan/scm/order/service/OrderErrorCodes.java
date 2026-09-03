package com.xianshuyuan.scm.order.service;
import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
public final class OrderErrorCodes {
 private OrderErrorCodes(){}
 public static final ErrorCode ORDER_NOT_FOUND=new ErrorCode(40420,HttpStatus.NOT_FOUND,"销售订单不存在");
 public static final ErrorCode ITEM_NOT_FOUND=new ErrorCode(40421,HttpStatus.NOT_FOUND,"订单行不存在");
 public static final ErrorCode VERSION_CONFLICT=new ErrorCode(40920,HttpStatus.CONFLICT,"销售订单版本冲突");
 public static final ErrorCode ITEM_VERSION_CONFLICT=new ErrorCode(40922,HttpStatus.CONFLICT,"订单行版本冲突");
 public static final ErrorCode INVALID_STATE=new ErrorCode(40923,HttpStatus.CONFLICT,"当前订单状态不允许此操作");
 public static final ErrorCode SUPPLEMENT_REASON_REQUIRED=new ErrorCode(40020,HttpStatus.BAD_REQUEST,"补单原因不能为空");
 public static final ErrorCode INVALID_SUPPLEMENT=new ErrorCode(40021,HttpStatus.BAD_REQUEST,"普通订单不能关联原订单或补单原因");
 public static final ErrorCode ORIGINAL_ORDER_INVALID=new ErrorCode(40924,HttpStatus.CONFLICT,"关联原订单必须已确认且客户一致");
 public static final ErrorCode DUPLICATE_SKU=new ErrorCode(40022,HttpStatus.BAD_REQUEST,"订单行SKU不能重复");
 public static final ErrorCode INVALID_QUANTITY=new ErrorCode(40023,HttpStatus.BAD_REQUEST,"数量必须大于零");
 public static final ErrorCode OVERRIDE_REASON_REQUIRED=new ErrorCode(40024,HttpStatus.BAD_REQUEST,"人工改价必须填写价格和原因");
 public static final ErrorCode INVALID_PRICE_OVERRIDE=new ErrorCode(40025,HttpStatus.BAD_REQUEST,"非人工改价行不能指定价格");
 public static final ErrorCode INVALID_PRICE=new ErrorCode(40026,HttpStatus.BAD_REQUEST,"价格不能小于零");
 public static final ErrorCode ACTUAL_ONLY_NON_STANDARD=new ErrorCode(40925,HttpStatus.CONFLICT,"只有待审核非标品订单行可录入实数量");
 public static final ErrorCode ACTUAL_REASON_REQUIRED=new ErrorCode(40027,HttpStatus.BAD_REQUEST,"实重修改原因不能为空");
 public static final ErrorCode CANCEL_REASON_REQUIRED=new ErrorCode(40028,HttpStatus.BAD_REQUEST,"取消原因不能为空");
 public static final ErrorCode ACTUAL_QUANTITY_REQUIRED=new ErrorCode(40926,HttpStatus.CONFLICT,"确认前所有订单行必须具有有效实数量");
 public static final ErrorCode IDEMPOTENCY_KEY_REQUIRED=new ErrorCode(40029,HttpStatus.BAD_REQUEST,"Idempotency-Key不能为空");
}
