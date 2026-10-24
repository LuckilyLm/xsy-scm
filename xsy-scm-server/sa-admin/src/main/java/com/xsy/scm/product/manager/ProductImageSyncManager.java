package com.xsy.scm.product.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.product.dao.ProductImageDao;
import com.xsy.scm.product.constant.ScmProductImageTypeEnum;
import com.xsy.scm.product.domain.entity.ProductImageEntity;
import com.xsy.scm.product.domain.form.ProductImageForm;
import net.lab1024.sa.base.module.support.file.service.FileService;
import net.lab1024.sa.base.module.support.file.service.FileRelationService;
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xsy.scm.product.constant.ProductErrorCode.IMAGE_INVALID;
import static com.xsy.scm.product.constant.ProductErrorCode.IMAGE_NOT_PUBLIC;
import static com.xsy.scm.product.constant.ProductErrorCode.VERSION_CONFLICT;

@Component
@RequiredArgsConstructor
public class ProductImageSyncManager {
    /**
     * 目录前缀只有一个来源：上传白名单枚举，避免业务侧与存储侧各写一份定义。
     */
    private static final String PUBLIC_IMAGE_FOLDER = FileFolderTypeEnum.PUBLIC_IMAGE.getFolder();
    /**
     * 图片类型与主图标记各自独立；只允许绑定 public/ 目录下的文件。
     */
    private static final String PUBLIC_FOLDER_PREFIX = FileFolderTypeEnum.FOLDER_PUBLIC + "/";
    private final ProductImageDao productImageDao;
    private final FileService fileService;
    private final FileRelationService fileRelationService;

    public List<ProductImageEntity> existing(Long spuId) {
        return productImageDao
            .selectList(new LambdaQueryWrapper<ProductImageEntity>().eq(ProductImageEntity::getSpuId, spuId)
                .orderByAsc(ProductImageEntity::getSortOrder, ProductImageEntity::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void sync(Long spuId, ProductImageChangeSet changes) {
        List<ProductImageForm> requested = new ArrayList<>(changes.updated());
        requested.addAll(changes.inserted());
        // 文件是否存在由文件中心验证，图片权限只检查 public/ 路径，不依赖 URL。
        Map<String, FileVO> metadata = fileService
            .getFileMetadata(requested.stream().map(ProductImageForm::getFileKey).toList()).stream()
            .filter(Objects::nonNull).collect(Collectors.toMap(FileVO::getFileKey, Function.identity(), (a, b) -> a));
        for (var form : requested)
            if (!metadata.containsKey(form.getFileKey()))
                throw new ScmBusinessException(IMAGE_INVALID);
        for (var form : requested)
            requirePublicImageKey(form);
        productImageDao.clearPrimary(spuId);
        for (var form : changes.updated()) {
            var entity = entity(spuId, form, metadata.get(form.getFileKey()), false);
            entity.setId(form.getImageId());
            entity.setVersion(form.getVersion());
            if (productImageDao.updateById(entity) != 1)
                throw new ScmBusinessException(VERSION_CONFLICT);
        }
        for (var form : changes.inserted()) {
            var entity = entity(spuId, form, metadata.get(form.getFileKey()), true);
            entity.setVersion(0);
            entity.setCreatedAt(entity.getUpdatedAt());
            entity.setCreatedBy(entity.getUpdatedBy());
            productImageDao.insert(entity);
        }
        remove(changes.removedIds());
        // 商品图都落在公开前缀后，这里恒为空清单；rebind 会收回历史私有 key 的
        // 关系行收回来：删图或搬到公开前缀后都必须同时收回读取权，只增不减会让已删附件长期可读。
        fileRelationService.rebind(FileRelationBizTypeEnum.PRODUCT, spuId, existing(spuId).stream()
            .map(ProductImageEntity::getFileKey).filter(key -> !key.startsWith(PUBLIC_FOLDER_PREFIX)).toList());
    }

    public void remove(List<Long> ids) {
        if (ids.isEmpty())
            return;
        productImageDao.update(null,
            new LambdaUpdateWrapper<ProductImageEntity>().in(ProductImageEntity::getId, ids)
                .set(ProductImageEntity::getUpdatedAt, OffsetDateTime.now())
                .set(ProductImageEntity::getUpdatedBy, ScmOperator.current()).setSql("version = version + 1"));
        productImageDao.deleteByIds(ids);
    }

    /**
     * 商品只能绑定文件模块确认存在的公开图片，不允许把他人的私有附件设为商品图片。
     */
    private void requirePublicImageKey(ProductImageForm form) {
        if (!form.getFileKey().startsWith(PUBLIC_IMAGE_FOLDER)) {
            throw new ScmBusinessException(IMAGE_NOT_PUBLIC);
        }
    }

    private ProductImageEntity entity(Long spuId, ProductImageForm form, FileVO file, boolean inserting) {
        var entity = new ProductImageEntity();
        entity.setSpuId(spuId);
        entity.setFileKey(file.getFileKey());
        // 图片中心允许服务端按商品编码重命名展示名；未指定时沿用上传时的原始文件名
        entity.setFileName(
            form.getFileName() != null && !form.getFileName().isBlank() ? form.getFileName() : file.getFileName());
        entity.setFileSize(file.getFileSize() == null ? null : file.getFileSize().longValue());
        entity.setPrimaryFlag(form.getPrimaryFlag());
        entity.setSortOrder(form.getSortOrder());
        // 只有新增行才落内容角色；已有行留 null，让非空更新策略把 image_type 整列排除在 UPDATE 之外。
        // 回写读到的现值并不安全：同一会话内的旁路改库不会刷新 MyBatis 的一级缓存，
        // 一旦按过期实体回写，「改个市场价或切主图」就会把详情图静默降级成图集图。
        if (inserting) {
            entity.setImageType(
                form.getImageType() == null ? ScmProductImageTypeEnum.GALLERY.name() : form.getImageType());
        }
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setUpdatedBy(ScmOperator.current());
        return entity;
    }
}
