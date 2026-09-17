package com.xsy.scm.admin.module.business.print.manager;

import com.xsy.scm.admin.module.business.print.dao.PrintTemplateDao;
import com.xsy.scm.admin.module.business.print.domain.entity.PrintTemplateEntity;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 打印模板 Manager
 *
 * @author xsy-scm
 */
@Service
public class PrintTemplateManager {

    /**
     * 默认标记：是
     */
    private static final int DEFAULT_FLAG_YES = 1;

    @Resource
    private PrintTemplateDao printTemplateDao;

    @Transactional(rollbackFor = Throwable.class)
    public void save(PrintTemplateEntity entity) {
        printTemplateDao.insert(entity);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void update(PrintTemplateEntity entity) {
        printTemplateDao.updateById(entity);
    }

    /**
     * 设为默认模板：先清除同业务类型的默认标记，再置当前模板为默认
     */
    @Transactional(rollbackFor = Throwable.class)
    public void setDefault(Long templateId, Integer bizType) {
        printTemplateDao.clearDefaultByBizType(bizType);
        PrintTemplateEntity entity = new PrintTemplateEntity();
        entity.setTemplateId(templateId);
        entity.setDefaultFlag(DEFAULT_FLAG_YES);
        printTemplateDao.updateById(entity);
    }
}
