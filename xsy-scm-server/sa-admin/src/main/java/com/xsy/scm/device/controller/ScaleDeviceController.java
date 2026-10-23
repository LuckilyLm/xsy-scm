package com.xsy.scm.device.controller;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.device.domain.form.ScaleActivateForm;
import com.xsy.scm.device.domain.form.ScaleHeartbeatForm;
import com.xsy.scm.device.domain.form.ScaleItemCompletionForm;
import com.xsy.scm.device.domain.form.ScaleJobActionForm;
import com.xsy.scm.device.domain.form.ScaleMeasurementForm;
import com.xsy.scm.device.domain.vo.ScaleAckVO;
import com.xsy.scm.device.domain.vo.ScaleDeviceConfigVO;
import com.xsy.scm.device.domain.vo.ScaleDeviceCredentialVO;
import com.xsy.scm.device.domain.vo.ScaleItemCompletionVO;
import com.xsy.scm.device.domain.vo.ScaleJobPageVO;
import com.xsy.scm.device.domain.vo.ScaleJobVO;
import com.xsy.scm.device.domain.vo.ScaleMeasurementVO;
import com.xsy.scm.device.service.ScaleDeviceAuthService;
import com.xsy.scm.device.service.ScaleDeviceJobService;
import com.xsy.scm.device.service.ScaleItemCompletionService;
import com.xsy.scm.device.service.ScaleMeasurementService;
import com.xsy.scm.device.service.ScaleUnitService;
import com.xsy.scm.device.support.ScaleDeviceResponse;
import com.xsy.scm.trace.domain.form.TraceScaleUnitIssueForm;
import com.xsy.scm.trace.domain.vo.TraceScaleUnitVO;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电子秤设备接入 v1。
 *
 * <p>
 * 设备主动出站访问服务端，服务端不连接现场串口或 Windows 内网端口。响应体用设备协议自己的 {@code OK} / {@code DUPLICATED} 字符串码，不复用后台的 {@code ResponseDTO}。
 *
 * <p>
 * 这里只做身份、作业队列与心跳。读数上报与多筐受理属于下一步，本版不会写入任何分拣数量。
 */
@RestController
@RequestMapping("/scm/device/scale/v1")
@Tag(name = "SCM 电子秤设备接入")
@RequiredArgsConstructor
public class ScaleDeviceController {

    private final ScaleDeviceAuthService scaleDeviceAuthService;

    private final ScaleDeviceJobService scaleDeviceJobService;

    private final ScaleMeasurementService scaleMeasurementService;

    private final ScaleItemCompletionService scaleItemCompletionService;

    private final ScaleUnitService scaleUnitService;

    /**
     * 用配对码换取设备凭证。配对码一次性，成功后立即失效。
     */
    @PostMapping("/agents/activate")
    public ScaleDeviceResponse<ScaleDeviceCredentialVO> activate(@Valid @RequestBody ScaleActivateForm form) {
        return ScaleDeviceResponse.ok(scaleDeviceAuthService.activate(form));
    }

    /**
     * 心跳：报告读数与打印状态。只更新观测字段，不代表云端已收齐历史事件。
     */
    @PostMapping("/agents/heartbeat")
    public ScaleDeviceResponse<ScaleAckVO> heartbeat(@Valid @RequestBody ScaleHeartbeatForm form) {
        scaleDeviceAuthService.heartbeat(form);
        return ScaleDeviceResponse.ok(new ScaleAckVO(true, OffsetDateTime.now()));
    }

    /**
     * 读取本设备配置：绑定仓库、工作站与凭证到期时间。
     */
    @GetMapping("/agents/config")
    public ScaleDeviceResponse<ScaleDeviceConfigVO> config() {
        return ScaleDeviceResponse.ok(scaleDeviceAuthService.config());
    }

