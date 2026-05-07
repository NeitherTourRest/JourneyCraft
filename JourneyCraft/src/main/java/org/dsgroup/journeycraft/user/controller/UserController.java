package org.dsgroup.journeycraft.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dsgroup.journeycraft.common.result.Response;
import org.dsgroup.journeycraft.user.api.UserService;
import org.dsgroup.journeycraft.user.vo.reqvo.ChangePasswordReqVO;
import org.dsgroup.journeycraft.user.vo.reqvo.UpdateUserInfoReqVO;
import org.dsgroup.journeycraft.user.vo.reqvo.UpdateUserPreferencesReqVO;
import org.dsgroup.journeycraft.user.vo.rspvo.UserInfoRspVO;
import org.dsgroup.journeycraft.user.vo.rspvo.UserPreferencesRspVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户模块 HTTP 接口（用户名认证，无需 Authorization header）。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/info")
    public Response<UserInfoRspVO> getCurrentUserInfo(@RequestParam Long userId) {
        return Response.ok(userService.getUserInfoById(userId));
    }

    @PutMapping("/info")
    public Response<UserInfoRspVO> updateCurrentUserInfo(@RequestParam Long userId,
                                                           @RequestBody UpdateUserInfoReqVO reqVO) {
        return Response.ok(userService.updateUserInfoById(userId, reqVO));
    }

    @PutMapping("/password")
    public Response<Void> changePassword(@RequestParam Long userId,
                                          @Valid @RequestBody ChangePasswordReqVO reqVO) {
        userService.changePasswordById(userId, reqVO);
        return Response.ok();
    }

    @GetMapping("/preferences")
    public Response<UserPreferencesRspVO> getCurrentUserPreferences(@RequestParam Long userId) {
        return Response.ok(userService.getUserPreferencesById(userId));
    }

    @PutMapping("/preferences")
    public Response<UserPreferencesRspVO> updateCurrentUserPreferences(@RequestParam Long userId,
                                                                         @RequestBody UpdateUserPreferencesReqVO reqVO) {
        return Response.ok(userService.updateUserPreferencesById(userId, reqVO));
    }
}
