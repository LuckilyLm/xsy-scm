package com.xsy.scm.dashboard.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.dashboard.domain.vo.ScmDashboardCardVO;
import com.xsy.scm.dashboard.domain.vo.ScmDashboardTrendVO;
import com.xsy.scm.dashboard.permission.DashboardPermission;
import com.xsy.scm.dashboard.service.ScmDashboardService;
import com.xsy.scm.metrics.domain.RankItem;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页「供应链工作台」只读聚合入口。
 *
 * <p>
 * {@code scm:dashboard:query} 只授权访问工作台本身，<b>不隐含任何领域可见性</b>：KPI 卡片按「入口权限 ∩ 领域权限」 逐卡裁剪（无权整卡省略），趋势与排行按指标 /
 * 维度校验各自的领域权限（缺权直接拒绝）。本接口不写业务表、 不发消息、不落快照。
 *
 * <p>
 * 与 {@code ScmTodoController} 共用 {@code /scm/dashboard} 前缀：待办与工作台是同一条产品线（首页）， 分成两个控制器只是为了让「待办」与「经营指标」各自的权限语义独立演进。
 */
@RestController
@RequestMapping("/scm/dashboard")
@Tag(name = "SCM 供应链工作台")
@RequiredArgsConstructor
public class ScmDashboardController {

    @Resource
    private ScmDashboardService dashboardService;

    /**
     * KPI 卡片：只返回业务语义（key / 值 / 单位 / 跳转），中文文案与配色由前端决定。
     */
    @GetMapping("/overview")
    @SaCheckPermission(DashboardPermission.QUERY)
    public ResponseDTO<List<ScmDashboardCardVO>> overview() {
        return ResponseDTO.ok(dashboardService.overview());
    }

    /**
     * 趋势：{@code metric} 取 {@code sales / purchase / inventory}，{@code range} 取 {@code 7d / 30d}。
     */
    @GetMapping("/trend")
    @SaCheckPermission(DashboardPermission.QUERY)
    public ResponseDTO<ScmDashboardTrendVO> trend(@RequestParam("metric") String metric,
            @RequestParam(value = "range", required = false, defaultValue = "7d") String range) {
        return ResponseDTO.ok(dashboardService.trend(metric, range));
    }

    /**
     * 排行：{@code dimension} 取 {@code customer / product}，{@code limit} 默认 5、上限为指标层已取回的条数。
     */
    @GetMapping("/ranking")
    @SaCheckPermission(DashboardPermission.QUERY)
    public ResponseDTO<List<RankItem>> ranking(@RequestParam("dimension") String dimension,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ResponseDTO.ok(dashboardService.ranking(dimension, limit));
    }
}
