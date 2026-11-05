package com.xsy.scm.base.module.support.feedback.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.RequestUser;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.XsyBeanUtil;
import com.xsy.scm.base.common.util.XsyPageUtil;
import com.xsy.scm.base.module.support.feedback.dao.FeedbackDao;
import com.xsy.scm.base.module.support.feedback.domain.FeedbackAddForm;
import com.xsy.scm.base.module.support.feedback.domain.FeedbackEntity;
import com.xsy.scm.base.module.support.feedback.domain.FeedbackQueryForm;
import com.xsy.scm.base.module.support.feedback.domain.FeedbackVO;
import com.xsy.scm.base.module.support.file.constant.FileRelationBizTypeEnum;
import com.xsy.scm.base.module.support.file.service.FileRelationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 意见反馈
 *
 */
@Service
public class FeedbackService {

    @Resource
    private FeedbackDao feedbackDao;

    @Resource
    private FileRelationService fileRelationService;

    /**
     * 分页查询
     *
     */
    public ResponseDTO<PageResult<FeedbackVO>> query(FeedbackQueryForm queryForm) {
        Page page = XsyPageUtil.convert2PageQuery(queryForm);
        List<FeedbackVO> list = feedbackDao.queryPage(page, queryForm);
        PageResult<FeedbackVO> pageResultDTO = XsyPageUtil.convert2PageResult(page, list);
        if (pageResultDTO.getEmptyFlag()) {
            return ResponseDTO.ok(pageResultDTO);
        }
        return ResponseDTO.ok(pageResultDTO);
    }

    /**
     * 新建
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDTO<String> add(FeedbackAddForm addForm, RequestUser requestUser) {
        FeedbackEntity feedback = XsyBeanUtil.copy(addForm, FeedbackEntity.class);
        feedback.setUserType(requestUser.getUserType().getValue());
        feedback.setUserId(requestUser.getUserId());
        feedback.setUserName(requestUser.getUserName());
        feedbackDao.insert(feedback);
        // 反馈行只增不改，绑定即可：不绑定的话处理人看不到用户自己上传的截图
        fileRelationService.bind(FileRelationBizTypeEnum.FEEDBACK, feedback.getFeedbackId(),
            FileRelationService.splitKeys(feedback.getFeedbackAttachment()));
        return ResponseDTO.ok();
    }
}
