package com.xsy.scm.admin.module.business.finance.manager;

import com.xsy.scm.admin.module.business.finance.dao.ReceivableDao;
import com.xsy.scm.admin.module.business.finance.domain.entity.ReceivableEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应收单 Manager
 *
 * <p>负责应收持久化与事务编排，Service 调用本层，不直接操作 Dao。</p>
 *
 * @author xsy-scm
 */
@Service
public class ReceivableManager {

    @Resource
    private ReceivableDao receivableDao;

    /**
     * 新增应收
     */
    @Transactional(rollbackFor = Throwable.class)
    public void save(ReceivableEntity receivableEntity) {
        receivableDao.insert(receivableEntity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(ReceivableEntity receivableEntity) {
        receivableDao.updateById(receivableEntity);
    }

    public ReceivableEntity getById(Long receivableId) {
        return receivableDao.selectById(receivableId);
    }
}
