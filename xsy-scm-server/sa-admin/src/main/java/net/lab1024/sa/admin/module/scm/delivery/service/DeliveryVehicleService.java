package net.lab1024.sa.admin.module.scm.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import net.lab1024.sa.admin.module.scm.delivery.dao.DeliveryVehicleDao;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.DeliveryVehicleEntity;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

import java.util.*;

import static net.lab1024.sa.admin.module.scm.delivery.constant.DeliveryErrorCode.*;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class DeliveryVehicleService {
    private final DeliveryVehicleDao dao;

    public PageResult<DeliveryVehicleEntity> query(DeliveryQueryForm form) {
        var requested = DeliveryRouteQueryService.page(form);
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeliveryVehicleEntity>(requested.getCurrent(), requested.getSize(), requested.searchCount());
        var wrapper = new LambdaQueryWrapper<DeliveryVehicleEntity>();
        if (form.getKeyword() != null && !form.getKeyword().isBlank())
            wrapper.like(DeliveryVehicleEntity::getVehicleNo, form.getKeyword());
        if (form.getStatus() != null && !form.getStatus().isBlank())
            wrapper.eq(DeliveryVehicleEntity::getStatus, form.getStatus());
        wrapper.orderByAsc(DeliveryVehicleEntity::getVehicleNo, DeliveryVehicleEntity::getId);
        var rows = dao.selectList(page, wrapper);
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    public List<DeliveryVehicleEntity> options() {
        return dao.selectList(new LambdaQueryWrapper<DeliveryVehicleEntity>().eq(DeliveryVehicleEntity::getStatus, "ENABLED").orderByAsc(DeliveryVehicleEntity::getVehicleNo));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(DeliveryVehicleForm form) {
        var row = form.getId() == null ? new DeliveryVehicleEntity() : dao.selectById(form.getId());
        if (row == null) throw new ScmBusinessException(NOT_FOUND);
        if (form.getId() != null && !Objects.equals(row.getVersion(), form.getVersion()))
            throw new ScmBusinessException(VERSION_CONFLICT);
        BeanUtils.copyProperties(form, row, "id", "version");
        row.setVehicleNo(form.getVehicleNo().trim().toUpperCase(Locale.ROOT));
        DeliveryRouteService.stamp(row, form.getId() == null);
        try {
            if (form.getId() == null) dao.insert(row);
            else if (dao.updateById(row) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(DUPLICATE);
        }
        return row.getId();
    }
}
