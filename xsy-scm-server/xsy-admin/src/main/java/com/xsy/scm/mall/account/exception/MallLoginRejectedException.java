package com.xsy.scm.mall.account.exception;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;

/**
 * 密码拒绝仍须提交失败计数；其它登录异常保持事务回滚。
 */
public class MallLoginRejectedException extends ScmBusinessException {
    public MallLoginRejectedException(MallErrorCode errorCode) {
        super(errorCode);
    }
}
