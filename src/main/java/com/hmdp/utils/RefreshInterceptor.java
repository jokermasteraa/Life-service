package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.从request中拿取token
        String token = request.getHeader("authorization");
        //System.out.println("Received token: " + token);
        // 2.判断token是否存在
        if(StringUtils.isBlank(token) ){
            return true;
        }
        //3.从Redis中拿取用户信息
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY+token);
        // 2.判断用户是否存在
        if(map.isEmpty()){
            //3.1不存在，拦截
            return true;
        }
        //将map转换为user对象
        User user = BeanUtil.fillBeanWithMap(map, new User(), false);
        stringRedisTemplate.expire(LOGIN_USER_KEY+token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //4.将信息保存在ThreadLocal中
        UserHolder.saveUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
