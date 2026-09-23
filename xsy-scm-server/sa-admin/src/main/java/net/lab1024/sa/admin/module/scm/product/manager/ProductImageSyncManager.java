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
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
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
    /** 目录前缀只有一个来源：上传白名单枚举，避免业务侧与存储侧各写一份口径。 */
    private static final String PUBLIC_IMAGE_FOLDER = FileFolderTypeEnum.PUBLIC_IMAGE.getFolder();
    /**
     * 图集：新增行的缺省归类。V49 之后 image_type 只表达内容角色，<b>不</b>再派生自 primaryFlag——
     * 主图唯一事实是 is_primary，否则「切主图」会顺带改写图片类型，等于保留第二个主图事实源。
     */
    private static final String IMAGE_TYPE_GALLERY = "GALLERY";
    private final ProductImageDao dao;
    private final FileService files;

    public List<ProductImageEntity> existing(Long spuId) {
        return dao.selectList(new LambdaQueryWrapper<ProductImageEntity>().eq(ProductImageEntity::getSpuId, spuId)
                .orderByAsc(ProductImageEntity::getSortOrder, ProductImageEntity::getId));
    }

    public void sync(Long spuId, ProductImageChangeSet changes) {
        List<ProductImageForm> requested = new ArrayList<>(changes.updated());
        requested.addAll(changes.inserted());
        // File module remains the authority for existence, metadata and URL generation.
        Map<String,FileVO> metadata=files.getFileList(requested.stream().map(ProductImageForm::getFileKey).toList())
                .stream().filter(Objects::nonNull).collect(Collectors.toMap(FileVO::getFileKey,Function.identity(),(a,b)->a));
        for (var form:requested) if (!metadata.containsKey(form.getFileKey())) throw new ScmBusinessException(IMAGE_INVALID);
        Map<Long,ProductImageEntity> persisted=existing(spuId).stream()
                .collect(Collectors.toMap(ProductImageEntity::getId,Function.identity()));
        for (var form:requested) requirePublicImageKey(form,persisted);
        dao.clearPrimary(spuId);
        for (var form : changes.updated()) {
            var entity = entity(spuId, form, metadata.get(form.getFileKey()), false);
            entity.setId(form.getImageId());
            entity.setVersion(form.getVersion());
            if (dao.updateById(entity) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        }
        for (var form : changes.inserted()) {
            var entity = entity(spuId, form, metadata.get(form.getFileKey()), true);
            entity.setVersion(0);
            entity.setCreatedAt(entity.getUpdatedAt());
            entity.setCreatedBy(entity.getUpdatedBy());
            dao.insert(entity);
        }
        remove(changes.removedIds());
    }

    public void remove(List<Long> ids) {
        if (ids.isEmpty()) return;
        dao.update(null, new LambdaUpdateWrapper<ProductImageEntity>().in(ProductImageEntity::getId, ids)
                .set(ProductImageEntity::getUpdatedAt, OffsetDateTime.now()).set(ProductImageEntity::getUpdatedBy, ScmOperator.current())
                .setSql("version = version + 1"));
        dao.deleteByIds(ids);
    }
    /**
     * 商品图是面向客户的展示资产，新增或换绑只能引用公开图片目录。
     * 只读 fileKey 前缀不够：存在性由文件模块证明，而「谁的附件」不在这条链上——
     * 少了这道判断，改商品权限就等于把他人私有附件晋升为所有查看者可读。
     * 本裁决之前落库的行仍挂着私有 key，仅「沿用该行原有 key」放行，
     * 否则改排序或切主图会被历史数据挡住，而换绑成另一个私有 key 依旧拒绝。
     */
    private void requirePublicImageKey(ProductImageForm form,Map<Long,ProductImageEntity> persisted) {
        String fileKey=form.getFileKey();
        var row=persisted.get(form.getImageId());
        if (fileKey.startsWith(PUBLIC_IMAGE_FOLDER) || (row != null && fileKey.equals(row.getFileKey()))) return;
        throw new ScmBusinessException(IMAGE_NOT_PUBLIC);
    }
    private ProductImageEntity entity(Long spuId,ProductImageForm form,FileVO file,boolean inserting) {
        if (file.getFileUrl()==null || file.getFileUrl().isBlank()) throw new ScmBusinessException(IMAGE_INVALID);
        var entity=new ProductImageEntity(); entity.setSpuId(spuId); entity.setFileKey(file.getFileKey());
        entity.setFileName(file.getFileName());
        entity.setFileSize(file.getFileSize()==null ? null : file.getFileSize().longValue());
        entity.setPrimaryFlag(form.getPrimaryFlag()); entity.setSortOrder(form.getSortOrder());
        // 只有新增行才落内容角色；已有行留 null，让非空更新策略把 image_type 整列排除在 UPDATE 之外。
        // 回写读到的现值并不安全：同一会话内的旁路改库不会刷新 MyBatis 的一级缓存，
        // 一旦按过期实体回写，「改个市场价或切主图」就会把详情图静默降级成图集图。
        if (inserting) entity.setImageType(form.getImageType() == null ? IMAGE_TYPE_GALLERY : form.getImageType());
        entity.setUpdatedAt(OffsetDateTime.now()); entity.setUpdatedBy(ScmOperator.current()); return entity;
    }
}
