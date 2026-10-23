package com.xsy.scm.preference.filter;

import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/preferences/filters")
public class UserFilterPresetController {
    private final UserFilterPresetService userFilterPresetService;

    @PostMapping("/list")
    public ResponseDTO<List<UserFilterPresetModel.Preset>> list(@Valid @RequestBody UserFilterPresetModel.View form) {
        return ResponseDTO.ok(userFilterPresetService.list(form.getViewKey()));
    }

    @PostMapping("/create")
    @OperateLog
    public ResponseDTO<Long> create(@Valid @RequestBody UserFilterPresetModel.Create form) {
        return ResponseDTO.ok(userFilterPresetService.create(form));
    }

    @PostMapping("/remove")
    @OperateLog
    public ResponseDTO<String> remove(@Valid @RequestBody UserFilterPresetModel.Remove form) {
        userFilterPresetService.remove(form);
        return ResponseDTO.ok();
    }
}
