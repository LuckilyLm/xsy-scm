package com.xsy.scm.sorting.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.sorting.domain.form.SortingScaleAcceptForm;
import com.xsy.scm.sorting.domain.form.SortingScaleRejectForm;
import com.xsy.scm.sorting.domain.form.SortingScaleReportForm;
import com.xsy.scm.sorting.domain.vo.SortingScaleEventVO;
import com.xsy.scm.sorting.permission.SortingPermission;
import com.xsy.scm.sorting.service.SortingScaleEventService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电子秤读数：上报（设备）→ 查询（作业台）→ 接受 / 驳回（人工）。
 *
 * <p>
 * 上报与接受分开授权：上报只是把设备读数记下来，接受才写进分拣结果。
 * 上报带 {@code eventKey} 幂等键，重复上报返回既有记录并置 {@code duplicated}，不报错。
 */
@RestController
@RequestMapping("/scm/sorting/scale")
@Tag(name = "SCM 分拣电子秤")
@RequiredArgsConstructor
public class SortingScaleController {

    private final SortingScaleEventService sortingScaleEventService;

    @PostMapping("/report")
    @SaCheckPermission(SortingPermission.SCALE_REPORT)
    public ResponseDTO<SortingScaleEventVO> report(@Valid @RequestBody SortingScaleReportForm form) {
        return ResponseDTO.ok(sortingScaleEventService.report(form));
    }

    /**
     * 某任务的读数列表；{@code status} 为空返回全部（默认看全部，便于追溯被驳回的读数）。
     */
    @GetMapping("/tasks/{taskId}")
    @SaCheckPermission(SortingPermission.SCALE_QUERY)
    public ResponseDTO<List<SortingScaleEventVO>> list(@PathVariable("taskId") Long taskId,
            @RequestParam(value = "status", required = false) String status) {
        return ResponseDTO.ok(sortingScaleEventService.list(taskId, status));
    }

    /**
     * 接受读数：把该读数写进分拣结果（只处理标准品）。
     */
    @PostMapping("/{id}/accept")
    @SaCheckPermission(SortingPermission.SCALE_ACCEPT)
    @OperateLog
    public ResponseDTO<String> accept(@PathVariable("id") Long id,
            @Valid @RequestBody SortingScaleAcceptForm form) {
        sortingScaleEventService.accept(id, form);
        return ResponseDTO.ok();
    }

    /**
     * 驳回读数：不写分拣结果，留原因。
     */
    @PostMapping("/{id}/reject")
    @SaCheckPermission(SortingPermission.SCALE_ACCEPT)
    @OperateLog
    public ResponseDTO<String> reject(@PathVariable("id") Long id,
            @Valid @RequestBody SortingScaleRejectForm form) {
        sortingScaleEventService.reject(id, form);
        return ResponseDTO.ok();
    }
}
