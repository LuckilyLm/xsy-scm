package com.xsy.scm.admin.module.business.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import com.xsy.scm.admin.module.business.product.dao.ProductCategoryDao;
import com.xsy.scm.admin.module.business.product.domain.entity.ProductCategoryEntity;
import com.xsy.scm.admin.module.business.product.domain.form.ProductCategoryTreeQueryForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductCategoryTreeVO;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.SmartBeanUtil;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 产品分类 Service
 *
 * @author xsy-scm
 */
@Service
public class ProductCategoryService {

    @Resource
    private ProductCategoryDao productCategoryDao;

    /**
     * 查询产品分类层级树
     */
    public ResponseDTO<List<ProductCategoryTreeVO>> queryTree(ProductCategoryTreeQueryForm queryForm) {
        Long parentId = queryForm.getParentId();
        if (parentId == null) {
            parentId = NumberUtils.LONG_ZERO;
        }

        LambdaQueryWrapper<ProductCategoryEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProductCategoryEntity::getDeletedFlag, false);
        queryWrapper.orderByAsc(ProductCategoryEntity::getSort);
        List<ProductCategoryEntity> allCategoryEntityList = productCategoryDao.selectList(queryWrapper);

        Long finalParentId = parentId;
        List<ProductCategoryEntity> rootEntityList = allCategoryEntityList.stream()
                .filter(e -> Objects.equals(e.getParentId(), finalParentId))
                .collect(Collectors.toList());

        List<ProductCategoryTreeVO> treeList = SmartBeanUtil.copyList(rootEntityList, ProductCategoryTreeVO.class);
        treeList.forEach(e -> {
            e.setValue(e.getCategoryId());
            e.setLabel(e.getCategoryName());
        });
        this.queryAndSetSubCategory(treeList, allCategoryEntityList);
        return ResponseDTO.ok(treeList);
    }

    /**
     * 递归设置子分类
     */
    private void queryAndSetSubCategory(List<ProductCategoryTreeVO> treeList, List<ProductCategoryEntity> allCategoryEntityList) {
        if (treeList == null || treeList.isEmpty()) {
            return;
        }
        List<Long> parentIdList = treeList.stream().map(ProductCategoryTreeVO::getValue).collect(Collectors.toList());
        Map<Long, List<ProductCategoryEntity>> categorySubMap = allCategoryEntityList.stream()
                .filter(e -> parentIdList.contains(e.getParentId()))
                .collect(Collectors.groupingBy(ProductCategoryEntity::getParentId));

        treeList.forEach(e -> {
            List<ProductCategoryEntity> childrenEntityList = categorySubMap.getOrDefault(e.getValue(), java.util.Collections.emptyList());
            List<ProductCategoryTreeVO> childrenVOList = SmartBeanUtil.copyList(childrenEntityList, ProductCategoryTreeVO.class);
            childrenVOList.forEach(item -> {
                item.setValue(item.getCategoryId());
                item.setLabel(item.getCategoryName());
            });
            this.queryAndSetSubCategory(childrenVOList, allCategoryEntityList);
            e.setChildren(childrenVOList);
        });
    }
}
