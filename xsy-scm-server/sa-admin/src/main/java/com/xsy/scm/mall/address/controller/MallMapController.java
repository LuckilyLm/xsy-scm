package com.xsy.scm.mall.address.controller;

import com.xsy.scm.mall.account.support.MallRequestSupport;
import com.xsy.scm.mall.address.domain.vo.MallMapLocationVO;
import com.xsy.scm.mall.address.service.MallMapService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/mall/map")
@RequiredArgsConstructor
public class MallMapController {
    private final MallMapService mapService;

    @GetMapping("/search")
    public ResponseDTO<List<MallMapLocationVO>> search(@RequestParam String keyword,
            @RequestParam(required = false) String city) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(mapService.search(keyword, city));
    }

    @GetMapping("/reverse")
    public ResponseDTO<MallMapLocationVO> reverse(@RequestParam BigDecimal longitude,
            @RequestParam BigDecimal latitude) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(mapService.reverse(longitude, latitude));
    }
}
