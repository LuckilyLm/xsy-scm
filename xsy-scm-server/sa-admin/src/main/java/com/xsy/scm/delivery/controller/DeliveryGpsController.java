package com.xsy.scm.delivery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.delivery.domain.form.DeliveryGpsQueryForm;
import com.xsy.scm.delivery.domain.form.DeliveryGpsReportForm;
import com.xsy.scm.delivery.domain.vo.DeliveryGpsEventVO;
import com.xsy.scm.delivery.permission.DeliveryPermission;
import com.xsy.scm.delivery.service.DeliveryGpsService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GPS 轨迹上报与查询。
 *
 * <p>
 * 范围不由前端传参决定：两个端点都先按当前登录态的**司机维度**校验线路可见性
 * （不存在 41100，越权 30005）。司机只能上报和查看自己线路的轨迹，调度按全量范围看。
 *
 * <p>
 * 上报带 {@code eventKey} 幂等键，重复上报返回既有记录并置 {@code duplicated}，不报错。
 */
@RestController
@RequestMapping("/scm/delivery/gps")
@Tag(name = "SCM 配送轨迹")
@RequiredArgsConstructor
public class DeliveryGpsController {

    private final DeliveryGpsService deliveryGpsService;

    @PostMapping("/report")
    @SaCheckPermission(DeliveryPermission.GPS_REPORT)
    public ResponseDTO<DeliveryGpsEventVO> report(@Valid @RequestBody DeliveryGpsReportForm form) {
        return ResponseDTO.ok(deliveryGpsService.report(form));
    }

    /**
     * 轨迹查询 / 回放（按采集时间升序）。
     */
    @PostMapping("/query")
    @SaCheckPermission(DeliveryPermission.GPS_QUERY)
    public ResponseDTO<List<DeliveryGpsEventVO>> query(@Valid @RequestBody DeliveryGpsQueryForm form) {
        return ResponseDTO.ok(deliveryGpsService.query(form));
    }
}
