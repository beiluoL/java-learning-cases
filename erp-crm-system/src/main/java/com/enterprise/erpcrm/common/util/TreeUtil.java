package com.enterprise.erpcrm.common.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用树构建工具：把扁平的「id / parentId」结构组装为层级树。
 * <p>通过 {@link TreeNode} 接口解耦具体实体，菜单、部门、字典等均可复用。</p>
 *
 * @author erp-crm-system
 */
public final class TreeUtil {

    private TreeUtil() {
    }

    /** 树节点契约：实现类提供 id / parentId 并暴露可变 children 列表 */
    public interface TreeNode<T> {
        Long getId();

        Long getParentId();

        List<T> getChildren();

        void setChildren(List<T> children);
    }

    /**
     * 构建树。
     *
     * @param list   扁平节点列表
     * @param rootId 根节点 parentId（通常为 0）
     * @param <T>    节点类型
     * @return 顶层节点列表（已嵌套 children）
     */
    public static <T extends TreeNode<T>> List<T> build(List<T> list, Long rootId) {
        Map<Long, T> nodeMap = new LinkedHashMap<>();
        for (T node : list) {
            nodeMap.put(node.getId(), node);
            if (node.getChildren() == null) {
                node.setChildren(new ArrayList<>());
            }
        }
        List<T> roots = new ArrayList<>();
        for (T node : list) {
            Long pid = node.getParentId();
            if (pid == null || pid.equals(rootId)) {
                roots.add(node);
            } else {
                T parent = nodeMap.get(pid);
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }
        return roots;
    }
}
