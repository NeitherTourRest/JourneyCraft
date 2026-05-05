package org.dsgroup.journeycraft.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dsgroup.journeycraft.common.enums.ResponseCodeEnum;
import org.dsgroup.journeycraft.common.exception.BusinessException;
import org.dsgroup.journeycraft.common.result.Response;
import org.dsgroup.journeycraft.common.utils.TokenSessionStore;
import org.dsgroup.journeycraft.user.api.UserService;
import org.dsgroup.journeycraft.user.vo.reqvo.ChangePasswordReqVO;
import org.dsgroup.journeycraft.user.vo.reqvo.UpdateUserInfoReqVO;
import org.dsgroup.journeycraft.user.vo.reqvo.UpdateUserPreferencesReqVO;
import org.dsgroup.journeycraft.user.vo.rspvo.UserInfoRspVO;
import org.dsgroup.journeycraft.user.vo.rspvo.UserPreferencesRspVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户模块 HTTP 接口。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final TokenSessionStore tokenSessionStore;

    /**
     * 获取用户信息。
     * <p>
     * 请求方需传入自己的 userId，并提供 Authorization token 用于身份校验。
     * Controller 会比对 token 中的 userId 与请求参数中的 userId 是否一致，
     * 防止用户越权访问他人数据。
     */
    @GetMapping("/info")
    public Response<UserInfoRspVO> getCurrentUserInfo(@RequestParam Long userId,
                                                       @RequestHeader("Authorization") String authorization) {
        verifyOwnership(authorization, userId);
        return Response.ok(userService.getUserInfoById(userId));
    }

    /**
     * 更新用户基础资料。
     */
    @PutMapping("/info")
    public Response<UserInfoRspVO> updateCurrentUserInfo(@RequestParam Long userId,
                                                          @RequestHeader("Authorization") String authorization,
                                                          @RequestBody UpdateUserInfoReqVO reqVO) {
        verifyOwnership(authorization, userId);
        return Response.ok(userService.updateUserInfoById(userId, reqVO));
    }

    /**
     * 修改用户密码。
     */
    @PutMapping("/password")
    public Response<Void> changePassword(@RequestParam Long userId,
                                          @RequestHeader("Authorization") String authorization,
                                          @Valid @RequestBody ChangePasswordReqVO reqVO) {
        verifyOwnership(authorization, userId);
        userService.changePasswordById(userId, reqVO);
        return Response.ok();
    }

    /**
     * 获取用户偏好设置。
     */
    @GetMapping("/preferences")
    public Response<UserPreferencesRspVO> getCurrentUserPreferences(@RequestParam Long userId,
                                                                     @RequestHeader("Authorization") String authorization) {
        verifyOwnership(authorization, userId);
        return Response.ok(userService.getUserPreferencesById(userId));
    }

    /**
     * 更新用户偏好设置。
     */
    @PutMapping("/preferences")
    public Response<UserPreferencesRspVO> updateCurrentUserPreferences(@RequestParam Long userId,
                                                                        @RequestHeader("Authorization") String authorization,
                                                                        @RequestBody UpdateUserPreferencesReqVO reqVO) {
        verifyOwnership(authorization, userId);
        return Response.ok(userService.updateUserPreferencesById(userId, reqVO));
    }

    /**
     * 校验 token 持有人与请求参数中的 userId 是否一致。
     * 不一致则抛出 403，防止用户 A 访问用户 B 的数据。
     */
    private void verifyOwnership(String authorization, Long requestUserId) {
        Long tokenUserId = tokenSessionStore.requireUserId(authorization);
        if (!tokenUserId.equals(requestUserId)) {
            throw new BusinessException(ResponseCodeEnum.FORBIDDEN, "无权访问该用户数据");
        }
    }
}
