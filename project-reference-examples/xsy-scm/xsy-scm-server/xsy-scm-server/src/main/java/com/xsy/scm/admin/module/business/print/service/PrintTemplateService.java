package com.xsy.scm.admin.module.business.print.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.print.constant.PrintTemplateStatusEnum;
import com.xsy.scm.admin.module.business.print.dao.PrintTemplateDao;
import com.xsy.scm.admin.module.business.print.domain.entity.PrintTemplateEntity;
import com.xsy.scm.admin.module.business.print.domain.form.PrintPreviewForm;
import com.xsy.scm.admin.module.business.print.domain.form.PrintTemplateAddForm;
import com.xsy.scm.admin.module.business.print.domain.form.PrintTemplateQueryForm;
import com.xsy.scm.admin.module.business.print.domain.form.PrintTemplateUpdateForm;
import com.xsy.scm.admin.module.business.print.domain.vo.PrintPreviewVO;
import com.xsy.scm.admin.module.business.print.domain.vo.PrintTemplateVO;
import com.xsy.scm.admin.module.business.print.manager.PrintTemplateManager;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import com.xsy.scm.base.common.util.SmartPageUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 打印模板 Service
 *
 * <p>对标蔬东坡：单据模板自定义（采购单 / 发货单 / 分拣小票 / 询价报价单）+ 服务端渲染，
 * 打印内容由服务端渲染，避免前端篡改金额。</p>
 *
 * @author xsy-scm
 */
@Service
@Slf4j
public class PrintTemplateService {

    /**
     * 占位符前后缀
     */
    private static final String PLACEHOLDER_PREFIX = "{{";
    private static final String PLACEHOLDER_SUFFIX = "}}";

    @Resource
    private PrintTemplateDao printTemplateDao;

    @Resource
    private PrintTemplateManager printTemplateManager;

    /**
     * 分页查询打印模板
     */
    public ResponseDTO<PageResult<PrintTemplateVO>> query(PrintTemplateQueryForm queryForm) {
        queryForm.setDeletedFlag(Boolean.FALSE);
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<PrintTemplateVO> list = printTemplateDao.queryPage(page, queryForm);
        return ResponseDTO.ok(SmartPageUtil.convert2PageResult(page, list));
    }

    /**
     * 新增打印模板
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(PrintTemplateAddForm addForm) {
        PrintTemplateEntity entity = SmartBeanUtil.copy(addForm, PrintTemplateEntity.class);
        entity.setDefaultFlag(0);
        if (entity.getStatus() == null) {
            entity.setStatus(PrintTemplateStatusEnum.ENABLED.getValue());
        }
        entity.setDeletedFlag(Boolean.FALSE);
        printTemplateManager.save(entity);
        return ResponseDTO.ok();
    }

    /**
     * 更新打印模板
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> update(PrintTemplateUpdateForm updateForm) {
        PrintTemplateEntity entity = SmartBeanUtil.copy(updateForm, PrintTemplateEntity.class);
        printTemplateManager.update(entity);
        return ResponseDTO.ok();
    }

    /**
     * 设为默认模板
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> setDefault(Long templateId) {
        PrintTemplateEntity entity = printTemplateDao.selectById(templateId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            return ResponseDTO.userErrorParam("模板不存在");
        }
        printTemplateManager.setDefault(templateId, entity.getBizType());
        return ResponseDTO.ok();
    }

    /**
     * 服务端渲染打印预览
     *
     * <p>按模板编码取启用中的模板，用业务数据填充 <code>{{key}}</code> 占位符；
     * 值统一做 HTML 转义，避免打印内容注入。</p>
     */
    public ResponseDTO<PrintPreviewVO> preview(PrintPreviewForm previewForm) {
        PrintTemplateEntity entity = printTemplateDao.getByTemplateCode(previewForm.getTemplateCode());
        if (entity == null) {
            return ResponseDTO.userErrorParam("打印模板不存在或已停用");
        }
        PrintPreviewVO vo = new PrintPreviewVO();
        vo.setTemplateCode(entity.getTemplateCode());
        vo.setTemplateName(entity.getTemplateName());
        vo.setPaperSize(entity.getPaperSize());
        vo.setContent(render(entity.getContent(), previewForm.getParams()));
        return ResponseDTO.ok(vo);
    }

    /**
     * 删除打印模板（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> delete(Long templateId) {
        printTemplateDao.batchUpdateDeleted(Collections.singletonList(templateId), Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 批量删除打印模板（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> batchDelete(ValidateList<Long> idList) {
        printTemplateDao.batchUpdateDeleted(idList, Boolean.TRUE);
        return ResponseDTO.ok();
    }

    /**
     * 占位符渲染（值做 HTML 转义）
     */
    private String render(String template, Map<String, Object> params) {
        if (template == null || template.isEmpty() || params == null || params.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = PLACEHOLDER_PREFIX + entry.getKey() + PLACEHOLDER_SUFFIX;
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            result = result.replace(placeholder, escapeHtml(value));
        }
        return result;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
