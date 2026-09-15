package net.lab1024.sa.admin.module.scm.product.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.ProductImageDao;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductImageEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductImageForm;
import net.lab1024.sa.base.module.support.file.service.FileService;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode.*;

@Component
@RequiredArgsConstructor
public class ProductImageSyncManager {
    private final ProductImageDao dao;
    private final FileService files;
    public List<ProductImageEntity> existing(Long spuId) {
        return dao.selectList(new LambdaQueryWrapper<ProductImageEntity>().eq(ProductImageEntity::getSpuId,spuId)
                .orderByAsc(ProductImageEntity::getSortOrder,ProductImageEntity::getId));
    }
    public void sync(Long spuId,ProductImageChangeSet changes) {
        List<ProductImageForm> requested=new ArrayList<>(changes.updated()); requested.addAll(changes.inserted());
        // File module remains the authority for existence, metadata and URL generation.
        Map<String,FileVO> metadata=files.getFileList(requested.stream().map(ProductImageForm::getFileKey).toList())
                .stream().filter(Objects::nonNull).collect(Collectors.toMap(FileVO::getFileKey,Function.identity(),(a,b)->a));
        for (var form:requested) if (!metadata.containsKey(form.getFileKey())) throw new ScmBusinessException(IMAGE_INVALID);
        dao.clearPrimary(spuId);
        for (var form:changes.updated()) {
            var entity=entity(spuId,form,metadata.get(form.getFileKey())); entity.setId(form.getImageId()); entity.setVersion(form.getVersion());
            if (dao.updateById(entity)!=1) throw new ScmBusinessException(VERSION_CONFLICT);
        }
        for (var form:changes.inserted()) {
            var entity=entity(spuId,form,metadata.get(form.getFileKey())); entity.setVersion(0);
            entity.setCreatedAt(entity.getUpdatedAt()); entity.setCreatedBy(entity.getUpdatedBy()); dao.insert(entity);
        }
        remove(changes.removedIds());
    }
    public void remove(List<Long> ids) {
        if(ids.isEmpty()) return;
        dao.update(null,new LambdaUpdateWrapper<ProductImageEntity>().in(ProductImageEntity::getId,ids)
            .set(ProductImageEntity::getUpdatedAt,OffsetDateTime.now()).set(ProductImageEntity::getUpdatedBy,ScmOperator.current())
            .setSql("version = version + 1"));
        dao.deleteByIds(ids);
    }
    private ProductImageEntity entity(Long spuId,ProductImageForm form,FileVO file) {
        if (file.getFileUrl()==null || file.getFileUrl().isBlank()) throw new ScmBusinessException(IMAGE_INVALID);
        var entity=new ProductImageEntity(); entity.setSpuId(spuId); entity.setFileKey(file.getFileKey());
        entity.setFileUrl(file.getFileUrl()); entity.setFileName(file.getFileName());
        entity.setFileSize(file.getFileSize()==null ? null : file.getFileSize().longValue());
        entity.setPrimaryFlag(form.getPrimaryFlag()); entity.setSortOrder(form.getSortOrder());
        entity.setUpdatedAt(OffsetDateTime.now()); entity.setUpdatedBy(ScmOperator.current()); return entity;
    }
}
