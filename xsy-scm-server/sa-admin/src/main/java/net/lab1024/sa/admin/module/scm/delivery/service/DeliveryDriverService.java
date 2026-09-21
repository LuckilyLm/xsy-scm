package net.lab1024.sa.admin.module.scm.delivery.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import net.lab1024.sa.admin.module.scm.delivery.dao.DeliveryDriverDao;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.DeliveryDriverEntity;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import java.util.*;
import static net.lab1024.sa.admin.module.scm.delivery.constant.DeliveryErrorCode.*;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
@Service @RequiredArgsConstructor
public class DeliveryDriverService {
    private final DeliveryDriverDao dao;
    public PageResult<DeliveryDriverEntity> query(DeliveryQueryForm form) {
        var requested=DeliveryRouteQueryService.page(form);
        var page=new com.baomidou.mybatisplus.extension.plugins.pagination.Page<DeliveryDriverEntity>(requested.getCurrent(),requested.getSize(),requested.searchCount());
        var wrapper=new LambdaQueryWrapper<DeliveryDriverEntity>();
        if(form.getKeyword()!=null && !form.getKeyword().isBlank()) wrapper.like(DeliveryDriverEntity::getDriverCode,form.getKeyword());
        if(form.getStatus()!=null && !form.getStatus().isBlank()) wrapper.eq(DeliveryDriverEntity::getStatus,form.getStatus());
        wrapper.orderByAsc(DeliveryDriverEntity::getDriverCode,DeliveryDriverEntity::getId);
        var rows=dao.selectList(page,wrapper);return SmartPageUtil.convert2PageResult(page,rows);
    }
    public List<DeliveryDriverEntity> options() {return dao.selectList(new LambdaQueryWrapper<DeliveryDriverEntity>().eq(DeliveryDriverEntity::getStatus,"ENABLED").orderByAsc(DeliveryDriverEntity::getDriverCode));}
    @Transactional(rollbackFor=Exception.class)
    public Long save(DeliveryDriverForm form) {
        var row=form.getId()==null?new DeliveryDriverEntity():dao.selectById(form.getId());
        if(row==null) throw new ScmBusinessException(NOT_FOUND);
        if(form.getId()!=null && !Objects.equals(row.getVersion(),form.getVersion())) throw new ScmBusinessException(VERSION_CONFLICT);
        BeanUtils.copyProperties(form,row,"id","version");
        row.setDriverCode(form.getDriverCode().trim().toUpperCase(Locale.ROOT));
        DeliveryRouteService.stamp(row,form.getId()==null);
        try {if(form.getId()==null) dao.insert(row);else if(dao.updateById(row)!=1) throw new ScmBusinessException(VERSION_CONFLICT);}
        catch(DuplicateKeyException e) {throw new ScmBusinessException(DUPLICATE);}
        return row.getId();
    }
}
