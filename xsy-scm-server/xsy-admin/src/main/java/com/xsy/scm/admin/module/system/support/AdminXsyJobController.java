package com.xsy.scm.admin.module.system.support;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.xsy.scm.base.common.controller.SupportBaseController;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.RequestUser;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.constant.SwaggerTagConst;
import com.xsy.scm.base.module.support.job.api.XsyJobService;
import com.xsy.scm.base.module.support.job.api.domain.*;
import com.xsy.scm.base.module.support.job.config.XsyJobAutoConfiguration;
import com.xsy.scm.base.module.support.repeatsubmit.annoation.RepeatSubmit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

/**
 * 定时任务 管理接口
 *
 */
@Tag(name = SwaggerTagConst.Support.JOB)
@RestController
@ConditionalOnBean(XsyJobAutoConfiguration.class)
public class AdminXsyJobController extends SupportBaseController {

    @Autowired
    private XsyJobService jobService;

    @Operation(summary = "定时任务-立即执行")
    @PostMapping("/job/execute")
    @RepeatSubmit
    public ResponseDTO<String> execute(@RequestBody @Valid XsyJobExecuteForm executeForm) {
        RequestUser requestUser = XsyRequestUtil.getRequestUser();
        executeForm.setUpdateName(requestUser.getUserName());
        return jobService.execute(executeForm);
    }

    @Operation(summary = "定时任务-查询详情")
    @GetMapping("/job/{jobId}")
    public ResponseDTO<XsyJobVO> queryJobInfo(@PathVariable Integer jobId) {
        return jobService.queryJobInfo(jobId);
    }

    @Operation(summary = "定时任务-分页查询")
    @PostMapping("/job/query")
    public ResponseDTO<PageResult<XsyJobVO>> queryJob(@RequestBody @Valid XsyJobQueryForm queryForm) {
        return jobService.queryJob(queryForm);
    }

    @Operation(summary = "定时任务-添加任务")
    @PostMapping("/job/add")
    @RepeatSubmit
    public ResponseDTO<String> addJob(@RequestBody @Valid XsyJobAddForm addForm) {
        RequestUser requestUser = XsyRequestUtil.getRequestUser();
        addForm.setUpdateName(requestUser.getUserName());
        return jobService.addJob(addForm);
    }

    @Operation(summary = "定时任务-更新-任务信息")
    @PostMapping("/job/update")
    @RepeatSubmit
    public ResponseDTO<String> updateJob(@RequestBody @Valid XsyJobUpdateForm updateForm) {
        RequestUser requestUser = XsyRequestUtil.getRequestUser();
        updateForm.setUpdateName(requestUser.getUserName());
        return jobService.updateJob(updateForm);
    }

    @Operation(summary = "定时任务-更新-开启状态")
    @PostMapping("/job/update/enabled")
    @RepeatSubmit
    public ResponseDTO<String> updateJobEnabled(@RequestBody @Valid XsyJobEnabledUpdateForm updateForm) {
        RequestUser requestUser = XsyRequestUtil.getRequestUser();
        updateForm.setUpdateName(requestUser.getUserName());
        return jobService.updateJobEnabled(updateForm);
    }

    @Operation(summary = "定时任务-删除")
    @GetMapping("/job/delete")
    @RepeatSubmit
    public ResponseDTO<String> deleteJob(@RequestParam Integer jobId) {
        return jobService.deleteJob(jobId, XsyRequestUtil.getRequestUser());
    }

    @Operation(summary = "定时任务-执行记录-分页查询")
    @PostMapping("/job/log/query")
    public ResponseDTO<PageResult<XsyJobLogVO>> queryJobLog(@RequestBody @Valid XsyJobLogQueryForm queryForm) {
        return jobService.queryJobLog(queryForm);
    }
}
