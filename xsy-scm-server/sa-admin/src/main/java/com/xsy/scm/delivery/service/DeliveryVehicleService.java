package com.xsy.scm.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xsy.scm.delivery.dao.DeliveryVehicleDao;
import com.xsy.scm.delivery.domain.entity.DeliveryVehicleEntity;
import com.xsy.scm.delivery.domain.form.DeliveryQueryForm;
import com.xsy.scm.delivery.domain.form.DeliveryVehicleForm;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.DUPLICATE;
import static com.xsy.scm.delivery.constant.DeliveryErrorCode.NOT_FOUND;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class DeliveryVehicleService {
    private final DeliveryVehicleDao deliveryVehicleDao;

    public PageResult<DeliveryVehicleEntity> query(DeliveryQueryForm form) {
        var requested = DeliveryRouteQueryService.page(form);
        var page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeliveryVehicleEntity>(
                requested.getCurrent(), requested.getSize(), requested.searchCount());
        var wrapper = new LambdaQueryWrapper<DeliveryVehicleEntity>();
        if (form.getKeyword() != null && !form.getKeyword().isBlank())
            wrapper.like(DeliveryVehicleEntity::getVehicleNo, form.getKeyword());
        if (form.getStatus() != null && !form.getStatus().isBlank())
            wrapper.eq(DeliveryVehicleEntity::getStatus, form.getStatus());
        wrapper.orderByAsc(DeliveryVehicleEntity::getVehicleNo, DeliveryVehicleEntity::getId);
        var rows = deliveryVehicleDao.selectList(page, wrapper);
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    public List<DeliveryVehicleEntity> options() {
        return deliveryVehicleDao.selectList(new LambdaQueryWrapper<DeliveryVehicleEntity>()
                .eq(DeliveryVehicleEntity::getStatus, ScmEnableStatusEnum.ENABLED.name())
                .orderByAsc(DeliveryVehicleEntity::getVehicleNo));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(DeliveryVehicleForm form) {
        var row = form.getId() == null ? new DeliveryVehicleEntity() : deliveryVehicleDao.selectById(form.getId());
        if (row == null)
            throw new ScmBusinessException(NOT_FOUND);
        if (form.getId() != null && !Objects.equals(row.getVersion(), form.getVersion()))
            throw new ScmBusinessException(VERSION_CONFLICT);
        BeanUtils.copyProperties(form, row, "id", "version");
        row.setVehicleNo(form.getVehicleNo().trim().toUpperCase(Locale.ROOT));
        DeliveryRouteService.stamp(row, form.getId() == null);
        try {
            if (form.getId() == null)
                deliveryVehicleDao.insert(row);
            else if (deliveryVehicleDao.updateById(row) != 1)
                throw new ScmBusinessException(VERSION_CONFLICT);
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(DUPLICATE);
        }
        return row.getId();
    }
}
