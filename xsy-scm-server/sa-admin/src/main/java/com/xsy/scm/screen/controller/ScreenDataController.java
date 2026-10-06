package com.xsy.scm.screen.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.screen.domain.vo.ScreenBusinessVO;
import com.xsy.scm.screen.domain.vo.ScreenGeoVO;
import com.xsy.scm.screen.domain.vo.ScreenInventoryVO;
import com.xsy.scm.screen.domain.vo.ScreenPurchaseVO;
import com.xsy.scm.screen.domain.vo.ScreenTrendVO;
import com.xsy.scm.screen.permission.ScreenPermission;
import com.xsy.scm.screen.service.ScreenDataService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据大屏只读聚合接口。
 */
@RestController
@RequestMapping("/scm/screen")
@Tag(name = "SCM 数据大屏")
@RequiredArgsConstructor
public class ScreenDataController {

    private final ScreenDataService screenDataService;

    @GetMapping("/data/business")
    @SaCheckPermission(ScreenPermission.QUERY)
    public ResponseDTO<ScreenBusinessVO> business() {
        return ResponseDTO.ok(screenDataService.getBusinessData());
    }

    @GetMapping("/data/inventory")
    @SaCheckPermission(ScreenPermission.QUERY)
    public ResponseDTO<ScreenInventoryVO> inventory() {
        return ResponseDTO.ok(screenDataService.getInventoryData());
    }

    /**
     * 地理分布（地图 M1）：按市聚合的客户 / 供应商 / 仓库气泡与按省上卷的着色值。
     *
     * <p>
     * 与其余面板一样属于只读聚合，受 {@code scm:screen:query} 权限保护。
     */
    @GetMapping("/data/geo")
    @SaCheckPermission(ScreenPermission.QUERY)
    public ResponseDTO<ScreenGeoVO> geo() {
        return ResponseDTO.ok(screenDataService.getGeoData());
    }

    @GetMapping("/data/purchase")
    @SaCheckPermission(ScreenPermission.QUERY)
    public ResponseDTO<ScreenPurchaseVO> purchase() {
        return ResponseDTO.ok(screenDataService.getPurchaseData());
    }

    /**
     * 趋势数据（近 7 / 30 天）。
     *
     * <p>
     * 三张趋势图共用这一个接口：分开调用不仅要多发十几次请求，更麻烦的是各接口的「今天」可能落在不同的毫秒上，导致三条日期轴对不齐。
     *
     * @param range
     *            7d（默认）或 30d，其余取值按 7d 处理（不抛错，大屏不应因参数笔误而空白）
     */
    @GetMapping("/data/trend")
    @SaCheckPermission(ScreenPermission.QUERY)
    public ResponseDTO<ScreenTrendVO> trend(@RequestParam(value = "range", required = false) String range) {
        return ResponseDTO.ok(screenDataService.getTrendData(range));
    }
}
