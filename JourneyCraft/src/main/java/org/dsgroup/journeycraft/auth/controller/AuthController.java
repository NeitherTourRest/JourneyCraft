package org.dsgroup.journeycraft.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dsgroup.journeycraft.auth.api.AuthService;
import org.dsgroup.journeycraft.auth.vo.reqvo.LoginReqVO;
import org.dsgroup.journeycraft.auth.vo.reqvo.RegisterReqVO;
import org.dsgroup.journeycraft.auth.vo.rspvo.AuthLoginRspVO;
import org.dsgroup.journeycraft.auth.vo.rspvo.RegisterRspVO;
import org.dsgroup.journeycraft.common.result.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证模块 HTTP 接口（用户名认证）。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Response<AuthLoginRspVO> login(@Valid @RequestBody LoginReqVO reqVO) {
        return Response.ok(authService.login(reqVO));
    }

    @PostMapping("/register")
    public Response<RegisterRspVO> register(@Valid @RequestBody RegisterReqVO reqVO) {
        return Response.ok(authService.register(reqVO));
    }

    @PostMapping("/logout")
    public Response<Void> logout() {
        authService.logout(null);
        return Response.ok();
    }
}
