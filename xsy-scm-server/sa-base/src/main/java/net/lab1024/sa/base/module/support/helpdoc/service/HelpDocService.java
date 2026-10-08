package net.lab1024.sa.base.module.support.helpdoc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartBeanUtil;
import net.lab1024.sa.base.common.util.SmartHtmlSanitizeUtil;
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
        // 正文按不可信 HTML 处理：前端 v-html 直接注入 DOM，对所有可见者执行。
        // 落库前统一走白名单清洗，存储侧不留可执行内容。
        helpDaoEntity.setContentHtml(SmartHtmlSanitizeUtil.clean(helpDaoEntity.getContentHtml()));
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
        // 与新增同一口径：更新路径也必须清洗，否则「先建干净的、再改成带脚本的」就能绕过写入侧校验。
        helpDaoEntity.setContentHtml(SmartHtmlSanitizeUtil.clean(helpDaoEntity.getContentHtml()));
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
            // 历史正文可能早于写入清洗规则，返回编辑器之前也须清洗。
            detail.setContentHtml(SmartHtmlSanitizeUtil.clean(detail.getContentHtml()));
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
