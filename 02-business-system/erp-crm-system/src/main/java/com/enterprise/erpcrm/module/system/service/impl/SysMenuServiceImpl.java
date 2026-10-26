package com.enterprise.erpcrm.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.enterprise.erpcrm.common.constant.ErpConstants;
import com.enterprise.erpcrm.module.system.domain.vo.RouterVo;
import com.enterprise.erpcrm.module.system.entity.SysMenu;
import com.enterprise.erpcrm.module.system.entity.SysRoleMenu;
import com.enterprise.erpcrm.module.system.entity.SysUserRole;
import com.enterprise.erpcrm.module.system.mapper.SysMenuMapper;
import com.enterprise.erpcrm.module.system.mapper.SysRoleMenuMapper;
import com.enterprise.erpcrm.module.system.mapper.SysUserRoleMapper;
import com.enterprise.erpcrm.module.system.service.SysMenuService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 系统菜单服务实现。
 *
 * @author erp-crm-system
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    @Override
    public List<SysMenu> buildTree() {
        List<SysMenu> all = list(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getParentId, SysMenu::getOrderNum));
        Map<Long, List<SysMenu>> childrenMap = all.stream().filter(m -> m.getParentId() != null)
                .collect(Collectors.groupingBy(SysMenu::getParentId, LinkedHashMap::new, Collectors.toList()));
        List<SysMenu> roots = all.stream().filter(m -> m.getParentId() == null || m.getParentId() == 0)
                .collect(Collectors.toList());
        fillChildren(roots, childrenMap);
        return roots;
    }

    private void fillChildren(List<SysMenu> nodes, Map<Long, List<SysMenu>> childrenMap) {
        for (SysMenu node : nodes) {
            List<SysMenu> children = childrenMap.get(node.getId());
            if (children != null) {
                node.setChildren(children);
                fillChildren(children, childrenMap);
            }
        }
    }

    @Override
    public List<String> selectPermsByUserId(Long userId) {
        List<Long> menuIds = menuIdsOfUser(userId);
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return listByIds(menuIds).stream().map(SysMenu::getPerms)
                .filter(Objects::nonNull).filter(p -> !p.isEmpty()).collect(Collectors.toList());
    }

    @Override
    public List<RouterVo> selectRouterByUserId(Long userId) {
        List<Long> menuIds = menuIdsOfUser(userId);
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<SysMenu> menus = listByIds(menuIds).stream()
                .filter(m -> !"F".equals(m.getMenuType()))
                .sorted((a, b) -> Integer.compare(a.getOrderNum(), b.getOrderNum()))
                .collect(Collectors.toList());
        Map<Long, List<SysMenu>> childrenMap = menus.stream()
                .filter(m -> m.getParentId() != null && m.getParentId() != 0)
                .collect(Collectors.groupingBy(SysMenu::getParentId, LinkedHashMap::new, Collectors.toList()));
        List<SysMenu> roots = menus.stream()
                .filter(m -> m.getParentId() == null || m.getParentId() == 0).collect(Collectors.toList());
        List<RouterVo> routers = new ArrayList<>();
        for (SysMenu root : roots) {
            routers.add(toRouter(root, childrenMap, true));
        }
        return routers;
    }

    private RouterVo toRouter(SysMenu menu, Map<Long, List<SysMenu>> childrenMap, boolean isRoot) {
        RouterVo.RouterVoBuilder builder = RouterVo.builder()
                .name(StringUtils.hasText(menu.getPath()) ? camel(menu.getPath()) : "Menu" + menu.getId())
                .path(isRoot ? ("/" + (menu.getPath() == null ? "" : menu.getPath())) : (menu.getPath() == null ? "" : menu.getPath()))
                .component(menu.getComponent())
                .query(menu.getQueryParam())
                .hidden(menu.getVisible() != null && menu.getVisible() == 1)
                .isFrame(menu.getIsFrame() != null && menu.getIsFrame() == 1)
                .keepAlive(menu.getIsCache() != null && menu.getIsCache() == 1)
                .meta(RouterVo.MetaVo.builder()
                        .title(menu.getMenuName())
                        .icon(menu.getIcon() == null ? "" : menu.getIcon())
                        .build());
        List<SysMenu> children = childrenMap.get(menu.getId());
        if (children != null && !children.isEmpty()) {
            List<RouterVo> childRouters = new ArrayList<>();
            for (SysMenu child : children) {
                childRouters.add(toRouter(child, childrenMap, false));
            }
            builder.children(childRouters);
        }
        return builder.build();
    }

    private String camel(String path) {
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private List<Long> menuIdsOfUser(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<Long> menuIds;
        if (roleIds.isEmpty()) {
            menuIds = List.of();
        } else {
            menuIds = roleMenuMapper.selectList(
                            new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                    .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
        }
        return menuIds;
    }
}
