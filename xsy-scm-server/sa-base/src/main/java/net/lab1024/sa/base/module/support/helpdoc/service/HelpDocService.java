package net.lab1024.sa.base.module.support.helpdoc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.service.FileRelationService;
import net.lab1024.sa.base.module.support.helpdoc.dao.HelpDocDao;
import net.lab1024.sa.base.module.support.helpdoc.domain.entity.HelpDocEntity;
import net.lab1024.sa.base.module.support.helpdoc.domain.form.HelpDocAddForm;
import net.lab1024.sa.base.module.support.helpdoc.domain.form.HelpDocQueryForm;
import net.lab1024.sa.base.module.support.helpdoc.domain.form.HelpDocUpdateForm;
import net.lab1024.sa.base.module.support.helpdoc.domain.vo.HelpDocDetailVO;
import net.lab1024.sa.base.module.support.helpdoc.domain.vo.HelpDocVO;
import net.lab1024.sa.base.module.support.helpdoc.manager.HelpDocManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 后台管理业务
 *
 */
@Service
public class HelpDocService {

    @Resource
    private HelpDocDao helpDocDao;

    @Resource
    private HelpDocManager helpDaoManager;

    @Resource
    private FileRelationService fileRelationService;


    /**
     * 查询 帮助文档
     *
     * @param queryForm
     * @return
     */
    public PageResult<HelpDocVO> query(HelpDocQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<HelpDocVO> list = helpDocDao.query(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 添加
     *
     * @param addForm
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(HelpDocAddForm addForm) {
        HelpDocEntity helpDaoEntity = SmartBeanUtil.copy(addForm, HelpDocEntity.class);
        helpDaoManager.save(helpDaoEntity, addForm.getRelationList());
        // 私有附件的读取权由关系行决定：存了 fileKey 却不绑定，其他有权查看者永远读不到它
        fileRelationService.rebind(FileRelationBizTypeEnum.HELP_DOC, helpDaoEntity.getHelpDocId(),
                FileRelationService.splitKeys(helpDaoEntity.getAttachment()));
        return ResponseDTO.ok();
    }


    /**
     * 更新
     *
     * @param updateForm
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(HelpDocUpdateForm updateForm) {
        // 更新
        HelpDocEntity helpDaoEntity = SmartBeanUtil.copy(updateForm, HelpDocEntity.class);
        helpDaoManager.update(helpDaoEntity, updateForm.getRelationList());
        // 用 rebind 而不是 bind：更新可以删掉附件，被删掉的 key 必须同时失去授权
        fileRelationService.rebind(FileRelationBizTypeEnum.HELP_DOC, helpDaoEntity.getHelpDocId(),
                FileRelationService.splitKeys(helpDaoEntity.getAttachment()));
        return ResponseDTO.ok();
    }


    /**
     * 删除
     *
     * @param helpDocId
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long helpDocId) {
        HelpDocEntity helpDaoEntity = helpDocDao.selectById(helpDocId);
        if (helpDaoEntity != null) {
            helpDocDao.deleteById(helpDocId);
            helpDocDao.deleteRelation(helpDocId);
        }
        return ResponseDTO.ok();
    }

    /**
     * 获取详情
     *
     * @param helpDocId
     * @return
     */
    public HelpDocDetailVO getDetail(Long helpDocId) {
        HelpDocEntity helpDaoEntity = helpDocDao.selectById(helpDocId);
        HelpDocDetailVO detail = SmartBeanUtil.copy(helpDaoEntity, HelpDocDetailVO.class);
        if (detail != null) {
            detail.setRelationList(helpDocDao.queryRelationByHelpDoc(helpDocId));
        }
        return detail;
    }

    /**
     * 获取详情
     *
     * @param relationId
     * @return
     */
    public List<HelpDocVO> queryHelpDocByRelationId(Long relationId) {
        return helpDocDao.queryHelpDocByRelationId(relationId);
    }
}
