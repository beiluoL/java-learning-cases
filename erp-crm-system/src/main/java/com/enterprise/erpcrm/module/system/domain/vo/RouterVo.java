package com.enterprise.erpcrm.module.system.domain.vo;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端路由视图对象（Vue Router 格式）。
 *
 * @author erp-crm-system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouterVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路由 name（驼峰，唯一） */
    private String name;
    /** 路由路径 */
    private String path;
    /** 组件路径（Layout 或业务页） */
    private String component;
    /** 内联路由参数 */
    private String query;
    /** 是否隐藏 */
    private boolean hidden;
    /** 是否外链 */
    private boolean isFrame;
    /** 是否缓存 */
    private boolean keepAlive;
    /** 路由元信息 */
    private MetaVo meta;
    /** 子路由 */
    private List<RouterVo> children;

    /**
     * 路由元信息（标题/图标/权限等）。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetaVo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String title;
        private String icon;
        private java.util.List<String> roles;
        private Map<String, Object> extra;
    }
}
