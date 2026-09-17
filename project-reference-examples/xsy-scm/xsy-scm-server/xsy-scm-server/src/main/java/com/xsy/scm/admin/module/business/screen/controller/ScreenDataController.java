package com.xsy.scm.admin.module.business.screen.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.screen.domain.form.ScreenDataQueryForm;
import com.xsy.scm.admin.module.business.screen.domain.vo.ScreenBusinessVO;
import com.xsy.scm.admin.module.business.screen.domain.vo.ScreenPurchaseVO;
import com.xsy.scm.admin.module.business.screen.domain.vo.ScreenStockVO;
import com.xsy.scm.admin.module.business.screen.service.ScreenDataService;
import com.xsy.scm.base.common.domain.ResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大屏数据 Controller（只读）
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_SCREEN)
public class ScreenDataController {

    @Resource
    private ScreenDataService screenDataService;

    @Operation(summary = "经营大屏数据 @author xsy-scm")
    @PostMapping("/screen/data/business")
    @SaCheckPermission("screenData:query")
    public ResponseDTO<ScreenBusinessVO> business(@RequestBody @Valid ScreenDataQueryForm queryForm) {
        return screenDataService.business(queryForm);
    }

    @Operation(summary = "库存大屏数据 @author xsy-scm")
    @GetMapping("/screen/data/stock")
    @SaCheckPermission("screenData:query")
    public ResponseDTO<ScreenStockVO> stock() {
        return screenDataService.stock();
    }

    @Operation(summary = "采购大屏数据 @author xsy-scm")
    @PostMapping("/screen/data/purchase")
    @SaCheckPermission("screenData:query")
    public ResponseDTO<ScreenPurchaseVO> purchase(@RequestBody @Valid ScreenDataQueryForm queryForm) {
        return screenDataService.purchase(queryForm);
    }
}
