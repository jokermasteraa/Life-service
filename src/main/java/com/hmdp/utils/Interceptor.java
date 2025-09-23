package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class Interceptor implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断线程中是否有用户，有则进行拦截
        User user = UserHolder.getUser();
        if( user == null ){
            //不存在，拦截
            return false;
        }
        return true;
    }


//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        //1.拿取请求中的session
//        //HttpSession session = request.getSession();
//        //1.从request中拿取token
//        String token = request.getHeader("authorization");
//        //2.拿取用户信息
//        //Object user = session.getAttribute("user");
//        //3.判断用户是否存在
//        if( user == null ){
//            //3.1不存在，拦截
//            return false;
//        }
//        //4.将信息保存在ThreadLocal中
//        UserHolder.saveUser((UserDTO) user);
//        return true;
//    }
}
