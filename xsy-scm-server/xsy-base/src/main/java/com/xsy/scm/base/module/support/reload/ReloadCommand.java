package com.xsy.scm.base.module.support.reload;

import jakarta.annotation.Resource;
import com.xsy.scm.base.common.util.XsyBeanUtil;
import com.xsy.scm.base.module.support.reload.core.AbstractXsyReloadCommand;
import com.xsy.scm.base.module.support.reload.core.domain.XsyReloadItem;
import com.xsy.scm.base.module.support.reload.core.domain.XsyReloadResult;
import com.xsy.scm.base.module.support.reload.dao.ReloadItemDao;
import com.xsy.scm.base.module.support.reload.dao.ReloadResultDao;
import com.xsy.scm.base.module.support.reload.domain.ReloadItemEntity;
import com.xsy.scm.base.module.support.reload.domain.ReloadResultEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * reload 操作
 *
 */
@Component
public class ReloadCommand extends AbstractXsyReloadCommand {

    @Resource
    private ReloadItemDao reloadItemDao;

    @Resource
    private ReloadResultDao reloadResultDao;

    /**
     * 读取数据库中XsyReload项
     *
     * @return List<ReloadItem>
     */
    @Override
    public List<XsyReloadItem> readReloadItem() {
        List<ReloadItemEntity> reloadItemEntityList = reloadItemDao.selectList(null);
        return XsyBeanUtil.copyList(reloadItemEntityList, XsyReloadItem.class);
    }

    /**
     * 保存reload结果
     *
     * @param xsyReloadResult
     */
    @Override
    public void handleReloadResult(XsyReloadResult xsyReloadResult) {
        ReloadResultEntity reloadResultEntity = XsyBeanUtil.copy(xsyReloadResult, ReloadResultEntity.class);
        reloadResultDao.insert(reloadResultEntity);
    }
}
