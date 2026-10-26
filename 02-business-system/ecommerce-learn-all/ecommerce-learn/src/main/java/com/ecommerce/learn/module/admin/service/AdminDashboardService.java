package com.ecommerce.learn.module.admin.service;

import com.ecommerce.learn.module.admin.dto.AdminStatsVO;

public interface AdminDashboardService {

    /** 后台首页统计 */
    AdminStatsVO stats();
}
