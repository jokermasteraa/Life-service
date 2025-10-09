package com.hmdp.utils;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class RedisIDWorker {

    public static final long beginStamp = 1735689600L;

    public StringRedisTemplate  stringRedisTemplate;

    public RedisIDWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        System.out.println(" RedisIDWorker 构造完成，stringRedisTemplate = " + stringRedisTemplate);

    }

    public Long nextID(String prefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long epochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long newStamp = epochSecond - beginStamp;
        //获得自增ID
        //获得当天日期
        String format = now.format(DateTimeFormatter.ofPattern("yy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:" + prefix + ":" +format);
        //返回
        return  newStamp << 32 | count ;
    }


    public static void main(String[] args) {
        LocalDateTime localDateTime = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
        long epochSecond = localDateTime.toEpochSecond(ZoneOffset.UTC);
        System.out.println(epochSecond);
    }
}
