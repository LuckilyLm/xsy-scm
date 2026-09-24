package net.lab1024.sa.admin.module.scm.common.scope;

import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.exception.BusinessException;

/**
 * 数据范围拒绝：该行确实存在，只是不在当前调用者的授权范围内。
 *
 * <p>不复用「对象不存在」的错误码，是为了让运维能从日志里分清越权探测与脏数据；
 * 对外仍映射成与功能权限同一个 30005，不给调用方一个可用来枚举主键的差异化信号。
 *
 * <p>必须是独立类型而不是 {@code BusinessException(UserErrorCode.NO_PERMISSION)}：
 * 底座的全局处理器会把普通 {@code BusinessException} 统一映射成系统错误，
 * 只有本类由 {@code ScmExceptionHandler} 显式翻译成 30005 信封。
 */
public class ScmDataScopeException extends BusinessException {

    /**
     * 消息与 {@code ScmExceptionHandler} 给出的信封同源：日志里的原因与客户端拿到的
     * {@code msg} 是同一句话，排查时不会两种说法各信一半。
     */
    public ScmDataScopeException() {
        super(UserErrorCode.NO_PERMISSION.getMsg());
    }
}
