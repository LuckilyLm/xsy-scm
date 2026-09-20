package net.lab1024.sa.admin.module.scm.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.ProductTagDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductTagRelationDao;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductTagEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.*;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSpuTagVO;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductTagVO;
import net.lab1024.sa.admin.module.scm.product.manager.ProductAggregateValidator;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import static net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode.*;

/**
 * 商品标签字典与打标关系。标签是独立关系表，商品扩展字段一律不退化成 tag1/tag2 列。
 */
@Service
@RequiredArgsConstructor
public class ProductTagService {
    private final ProductTagDao dao;
    private final ProductTagRelationDao relationDao;

    public List<ProductTagVO> list(ProductAssistantQueryForm query) {
        return dao.selectWithProductCount(query == null ? new ProductAssistantQueryForm() : query);
    }

    public List<ProductTagVO> options() {
        var query = new ProductAssistantQueryForm();
        query.setStatus("ENABLED");
        return dao.selectWithProductCount(query);
    }

    /** 供商品详情与列表富化：一次取回多个 SPU 的标签，停用标签照样返回，只影响能否新挂。 */
    public Map<Long, List<ProductSpuTagVO>> bySpuIds(Collection<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) return Map.of();
        Map<Long, List<ProductSpuTagVO>> grouped = new LinkedHashMap<>();
        for (var row : relationDao.selectBySpuIds(List.copyOf(spuIds))) grouped.computeIfAbsent(row.getSpuId(), k -> new ArrayList<>()).add(row);
        return grouped;
    }

    @Transactional
    public Long add(ProductTagAddForm form) {
        var entity = new ProductTagEntity();
        BeanUtils.copyProperties(form, entity, "version");
        entity.setTagCode(ProductAggregateValidator.normalizeCode(form.getTagCode()));
        entity.setName(form.getName().trim());
        assertUnique(entity.getTagCode(), entity.getName(), null);
        stamp(entity);
        entity.setCreatedAt(entity.getUpdatedAt());
        entity.setCreatedBy(entity.getUpdatedBy());
        try { dao.insert(entity); } catch (DuplicateKeyException e) { throw duplicate(e); }
        return entity.getId();
    }

    /** 编码与名称可改：关系表按 tag_id 关联，改名不影响已打标商品。 */
    @Transactional
    public void update(ProductTagUpdateForm form) {
        var entity = require(form.getTagId(), form.getVersion());
        BeanUtils.copyProperties(form, entity, "version", "tagId");
        entity.setTagCode(ProductAggregateValidator.normalizeCode(form.getTagCode()));
        entity.setName(form.getName().trim());
        assertUnique(entity.getTagCode(), entity.getName(), entity.getId());
        entity.setVersion(form.getVersion());
        stamp(entity);
        try {
            if (dao.updateById(entity) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        } catch (DuplicateKeyException e) { throw duplicate(e); }
    }

    @Transactional
    public void delete(ProductTagKeyForm form) {
        var entity = dao.selectForUpdate(form.getTagId());
        if (entity == null) throw new ScmBusinessException(TAG_NOT_FOUND);
        if (!Objects.equals(entity.getVersion(), form.getVersion())) throw new ScmBusinessException(VERSION_CONFLICT);
        if (dao.selectVoById(entity.getId()).getProductCount() > 0) throw new ScmBusinessException(TAG_REFERENCED);
        stamp(entity);
        if (dao.updateById(entity) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        dao.deleteById(entity.getId());
    }

    /**
     * REPLACE 语义：活动关系收敛到 tagIds 全集，多退少补。整批一次锁定标签行，
     * 与删除标签互斥；调用方必须已持有这些 SPU 的行锁。
     * 这里不校验标签可用性：新建与批量打标由调用方全量校验，单商品编辑只校验新增绑定。
     */
    @Transactional
    public void replaceTags(Collection<Long> spuIds, Collection<Long> tagIds) {
        var targets = distinct(spuIds);
        if (targets.isEmpty()) return;
        var tags = distinct(tagIds);
        var operator = ScmOperator.current();
        relationDao.softDeleteExcept(targets, tags, operator);
        if (!tags.isEmpty()) relationDao.insertIgnore(targets, tags, operator);
    }

    @Transactional
    public void addTags(Collection<Long> spuIds, Collection<Long> tagIds) {
        var targets = distinct(spuIds);
        var tags = distinct(tagIds);
        if (targets.isEmpty() || tags.isEmpty()) return;
        assertUsable(tags);
        relationDao.insertIgnore(targets, tags, ScmOperator.current());
    }

    /** 移除标签不要求标签仍可用，否则停用标签再也无法从商品上摘掉。 */
    @Transactional
    public void removeTags(Collection<Long> spuIds, Collection<Long> tagIds) {
        var targets = distinct(spuIds);
        var tags = distinct(tagIds);
        if (targets.isEmpty() || tags.isEmpty()) return;
        relationDao.softDelete(targets, tags, ScmOperator.current());
    }

    /** 商品档案删除时清掉其标签关系，避免标签引用数虚高。 */
    public void untagProducts(Collection<Long> spuIds) {
        var targets = distinct(spuIds);
        if (!targets.isEmpty()) relationDao.softDeleteBySpuIds(targets, ScmOperator.current());
    }

    /** 新挂的标签必须存在且启用；按 id 升序加锁，与并发删除标签串行。 */
    public void assertUsable(Collection<Long> tagIds) {
        var ids = distinct(tagIds);
        if (ids.isEmpty()) return;
        var found = dao.lockByIds(ids);
        if (found.size() != ids.size()) throw new ScmBusinessException(TAG_NOT_FOUND);
        if (found.stream().anyMatch(t -> !"ENABLED".equals(t.getStatus()))) throw new ScmBusinessException(TAG_NOT_USABLE);
    }

    /**
     * 单商品编辑口径：只校验本次新增的绑定。已绑定的停用标签允许原样保留，
     * 否则运营改一个无关字段就会被迫先摘标签；要换掉时前端本来就会重选。
     */
    public void assertNewBindings(Long spuId, Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return;
        var retained = new HashSet<>(relationDao.selectTagIds(spuId));
        assertUsable(tagIds.stream().filter(id -> !retained.contains(id)).toList());
    }

    /** 与 uk_product_tag_code_active / uk_product_tag_name_active 同域的应用级预检。 */
    private void assertUnique(String code, String name, Long self) {
        if (dao.selectCount(new LambdaQueryWrapper<ProductTagEntity>().eq(ProductTagEntity::getTagCode, code)
                .ne(self != null, ProductTagEntity::getId, self)) > 0) throw new ScmBusinessException(TAG_CODE_DUPLICATE);
        if (dao.selectCount(new LambdaQueryWrapper<ProductTagEntity>().eq(ProductTagEntity::getName, name)
                .ne(self != null, ProductTagEntity::getId, self)) > 0) throw new ScmBusinessException(TAG_NAME_DUPLICATE);
    }

    private ProductTagEntity require(Long id, Integer version) {
        var entity = dao.selectById(id);
        if (entity == null) throw new ScmBusinessException(TAG_NOT_FOUND);
        if (!Objects.equals(entity.getVersion(), version)) throw new ScmBusinessException(VERSION_CONFLICT);
        return entity;
    }

    private List<Long> distinct(Collection<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }

    private void stamp(ProductTagEntity entity) {
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setUpdatedBy(ScmOperator.current());
    }

    private ScmBusinessException duplicate(DuplicateKeyException e) {
        String constraint = e.getMostSpecificCause().getMessage();
        if (constraint != null && constraint.contains("uk_product_tag_name_active")) return new ScmBusinessException(TAG_NAME_DUPLICATE);
        return new ScmBusinessException(TAG_CODE_DUPLICATE);
    }
}
