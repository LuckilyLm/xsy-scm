package com.xsy.scm.mall.address.controller;

import com.xsy.scm.mall.account.support.MallRequestSupport;
import com.xsy.scm.mall.address.domain.form.MallAddressSaveForm;
import com.xsy.scm.mall.address.domain.vo.MallAddressVO;
import com.xsy.scm.mall.address.service.MallAddressService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/mall/addresses")
@RequiredArgsConstructor
public class MallAddressController {
    private final MallAddressService addressService;

    @GetMapping
    public ResponseDTO<List<MallAddressVO>> list() {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(addressService.list(MallRequestSupport.current()));
    }

    @GetMapping("/{id}")
    public ResponseDTO<MallAddressVO> detail(@PathVariable Long id) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(addressService.detail(MallRequestSupport.current(), id));
    }

    @PostMapping
    public ResponseDTO<List<MallAddressVO>> create(@Valid @RequestBody MallAddressSaveForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(addressService.create(MallRequestSupport.current(), form));
    }

    @PutMapping("/{id}")
    public ResponseDTO<List<MallAddressVO>> update(@PathVariable Long id, @Valid @RequestBody MallAddressSaveForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(addressService.update(MallRequestSupport.current(), id, form));
    }

    @PostMapping("/{id}/default")
    public ResponseDTO<List<MallAddressVO>> setDefault(@PathVariable Long id) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(addressService.setDefault(MallRequestSupport.current(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<List<MallAddressVO>> remove(@PathVariable Long id) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(addressService.remove(MallRequestSupport.current(), id));
    }
}
