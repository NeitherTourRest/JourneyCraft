package org.dsgroup.journeycraft.auth.vo.rspvo;

import lombok.Data;

/**
 * 登录成功返回数据（用户名认证，无 token）。
 */
@Data
public class AuthLoginRspVO {

    /** 用户 ID。 */
    private Long userId;

    /** 用户名。 */
    private String username;

    /** 昵称。 */
    private String nickname;

    /** 头像 URL。 */
    private String avatarUrl;
}
