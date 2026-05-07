package org.dsgroup.journeycraft.auth.service.impl;

import org.dsgroup.journeycraft.auth.api.AuthService;
import org.dsgroup.journeycraft.auth.vo.reqvo.LoginReqVO;
import org.dsgroup.journeycraft.auth.vo.reqvo.RefreshTokenReqVO;
import org.dsgroup.journeycraft.auth.vo.reqvo.RegisterReqVO;
import org.dsgroup.journeycraft.auth.vo.rspvo.AuthLoginRspVO;
import org.dsgroup.journeycraft.auth.vo.rspvo.RegisterRspVO;
import org.dsgroup.journeycraft.common.enums.ResponseCodeEnum;
import org.dsgroup.journeycraft.common.exception.BusinessException;
import org.dsgroup.journeycraft.user.entity.User;
import org.dsgroup.journeycraft.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery;

/**
 * 认证服务实现，负责注册、登录（用户名认证，无需 token）。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 登录校验并返回用户 ID。
     */
    @Override
    public AuthLoginRspVO login(LoginReqVO reqVO) {
        User user = userMapper.selectOne(lambdaQuery(User.class)
                .and(w -> w.eq(User::getUsername, reqVO.getUsername())
                        .or()
                        .eq(User::getPhone, reqVO.getUsername())
                        .or()
                        .eq(User::getEmail, reqVO.getUsername()))
                .last("limit 1"));
        if (user == null || !passwordEncoder.matches(reqVO.getPassword(), user.getPassword())) {
            throw new BusinessException(ResponseCodeEnum.LOGIN_FAILED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResponseCodeEnum.FORBIDDEN, "账号已禁用");
        }
        AuthLoginRspVO rsp = new AuthLoginRspVO();
        rsp.setUserId(user.getId());
        rsp.setUsername(user.getUsername());
        rsp.setNickname(user.getNickname());
        rsp.setAvatarUrl(user.getAvatarUrl());
        return rsp;
    }

    /**
     * 创建用户账号。
     */
    @Override
    public RegisterRspVO register(RegisterReqVO reqVO) {
        if (userMapper.selectCount(lambdaQuery(User.class).eq(User::getUsername, reqVO.getUsername())) > 0) {
            throw new BusinessException(ResponseCodeEnum.DATA_ALREADY_EXIST, "用户名已存在");
        }
        if (reqVO.getPhone() != null && !reqVO.getPhone().isBlank()
                && userMapper.selectCount(lambdaQuery(User.class).eq(User::getPhone, reqVO.getPhone())) > 0) {
            throw new BusinessException(ResponseCodeEnum.DATA_ALREADY_EXIST, "手机号已存在");
        }
        if (reqVO.getEmail() != null && !reqVO.getEmail().isBlank()
                && userMapper.selectCount(lambdaQuery(User.class).eq(User::getEmail, reqVO.getEmail())) > 0) {
            throw new BusinessException(ResponseCodeEnum.DATA_ALREADY_EXIST, "邮箱已存在");
        }
        RegisterRspVO rspVO = new RegisterRspVO();
        User user = new User();
        user.setUsername(reqVO.getUsername());
        user.setPassword(passwordEncoder.encode(reqVO.getPassword()));
        user.setNickname(reqVO.getNickname() == null || reqVO.getNickname().isBlank() ? reqVO.getUsername() : reqVO.getNickname());
        user.setPhone(reqVO.getPhone());
        user.setEmail(reqVO.getEmail());
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        rspVO.setUserId(user.getId());
        return rspVO;
    }

    /**
     * 注销（用户名认证模式，无需操作）。
     */
    @Override
    public void logout(String authorization) {
        // 用户名认证模式：无需 token 注销操作
    }

    /**
     * 刷新（用户名认证模式，无需刷新）。
     */
    @Override
    public AuthLoginRspVO refreshToken(RefreshTokenReqVO reqVO) {
        throw new BusinessException(ResponseCodeEnum.UNAUTHORIZED, "用户名认证模式不支持 token 刷新");
    }
}
