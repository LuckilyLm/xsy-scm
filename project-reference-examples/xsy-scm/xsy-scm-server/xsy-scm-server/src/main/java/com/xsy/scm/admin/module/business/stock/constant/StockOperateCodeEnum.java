package com.xsy.scm.admin.module.business.stock.constant;

import com.xsy.scm.base.common.code.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存操作（统一业务层）错误码
 *
 * @author xsy-scm
 */
@Getter
@AllArgsConstructor
public enum StockOperateCodeEnum implements ErrorCode {

    /**
     * 余额不存在（出库 / 盘点前必须先有入库记录）
     */
    BALANCE_NOT_EXIST(31001, "库存余额不存在，请先完成入库"),

    /**
     * 库存不足
     */
    STOCK_NOT_ENOUGH(31002, "库存不足，无法出库"),

    ;

    private final int code;

    private final String msg;

    private final String level;

    StockOperateCodeEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
        this.level = LEVEL_USER;
    }
}
