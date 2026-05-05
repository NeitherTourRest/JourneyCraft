package org.dsgroup.journeycraft.user.api;

import org.dsgroup.journeycraft.user.vo.reqvo.ChangePasswordReqVO;
import org.dsgroup.journeycraft.user.vo.reqvo.UpdateUserInfoReqVO;
import org.dsgroup.journeycraft.user.vo.reqvo.UpdateUserPreferencesReqVO;
import org.dsgroup.journeycraft.user.vo.rspvo.UserInfoRspVO;
import org.dsgroup.journeycraft.user.vo.rspvo.UserPreferencesRspVO;

/**
 * 用户模块对外服务接口。
 */
public interface UserService {

    /**
     * 按用户 ID 获取用户信息。
     */
    UserInfoRspVO getUserInfoById(Long userId);

    /**
     * 按用户 ID 更新用户基础资料。
     */
    UserInfoRspVO updateUserInfoById(Long userId, UpdateUserInfoReqVO reqVO);

    /**
     * 按用户 ID 修改用户密码。
     */
    void changePasswordById(Long userId, ChangePasswordReqVO reqVO);

    /**
     * 按用户 ID 获取用户偏好设置。
     */
    UserPreferencesRspVO getUserPreferencesById(Long userId);

    /**
     * 按用户 ID 更新用户偏好设置。
     */
    UserPreferencesRspVO updateUserPreferencesById(Long userId, UpdateUserPreferencesReqVO reqVO);
}
