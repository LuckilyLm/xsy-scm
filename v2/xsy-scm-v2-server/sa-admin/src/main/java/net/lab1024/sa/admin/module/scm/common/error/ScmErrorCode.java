package net.lab1024.sa.admin.module.scm.common.error;

import net.lab1024.sa.base.common.code.ErrorCode;

/** SCM codes share SmartAdmin's response contract, not its exception catch-all. */
public interface ScmErrorCode extends ErrorCode {
    @Override default String getLevel() { return LEVEL_USER; }
}
