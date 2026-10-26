package com.example.springvuelearn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.springvuelearn.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 继承 BaseMapper 即可获得 CRUD 能力，无需手写 XML。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
