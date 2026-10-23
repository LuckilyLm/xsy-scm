package com.xsy.scm.common.no;

import com.xsy.scm.common.dao.ScmBusinessNoDao;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 内部编码都从这里发：业务代码只依赖类型枚举，不管序列怎么实现。换发号机制只改这里。
 *
 * <p>
 * 创建命令拿到编码后自己保存。
 */
@Service
@RequiredArgsConstructor
public class ScmBusinessNoService {

    private final ScmBusinessNoDao businessNoDao;

    /**
     * 生成下一个内部编码（如 {@code CUS000001}）。
     *
     * <p>
     * 并发唯一性由 PG 序列保证，各主数据表的唯一索引兜底。
     */
    public String next(ScmBusinessNoType type) {
        return ScmDocumentNumbers.masterCode(type.getPrefix(), businessNoDao.nextSequence(type.getSequence()));
    }

    /**
     * 生成带业务日期的内部编码（如 {@code LOT20261018000001}）。只有确有业务日期的编号才用它。
     */
    public String nextDated(ScmBusinessNoType type) {
        return ScmDocumentNumbers.format(type.getPrefix(), businessNoDao.nextSequence(type.getSequence()));
    }
}
