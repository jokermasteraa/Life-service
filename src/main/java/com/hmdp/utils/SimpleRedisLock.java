package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Slf4j
public class SimpleRedisLock implements ILock{

    private  static final String  LOCK_PREFIX = "Lock";

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    private StringRedisTemplate  stringRedisTemplate;
    private String name ;

    private  static final String  uuId = UUID.randomUUID().toString()+"-";

    private  static  final DefaultRedisScript<Long> redisScript;
    static{
        redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("unLock.lua"));
        redisScript.setResultType(Long.class);
    }


    @Override
    public boolean tryLock(Long TimeoutSec) {
        //1.获得当前线程的标识
        long threadId = Thread.currentThread().getId();
        String lockKey = LOCK_PREFIX  + name;
        //2.获取当前线程
        Boolean isAquire = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, uuId+threadId, TimeoutSec, TimeUnit.MINUTES);
        //3.返回获取结果
        return BooleanUtil.isTrue(isAquire);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.execute(redisScript,
                Collections.singletonList(LOCK_PREFIX + name) ,
                uuId + Thread.currentThread().getId());
    }

//    public void unlock() {
//        //1.获得当前锁的线程ID
//        String threadId = uuId + Thread.currentThread().getId();
//        //2.判断是否是当前的线程锁
//        String lockKey = LOCK_PREFIX + name;
//        //3.获得当前持有锁的线程
//        String currentThreadId = stringRedisTemplate.opsForValue().get(lockKey);
//        //4.如果一致则删除
//        if(currentThreadId.equals(threadId)){
//            stringRedisTemplate.delete(LOCK_PREFIX+name);
//        }
//    }

}
