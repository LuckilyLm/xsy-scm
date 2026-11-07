package com.xsy.scm.mall.account.exception;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;

/**
 * 密码登录或改密被拒绝时仍须提交失败计数；其他业务异常保持事务回滚。
 */
public class MallLoginRejectedException extends ScmBusinessException {
    public MallLoginRejectedException(MallErrorCode errorCode) {
        super(errorCode);
    }
}
