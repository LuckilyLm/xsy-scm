package net.lab1024.sa.admin.module.scm.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.*;
import net.lab1024.sa.admin.module.scm.product.domain.entity.*;
import net.lab1024.sa.admin.module.scm.product.domain.form.*;
import net.lab1024.sa.admin.module.scm.product.domain.vo.*;
import net.lab1024.sa.admin.module.scm.product.manager.ProductAggregateValidator;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import static net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode.*;

@Service
@RequiredArgsConstructor
public class ProductCategoryService {
    private final ProductCategoryDao dao;
    private final ProductSpuDao spuDao;

    public List<ProductCategoryEntity> all() {
        return dao.selectList(new LambdaQueryWrapper<ProductCategoryEntity>().orderByAsc(ProductCategoryEntity::getSortOrder,ProductCategoryEntity::getId));
    }
    public ProductCategoryEntity require(Long id) {
        var category=dao.selectById(id);
        if (category==null) throw new ScmBusinessException(CATEGORY_NOT_FOUND);
        return category;
    }
    public ProductCategoryEntity requireSelectableCategory(Long id) {
        // Serialize category deletion with creation/moving of a product referencing it.
        var category=dao.selectOne(new LambdaQueryWrapper<ProductCategoryEntity>().eq(ProductCategoryEntity::getId,id).last("FOR UPDATE"));
        if (category==null) throw new ScmBusinessException(CATEGORY_NOT_FOUND);
        if (category.getLevel()!=3 || !"ENABLED".equals(category.getStatus())) throw new ScmBusinessException(CATEGORY_PARENT_INVALID);
        return category;
    }
    public int resolveLevel(Long parentId,Long self) {
        if (parentId==null) return 1;
        if (parentId.equals(self)) throw new ScmBusinessException(CATEGORY_PARENT_INVALID);
        var parent=require(parentId);
        if (parent.getLevel()>=3) throw new ScmBusinessException(CATEGORY_LEVEL_INVALID);
        if (!"ENABLED".equals(parent.getStatus())) throw new ScmBusinessException(CATEGORY_PARENT_INVALID);
        return parent.getLevel()+1;
    }
    public List<ProductCategoryTreeVO> tree() {
        var rows=all(); Map<Long,ProductCategoryTreeVO> map=new LinkedHashMap<>();
        for (var row:rows) {
            var vo=new ProductCategoryTreeVO(); BeanUtils.copyProperties(row,vo); vo.setCategoryId(row.getId());
            vo.setChildren(new ArrayList<>()); map.put(row.getId(),vo);
        }
        List<ProductCategoryTreeVO> roots=new ArrayList<>();
        for (var vo:map.values()) {
            vo.setCategoryPath(path(vo.getCategoryId(),rows));
            if (vo.getParentId()==null || !map.containsKey(vo.getParentId())) roots.add(vo);
            else map.get(vo.getParentId()).getChildren().add(vo);
        }
        return roots;
    }
    public ProductCategoryVO detail(Long id) {
        var row=require(id); var vo=new ProductCategoryVO(); BeanUtils.copyProperties(row,vo);
        vo.setCategoryId(id); vo.setCategoryPath(path(id,all())); return vo;
    }
    public static String path(Long id,List<ProductCategoryEntity> rows) {
        Map<Long,ProductCategoryEntity> map=new HashMap<>(); rows.forEach(c -> map.put(c.getId(),c));
        LinkedList<String> names=new LinkedList<>(); Set<Long> seen=new HashSet<>();
        while (id!=null && seen.add(id) && map.containsKey(id)) {
            var row=map.get(id); names.addFirst(row.getName()); id=row.getParentId();
        }
        return String.join(" / ",names);
    }
    /** Q3 V2 Enhancement: selected category plus all descendants, not legacy exact-match semantics. */
    public List<Long> descendantIds(Long id,List<ProductCategoryEntity> rows) {
        Set<Long> result=new LinkedHashSet<>(); result.add(id);
        boolean changed;
        do { changed=false; for (var row:rows) if (result.contains(row.getParentId())) changed|=result.add(row.getId()); } while(changed);
        return List.copyOf(result);
    }
    @Transactional
    public Long add(ProductCategoryAddForm form) {
        lockParent(form.getParentId());
        var entity=new ProductCategoryEntity(); apply(entity,form,null);
        entity.setCreatedAt(entity.getUpdatedAt()); entity.setCreatedBy(entity.getUpdatedBy());
        try { dao.insert(entity); } catch (DuplicateKeyException e) { throw new ScmBusinessException(PRODUCT_CODE_DUPLICATE); }
        return entity.getId();
    }
    @Transactional
    public void update(ProductCategoryUpdateForm form) {
        var entity=require(form.getCategoryId());
        if (!Objects.equals(entity.getVersion(),form.getVersion())) throw new ScmBusinessException(VERSION_CONFLICT);
        var descendants=descendantIds(entity.getId(),all());
        if (form.getParentId()!=null && descendants.contains(form.getParentId())) throw new ScmBusinessException(CATEGORY_PARENT_INVALID);
        lockParent(form.getParentId());
        int level=resolveLevel(form.getParentId(),entity.getId());
        // A move must not silently invalidate descendant levels or products' level-three selection.
        if (level!=entity.getLevel() && (descendants.size()>1 || countProducts(entity.getId())>0)) throw new ScmBusinessException(CATEGORY_PARENT_INVALID);
        apply(entity,form,entity.getId()); entity.setVersion(form.getVersion());
        try { if (dao.updateById(entity)!=1) throw new ScmBusinessException(VERSION_CONFLICT); }
        catch (DuplicateKeyException e) { throw new ScmBusinessException(PRODUCT_CODE_DUPLICATE); }
    }
    @Transactional
    public void delete(ProductCategoryDeleteForm form) {
        var entity=dao.selectOne(new LambdaQueryWrapper<ProductCategoryEntity>().eq(ProductCategoryEntity::getId,form.getCategoryId()).last("FOR UPDATE"));
        if (entity==null) throw new ScmBusinessException(CATEGORY_NOT_FOUND);
        if (!Objects.equals(entity.getVersion(),form.getVersion())) throw new ScmBusinessException(VERSION_CONFLICT);
        if (dao.selectCount(new LambdaQueryWrapper<ProductCategoryEntity>().eq(ProductCategoryEntity::getParentId,entity.getId()))>0) throw new ScmBusinessException(CATEGORY_HAS_CHILDREN);
        if (countProducts(entity.getId())>0) throw new ScmBusinessException(CATEGORY_HAS_PRODUCTS);
        entity.setUpdatedAt(OffsetDateTime.now()); entity.setUpdatedBy(ScmOperator.current());
        if (dao.updateById(entity)!=1) throw new ScmBusinessException(VERSION_CONFLICT);
        dao.deleteById(entity.getId());
    }
    private long countProducts(Long id) { return spuDao.selectCount(new LambdaQueryWrapper<ProductSpuEntity>().eq(ProductSpuEntity::getCategoryId,id)); }
    private void lockParent(Long id) {
        if(id!=null && dao.selectOne(new LambdaQueryWrapper<ProductCategoryEntity>().eq(ProductCategoryEntity::getId,id).last("FOR UPDATE"))==null) throw new ScmBusinessException(CATEGORY_NOT_FOUND);
    }
    private void apply(ProductCategoryEntity entity,ProductCategoryAddForm form,Long self) {
        int level=resolveLevel(form.getParentId(),self); BeanUtils.copyProperties(form,entity,"version");
        entity.setCategoryCode(ProductAggregateValidator.normalizeCode(form.getCategoryCode())); entity.setName(form.getName().trim());
        entity.setLevel(level); entity.setUpdatedAt(OffsetDateTime.now()); entity.setUpdatedBy(ScmOperator.current());
    }
}
