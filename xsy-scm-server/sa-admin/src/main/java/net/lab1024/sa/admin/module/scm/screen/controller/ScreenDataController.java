package net.lab1024.sa.admin.module.scm.screen.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenBusinessVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenInventoryVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenPurchaseVO;
import net.lab1024.sa.admin.module.scm.screen.service.ScreenDataService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
