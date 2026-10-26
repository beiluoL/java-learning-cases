package com.example.ssm.mapper;

import com.example.ssm.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户表 Mapper 接口（数据访问层）。
 * 由 MapperScannerConfigurer 扫描并生成代理实现，无需手写实现类。
 * 对应 XML：resources/mybatis/mapper/UserMapper.xml
 */
public interface UserMapper {

    /** 条件分页查询（username / status 均可为空） */
    List<User> selectByCondition(@Param("username") String username,
                                 @Param("status") Integer status);

    /** 根据 id 查询 */
    User selectById(@Param("id") Long id);

    /** 统计总数（工作台用） */
    int count();

    /** 新增，返回自增主键到 user.id */
    int insert(User user);

    /** 更新（动态字段） */
    int update(User user);

    /** 删除单条 */
    int deleteById(@Param("id") Long id);

    /** 批量删除 */
    int deleteBatch(@Param("ids") List<Long> ids);
}
