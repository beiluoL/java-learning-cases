package com.ecommerce.learn.module.product.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ecommerce.learn.common.constants.RedisKeyConstants;
import com.ecommerce.learn.common.exception.BizException;
import com.ecommerce.learn.common.exception.CodeEnum;
import com.ecommerce.learn.common.util.RedisUtil;
import com.ecommerce.learn.module.product.dto.CategorySaveDTO;
import com.ecommerce.learn.module.product.dto.CategoryVO;
import com.ecommerce.learn.module.product.entity.Category;
import com.ecommerce.learn.module.product.mapper.CategoryMapper;
import com.ecommerce.learn.module.product.service.CategoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类服务：树形结构构建 + Redis 缓存
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final RedisUtil redisUtil;

    @Override
    public List<CategoryVO> tree() {
        String key = RedisKeyConstants.CATEGORY_TREE;
        List<CategoryVO> cached = redisUtil.getObject(key, new TypeReference<List<CategoryVO>>() {});
        if (cached != null) {
            return cached;
        }
        List<Category> all = categoryMapper.selectList(Wrappers.emptyWrapper());
        List<CategoryVO> tree = buildTree(all);
        redisUtil.setObject(key, tree, RedisKeyConstants.CATEGORY_TREE_TTL);
        return tree;
    }

    @Override
    public List<Category> listAll() {
        return categoryMapper.selectList(Wrappers.emptyWrapper());
    }

    @Override
    public void save(CategorySaveDTO dto) {
        Category c = new Category();
        c.setName(dto.getName());
        c.setParentId(dto.getParentId());
        c.setLevel(dto.getLevel());
        c.setSort(dto.getSort());
        c.setIcon(dto.getIcon());
        categoryMapper.insert(c);
        redisUtil.delete(RedisKeyConstants.CATEGORY_TREE);
    }

    @Override
    public void update(CategorySaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(CodeEnum.PARAM_ERROR, "分类 ID 不能为空");
        }
        Category c = categoryMapper.selectById(dto.getId());
        if (c == null) {
            throw new BizException(CodeEnum.NOT_FOUND, "分类不存在");
        }
        c.setName(dto.getName());
        c.setParentId(dto.getParentId());
        c.setLevel(dto.getLevel());
        c.setSort(dto.getSort());
        c.setIcon(dto.getIcon());
        categoryMapper.updateById(c);
        redisUtil.delete(RedisKeyConstants.CATEGORY_TREE);
    }

    @Override
    public void remove(Long id) {
        Long childCount = categoryMapper.selectCount(
                Wrappers.<Category>lambdaQuery().eq(Category::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(CodeEnum.BIZ_ERROR, "存在子分类，无法删除");
        }
        categoryMapper.deleteById(id);
        redisUtil.delete(RedisKeyConstants.CATEGORY_TREE);
    }

    private List<CategoryVO> buildTree(List<Category> all) {
        if (CollectionUtils.isEmpty(all)) {
            return new ArrayList<>();
        }
        Map<Long, List<Category>> byParent = all.stream()
                .collect(Collectors.groupingBy(Category::getParentId));
        List<CategoryVO> roots = convert(byParent.getOrDefault(0L, List.of()));
        fillChildren(roots, byParent);
        return roots;
    }

    private void fillChildren(List<CategoryVO> nodes, Map<Long, List<Category>> byParent) {
        for (CategoryVO node : nodes) {
            List<Category> children = byParent.getOrDefault(node.getId(), List.of());
            List<CategoryVO> childVos = convert(children);
            if (!childVos.isEmpty()) {
                node.setChildren(childVos);
                fillChildren(childVos, byParent);
            }
        }
    }

    private List<CategoryVO> convert(List<Category> list) {
        return list.stream().map(c -> {
            CategoryVO vo = new CategoryVO();
            vo.setId(c.getId());
            vo.setName(c.getName());
            vo.setParentId(c.getParentId());
            vo.setLevel(c.getLevel());
            vo.setIcon(c.getIcon());
            return vo;
        }).collect(Collectors.toList());
    }
}
