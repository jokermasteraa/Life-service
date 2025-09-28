package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_TTL;


@Slf4j
@Component
public class CacheClient {

    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String  key , Object value, Long dateTime , TimeUnit unit){
        stringRedisTemplate.opsForValue().set( key , JSONUtil.toJsonStr(value) , dateTime, unit);
    }

    public void setWithLogicalExpire(String  key , Object value, Long dateTime , TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(dateTime)));
        stringRedisTemplate.opsForValue().set( key , JSONUtil.toJsonStr(redisData));
    }

    public  <ID,R>  R queryWithPassThrough(String prefix, ID id, Class<R> type,
                                           Function<ID,R> dbFallBack, Long dateTime , TimeUnit unit){
        String key = prefix + id;
        //1.查询Redis缓存中个是否有商户信息
        String cache = stringRedisTemplate.opsForValue().get(key);
        //2.有则返回
        if(StringUtils.isNotBlank(cache)){
            return  JSONUtil.toBean(cache,type);
        }
        if (cache != null) {
            return null;
        }
        //3.没有则去数据库中查找
        R r = dbFallBack.apply(id);
        if(r == null){
            //3.1数据库中没有则缓存一个空对象
            stringRedisTemplate.opsForValue().set(key,"",
                    RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //3.2数据中有则加载中缓存中
        this.set(key, r, dateTime, unit);
        return r;
    }

    private static ExecutorService executorService = Executors.newFixedThreadPool(10);

    public <ID,R> R queryWithLogicalExpire(String prefix,ID id,Class<R> type
            ,Function<ID,R> deFallBack ,Long dateTime , TimeUnit unit) throws InterruptedException {
        String key = prefix + id;
        //1.查询Redis缓存中个是否有商户信息
        String cache = stringRedisTemplate.opsForValue().get(key);
        //2.没有则返回空
        if (StringUtils.isBlank(cache)) {
            return null;
        }
        //3.有则判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(cache, RedisData.class);
        //把JSON反序列化为对象
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type );
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //3.1缓存未过期则直接返回
            return r;
        }
        //3.2缓存已过期则进行缓存重建
        //4.尝试获取互斥锁
        String lockKey = "Lock" + id;
        boolean isAquire = tryLock(lockKey);
        if(!isAquire){
            //4.1未获得锁直接返回数据(旧数据)
            return  r;
        }
        //4.2获得调用线程进行重建
        if(isAquire){
            System.out.println(Thread.currentThread().getName()+"获得了锁");
            //5.开启线程进行缓存重建
            executorService.submit(() -> {
                try {
                    //1.查询数据库
                    R r1 = deFallBack.apply(id);
                    //2.写入Redis
                   this.setWithLogicalExpire(key, r1, dateTime, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //6.释放锁
                    unlock(lockKey);
                }
            });
        }
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }



}
