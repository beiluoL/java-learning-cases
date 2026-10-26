package com.enterprise.erpcrm.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.enterprise.erpcrm.module.system.entity.SysDept;
import com.enterprise.erpcrm.module.system.mapper.SysDeptMapper;
import com.enterprise.erpcrm.module.system.service.SysDeptService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统部门服务实现。保存/更新时维护 {@code ancestors} 路径，支撑「本部门及以下」数据权限。
 *
 * @author erp-crm-system
 */
@Service
@RequiredArgsConstructor
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    @Override
    public List<SysDept> buildTree() {
        List<SysDept> all = list(new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getParentId, SysDept::getOrderNum));
        Map<Long, List<SysDept>> childrenMap = all.stream()
                .filter(d -> d.getParentId() != null && d.getParentId() != 0)
                .collect(Collectors.groupingBy(SysDept::getParentId, LinkedHashMap::new, Collectors.toList()));
        List<SysDept> roots = all.stream()
                .filter(d -> d.getParentId() == null || d.getParentId() == 0).collect(Collectors.toList());
        for (SysDept root : roots) {
            fill(root, childrenMap);
        }
        return roots;
    }

    private void fill(SysDept node, Map<Long, List<SysDept>> childrenMap) {
        List<SysDept> children = childrenMap.get(node.getId());
        if (children != null) {
            node.setChildren(children);
            for (SysDept child : children) {
                fill(child, childrenMap);
            }
        }
    }

    @Override
    public List<Long> selectChildDeptIds(Long deptId) {
        List<SysDept> all = list();
        List<Long> result = new ArrayList<>();
        result.add(deptId);
        for (SysDept d : all) {
            if (d.getAncestors() != null && (d.getAncestors().equals(String.valueOf(deptId))
                    || d.getAncestors().startsWith(deptId + ",")
                    || d.getAncestors().endsWith("," + deptId)
                    || d.getAncestors().contains("," + deptId + ",")
                    || Objects.equals(d.getId(), deptId))) {
                result.add(d.getId());
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(SysDept entity) {
        if (entity.getParentId() == null) {
            entity.setParentId(0L);
        }
        String ancestors = computeAncestors(entity.getParentId());
        entity.setAncestors(ancestors);
        boolean ok = super.save(entity);
        // 新增后回写：把自身 id 追加进祖先链，便于下层部门继承
        if (entity.getParentId() != 0) {
            entity.setAncestors(ancestors + "," + entity.getId());
            super.updateById(entity);
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(SysDept entity) {
        if (entity.getParentId() == null) {
            entity.setParentId(0L);
        }
        String ancestors = computeAncestors(entity.getParentId());
        if (entity.getParentId() != 0) {
            ancestors = ancestors + "," + entity.getId();
        }
        entity.setAncestors(ancestors);
        return super.updateById(entity);
    }

    private String computeAncestors(Long parentId) {
        if (parentId == null || parentId == 0) {
            return "0";
        }
        SysDept parent = getById(parentId);
        if (parent == null || parent.getAncestors() == null) {
            return "0," + parentId;
        }
        return parent.getAncestors() + "," + parentId;
    }
}
