package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ShopMapper shopMapper;
    @Resource
    private CacheClient  cacheClient;

    private static ExecutorService executorService = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) throws InterruptedException {
        //1.根据缓存穿透的方法调用
        //**Shop shop = queryWithPassThrough(id);
        //2.根据缓存击穿的方法调用（互斥锁）
        //Shop shop = queryWithMutex(id);
        //3.根据逻辑过期的方法调用
        //Shop shop = queryWithLogicalExpire(id);
        //使用工具类缓存穿脱的方法调用
        Shop shop = this.cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById,
                CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //使用工具类逻辑过期的方法调用
//        Shop shop = this.cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById,
//                10L , TimeUnit.SECONDS);
        if(shop == null){
            return Result.fail("商铺信息不存在");
        }
        return Result.ok(shop);
    }

    public Shop queryWithLogicalExpire(Long id) throws InterruptedException {
        //1.查询Redis缓存中个是否有商户信息
        String shopCache = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.没有则返回空
        if (StringUtils.isBlank(shopCache)) {
            return null;
        }
        //3.有则判断缓存是否过期
        RedisData redisData = JSONUtil.toBean(shopCache, RedisData.class);
        //把JSON反序列化为对象
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            //3.1缓存未过期则直接返回
            return shop;
        }
        //3.2缓存已过期则进行缓存重建
        //4.尝试获取互斥锁
        String key = LOCK_SHOP_KEY + id;
        boolean isAquire = tryLock(key);
        if(!isAquire){
            //4.1未获得锁直接返回数据(旧数据)
            return  shop;
        }
        //4.2获得调用线程进行重建
        if(isAquire){
            //5.开启线程进行缓存重建
            executorService.submit(() -> {
               try {
                    shop2Redis(id, 10L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //6.释放锁
                    unlock(key);
                }
                });
        }
        return shop;
    }

    public Shop queryWithMutex(Long id) throws InterruptedException {
       //1.查询Redis缓存中个是否有商户信息
        String shopCache = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.有则返回
        if(StringUtils.isNotBlank(shopCache)){
            return  JSONUtil.toBean(shopCache,Shop.class);
        }
        if (shopCache != null) {
            return null;
        }
        String key = LOCK_SHOP_KEY + id;
        Shop shop = null;
        // 使用循环替代递归
        while (true) {
            boolean isAquire = tryLock(key);
            if (isAquire) {
                try {
                    // 双重检查缓存
                    String cachedShop = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
                    if(StringUtils.isNotBlank(cachedShop)){
                        return JSONUtil.toBean(cachedShop,Shop.class);
                    }

                    System.out.println("获取锁成功" + Thread.currentThread().getId());
                    shop = getById(id);
                    Thread.sleep(200);

                    if(shop == null){
                        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",
                                RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                        return null;
                    }
                    stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                            RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
                    break; // 成功获取数据后退出循环
                } finally {
                    unlock(key);
                }
            } else {
                Thread.sleep(50);
            }
        }      return shop;
    }

    public Shop queryWithPassThrough(Long id){
        //1.查询Redis缓存中个是否有商户信息
        String shopCache = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.有则返回
        if(StringUtils.isNotBlank(shopCache)){
            return  JSONUtil.toBean(shopCache,Shop.class);
        }
        if (shopCache != null) {
            return null;
        }
        //3.没有则去数据库中查找
        Shop shop = getById(id);
        if(shop == null){
            //3.1数据库中没有则缓存一个空对象
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",
                    RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //3.2数据中有则加载中缓存中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    public void shop2Redis(Long id, Long expireTime) throws InterruptedException {
        //1.数据库中查询商户的信息
        Shop shop = getById(id);
        //模拟重建延时
        Thread.sleep(200L);
        //2.将信息保存到RedisData中
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        //3。将RedisData保存到Redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    @Transactional
    @Override
    public Result updateShop(Shop shop) {
        //1.判断id是否存在
        Long id = shop.getId();
        if(id == null){
            return Result.fail("id信息不能为空");
        }
        //2.更新数据库
        shopMapper.updateById(shop);
        String shopCache = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+shop.getId());
        //3.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
