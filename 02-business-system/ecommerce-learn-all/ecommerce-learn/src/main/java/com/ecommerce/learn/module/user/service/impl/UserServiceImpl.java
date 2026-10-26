package com.ecommerce.learn.module.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ecommerce.learn.common.constants.UserConstants;
import com.ecommerce.learn.common.exception.BizException;
import com.ecommerce.learn.common.exception.CodeEnum;
import com.ecommerce.learn.module.user.dto.*;
import com.ecommerce.learn.module.user.entity.User;
import com.ecommerce.learn.module.user.mapper.UserMapper;
import com.ecommerce.learn.module.user.service.UserService;
import com.ecommerce.learn.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

@Service
@Validated
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void register(RegisterDTO dto) {
        Long count = userMapper.selectCount(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BizException(CodeEnum.BIZ_ERROR, "用户名已被注册");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole(UserConstants.ROLE_USER);
        user.setStatus(1);
        userMapper.insert(user);
    }

    @Override
    public TokenPairVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, dto.getUsername()));
        if (user == null) {
            throw new BizException(CodeEnum.BIZ_ERROR, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(CodeEnum.BIZ_ERROR, "账号已被禁用");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(CodeEnum.BIZ_ERROR, "用户名或密码错误");
        }
        return buildTokenPair(user);
    }

    @Override
    public TokenPairVO refresh(String refreshToken) {
        var claims = jwtTokenProvider.parse(refreshToken);
        Long userId = jwtTokenProvider.getUserId(claims);
        User user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BizException(CodeEnum.UNAUTHORIZED, "用户不存在或已被禁用");
        }
        return buildTokenPair(user);
    }

    @Override
    public UserVO profile(Long userId) {
        return toVO(userMapper.selectById(userId));
    }

    @Override
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(CodeEnum.NOT_FOUND, "用户不存在");
        }
        if (StringUtils.hasText(dto.getNickname())) {
            user.setNickname(dto.getNickname());
        }
        if (StringUtils.hasText(dto.getPhone())) {
            user.setPhone(dto.getPhone());
        }
        if (StringUtils.hasText(dto.getEmail())) {
            user.setEmail(dto.getEmail());
        }
        if (StringUtils.hasText(dto.getAvatar())) {
            user.setAvatar(dto.getAvatar());
        }
        userMapper.updateById(user);
        return toVO(user);
    }

    private TokenPairVO buildTokenPair(User user) {
        TokenPairVO vo = new TokenPairVO();
        vo.setAccessToken(jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), user.getRole()));
        vo.setRefreshToken(jwtTokenProvider.createRefreshToken(user.getId(), user.getUsername(), user.getRole()));
        vo.setExpiresIn(7200L);
        vo.setUser(toVO(user));
        return vo;
    }

    private UserVO toVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        vo.setCreatedAt(user.getCreatedAt() == null ? LocalDateTime.now() : user.getCreatedAt());
        return vo;
    }
}
