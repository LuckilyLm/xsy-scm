package com.xsy.scm.common.no;

import com.xsy.scm.common.dao.ScmBusinessNoDao;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SCM 内部编码的唯一出口：业务代码只依赖类型枚举，不关心底层序列实现。
 *
 * <p>
 * 上层的创建命令只负责带上生成的编码落库；换用其他发号机制时改本类即可，业务代码不动。
 */
@Service
@RequiredArgsConstructor
public class ScmBusinessNoService {

    private final ScmBusinessNoDao businessNoDao;

    /**
     * 生成下一个内部编码（如 {@code CUS000001}）。
     *
     * <p>
     * 并发唯一性由 PG 序列保证；各主数据表的唯一索引是最后一道保险。
     */
    public String next(ScmBusinessNoType type) {
        return ScmDocumentNumbers.masterCode(type.getPrefix(), businessNoDao.nextSequence(type.getSequence()));
    }
}
