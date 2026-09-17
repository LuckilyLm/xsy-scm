package com.xsy.scm.admin.module.business.finance.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 凭证同步状态
 *
 * 数据库字段：t_finance_voucher.sync_status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum VoucherSyncStatusEnum implements BaseEnum {

    /**
     * 1 未同步
     */
    UNSYNCED(1, "未同步"),

    /**
     * 2 同步中
     */
    SYNCING(2, "同步中"),

    /**
     * 3 同步成功
     */
    SYNCED(3, "同步成功"),

    /**
     * 4 同步失败
     */
    FAILED(4, "同步失败"),

    ;

    private final Integer value;

    private final String desc;
}
