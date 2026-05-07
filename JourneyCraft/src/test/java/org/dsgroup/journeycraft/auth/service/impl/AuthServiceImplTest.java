package org.dsgroup.journeycraft.auth.service.impl;

import org.dsgroup.journeycraft.auth.vo.reqvo.LoginReqVO;
import org.dsgroup.journeycraft.auth.vo.reqvo.RegisterReqVO;
import org.dsgroup.journeycraft.user.entity.User;
import org.dsgroup.journeycraft.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userMapper);
        ReflectionTestUtils.setField(authService, "tokenExpiresInMillis", 60000L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpiresInMillis", 600000L);
    }

    @Test
    void registerShouldReturnNewUserId() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1001L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        RegisterReqVO reqVO = new RegisterReqVO();
        reqVO.setUsername("zhangsan");
        reqVO.setPassword("password123");
        reqVO.setNickname("张三");

        assertEquals(1001L, authService.register(reqVO).getUserId());
    }

    @Test
    void loginShouldFailWithWrongPassword() {
        User user = new User();
        user.setId(1001L);
        user.setUsername("zhangsan");
        user.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("correctpwd"));
        user.setStatus(1);

        when(userMapper.selectOne(any())).thenReturn(user);

        LoginReqVO reqVO = new LoginReqVO();
        reqVO.setUsername("zhangsan");
        reqVO.setPassword("wrongpwd");

        assertThrows(org.dsgroup.journeycraft.common.exception.BusinessException.class,
                () -> authService.login(reqVO));
    }
}
