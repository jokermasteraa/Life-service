package com.hmdp;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;
    @Autowired
    private ShopServiceImpl shopService;

    @Test
    public void test() throws InterruptedException {
        shopService.shop2Redis(1L,10L);
    }

}