    /**
     * 当前员工可作业的任务分页。游标原样回传即可，不据此推断任务完整性。
     */
    @GetMapping("/jobs")
    public ScaleDeviceResponse<ScaleJobPageVO> jobs(@RequestParam(value = "cursor", required = false) String cursor,
        @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return ScaleDeviceResponse.ok(scaleDeviceJobService.jobs(cursor, limit));
    }

    @GetMapping("/jobs/{jobId}")
    public ScaleDeviceResponse<ScaleJobVO> job(@PathVariable("jobId") String jobId) {
        return ScaleDeviceResponse.ok(scaleDeviceJobService.job(jobId));
    }

    /**
     * 领取作业租约。同一任务同时只能被一台设备持有。
     */
    @PostMapping("/jobs/{jobId}/claim")
    public ScaleDeviceResponse<ScaleJobVO> claim(@PathVariable("jobId") String jobId,
        @Valid @RequestBody ScaleJobActionForm form) {
        return ScaleDeviceResponse.ok(scaleDeviceJobService.claim(jobId, form));
    }

    /**
     * 释放作业租约。重复释放按幂等处理。
     */
    @PostMapping("/jobs/{jobId}/release")
    public ScaleDeviceResponse<ScaleJobVO> release(@PathVariable("jobId") String jobId,
        @Valid @RequestBody ScaleJobActionForm form) {
        return ScaleDeviceResponse.ok(scaleDeviceJobService.release(jobId, form));
    }

    /**
     * 上报一筐读数。只记录设备事实，不改分拣结果。
     *
     * <p>
     * 同一 {@code operationId} 或 {@code eventKey} 重复提交且内容一致时返回原记录并标注重复， 不报错也不重复累计。
     */
    @PostMapping("/measurements")
    public ScaleDeviceResponse<ScaleMeasurementVO> report(@Valid @RequestBody ScaleMeasurementForm form) {
        return ScaleDeviceResponse.ok(scaleMeasurementService.upload(form));
    }

    /**
     * 商品集合完成：把该明细当前全部待完成筐作为一个整体确认，写入正式分拣结果。
     *
     * <p>
     * 累计总量由服务端按筐重算；{@code manifestHash} 只用于确认筐集合未被并发改动。
     */
    @PostMapping("/items/{taskItemId}/complete")
    public ScaleDeviceResponse<ScaleItemCompletionVO> complete(@PathVariable("taskItemId") String taskItemId,
        @Valid @RequestBody ScaleItemCompletionForm form) {
        return ScaleDeviceResponse.ok(scaleItemCompletionService.complete(taskItemId, form));
    }

    /**
     * 该明细可选的批次：本仓、该商品、余额为正，按先进先出排序。
     *
     * <p>
     * 出库不按批次扣减，服务端不替现场做批次分配；这里只回答「还有哪些批次可用」。
     */
    @GetMapping("/items/{taskItemId}/lots")
    public ScaleDeviceResponse<List<TraceUnitVO.LotOption>> lots(@PathVariable("taskItemId") String taskItemId) {
        return ScaleDeviceResponse.ok(scaleUnitService.candidateLots(taskItemId));
    }

    /**
     * 为一筐称重结果发包装码。
     *
     * <p>
     * 幂等靠 {@code weighingEventId}：同一筐重试返回同一枚码，不会在标签上出现两个身份。
     */
    @PostMapping("/units/issue")
    public ScaleDeviceResponse<TraceScaleUnitVO> issueUnit(@Valid @RequestBody TraceScaleUnitIssueForm form) {
        return ScaleDeviceResponse.ok(scaleUnitService.issue(form, ScmOperator.current()));
    }

    /**
     * 补打标签：按码取回二维码链接与重量，不重新发码。
     */
    @GetMapping("/units/{unitCode}/label")
    public ScaleDeviceResponse<TraceScaleUnitVO> label(@PathVariable("unitCode") String unitCode) {
        return ScaleDeviceResponse.ok(scaleUnitService.label(unitCode));
    }
}
