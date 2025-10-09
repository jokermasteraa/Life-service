package com.hmdp;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIDWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



@SpringBootTest
public class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;
    @Autowired
    private ShopServiceImpl shopService;
    @Resource
    private RedisIDWorker  redisIDWorker;

    @Test
    public void test() throws InterruptedException {
        shopService.shop2Redis(2L,10L);
    }

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testId() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable task =() -> {
            for (int i = 0; i < 100; i++) {
                Long id = redisIDWorker.nextID("Order");
                System.out.println(id);
            }countDownLatch.countDown();
        };
        long beginning = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println(end-beginning);
    }


}
