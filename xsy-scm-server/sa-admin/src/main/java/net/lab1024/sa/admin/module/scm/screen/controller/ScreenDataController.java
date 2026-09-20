package net.lab1024.sa.admin.module.scm.screen.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenBusinessVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenInventoryVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenPurchaseVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenTrendVO;
import net.lab1024.sa.admin.module.scm.screen.service.ScreenDataService;
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
    @SaCheckPermission("scm:screen:query")
    public ResponseDTO<ScreenBusinessVO> business() {
        return ResponseDTO.ok(screenDataService.getBusinessData());
    }

    @GetMapping("/data/inventory")
    @SaCheckPermission("scm:screen:query")
    public ResponseDTO<ScreenInventoryVO> inventory() {
        return ResponseDTO.ok(screenDataService.getInventoryData());
    }

    @GetMapping("/data/purchase")
    @SaCheckPermission("scm:screen:query")
    public ResponseDTO<ScreenPurchaseVO> purchase() {
        return ResponseDTO.ok(screenDataService.getPurchaseData());
    }

    /**
     * 趋势数据（近 7 / 30 天）。
     *
     * <p>三张趋势图共用这一个接口：分开调用不仅要多发十几次请求，
     * 更麻烦的是各接口的「今天」可能落在不同的毫秒上，导致三条日期轴对不齐。
     *
     * @param range 7d（默认）或 30d，其余取值按 7d 处理（不抛错，大屏不应因参数笔误而空白）
     */
    @GetMapping("/data/trend")
    @SaCheckPermission("scm:screen:query")
    public ResponseDTO<ScreenTrendVO> trend(@RequestParam(value = "range", required = false) String range) {
        return ResponseDTO.ok(screenDataService.getTrendData(range));
    }
}
