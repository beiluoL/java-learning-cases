package com.example.demo.mapper;

import com.example.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户表 Mapper 接口。
 *
 * <p>已被启动类的 @MapperScan 扫描，也可单独标注 @Mapper（二者取其一即可，此处显式标注更直观）。
 */
@Mapper
public interface UserMapper {

    /**
     * 根据主键查询未删除的用户。
     *
     * @param id 主键
     * @return 用户实体，未找到返回 null
     */
    User selectById(@Param("id") Long id);

    /**
     * 查询全部未删除的用户。
     *
     * @return 用户列表
     */
    List<User> selectAll();
}
