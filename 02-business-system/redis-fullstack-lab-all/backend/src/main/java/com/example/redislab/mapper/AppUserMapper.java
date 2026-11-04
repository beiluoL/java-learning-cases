package com.example.redislab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.redislab.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
}
