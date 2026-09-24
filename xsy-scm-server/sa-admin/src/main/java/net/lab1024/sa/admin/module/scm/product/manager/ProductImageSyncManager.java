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
import net.lab1024.sa.base.module.support.file.service.FileRelationService;
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    /** 公开前缀按 {@code FOLDER_PUBLIC} 判定，不用 PUBLIC_IMAGE 的完整目录，新增公开目录时这里不必跟着改。 */
    private static final String PUBLIC_FOLDER_PREFIX = FileFolderTypeEnum.FOLDER_PUBLIC + "/";
    private final ProductImageDao dao;
    private final FileService files;
    private final FileRelationService relations;

    public List<ProductImageEntity> existing(Long spuId) {
        return dao.selectList(new LambdaQueryWrapper<ProductImageEntity>().eq(ProductImageEntity::getSpuId, spuId)
                .orderByAsc(ProductImageEntity::getSortOrder, ProductImageEntity::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void sync(Long spuId, ProductImageChangeSet changes) {
        List<ProductImageForm> requested = new ArrayList<>(changes.updated());
        requested.addAll(changes.inserted());
        // File module remains the authority for existence and metadata; URLs are never resolved on
        // the write path (the caller may not own these keys — resolving them would be an ungarded
        // read). Public-prefix binding is enforced by requirePublicImageKey, not by URL resolution.
        Map<String,FileVO> metadata=files.getFileMetadata(requested.stream().map(ProductImageForm::getFileKey).toList())
                .stream().filter(Objects::nonNull).collect(Collectors.toMap(FileVO::getFileKey,Function.identity(),(a,b)->a));
        for (var form:requested) if (!metadata.containsKey(form.getFileKey())) throw new ScmBusinessException(IMAGE_INVALID);
        for (var form:requested) requirePublicImageKey(form);
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
        // 商品图全部落在公开前缀后（FA-3 / V58），这里恒为空清单，rebind 的作用是把历史私有 key 的
        // 关系行收回来：删图或搬到公开前缀后都必须同时收回读取权，只增不减会让已删附件长期可读。
        relations.rebind(FileRelationBizTypeEnum.PRODUCT, spuId, existing(spuId).stream()
                .map(ProductImageEntity::getFileKey)
                .filter(key -> !key.startsWith(PUBLIC_FOLDER_PREFIX)).toList());
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
     *
     * <p>这里不再给「沿用本行原有私有 key」留过渡例外：FA-3（V58）已把存量 key 搬到
     * {@code public/image/} 并把同一判据落成数据库 CHECK {@code ck_product_image_public_file_key}，
     * 「私有前缀的活商品图」已经不是可能存在的状态。例外若留着，任何一次编辑都会把它重新养大 ——
     * 判据与库约束不一致时，绕过服务层的写入就能造出只有这里拒、库里却收下的行。
     */
    private void requirePublicImageKey(ProductImageForm form) {
        if (!form.getFileKey().startsWith(PUBLIC_IMAGE_FOLDER)) {
            throw new ScmBusinessException(IMAGE_NOT_PUBLIC);
        }
    }
    private ProductImageEntity entity(Long spuId,ProductImageForm form,FileVO file,boolean inserting) {
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
