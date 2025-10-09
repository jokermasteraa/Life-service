package com.hmdp.service.impl;

import com.hmdp.config.RedissonConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIDWorker  redisIDWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient  redissonClient;
    @Resource
    private RabbitTemplate  rabbitTemplate;

    private final static DefaultRedisScript<Long> redisScript;
    static {
        redisScript = new DefaultRedisScript<Long>();
        redisScript.setResultType(Long.class);
        redisScript.setLocation(new ClassPathResource("seckill.lua"));
    }

    private static final BlockingQueue<VoucherOrder> voucherOrderQueue = new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init (){
        executor.submit(new voucherOrderHandler());
    }

    public class voucherOrderHandler implements Runnable {

        @Override
        public void run() {
            //1.获取数据
            while (true) {
                try {
                    VoucherOrder voucherOrder = voucherOrderQueue.take();
                    //2.进行
                    orderHandler(voucherOrder);

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

        }
    }

    IVoucherOrderService proxy;

    @Override
    public Result secVoucherOrder(Long voucherId) {
        Long id = UserHolder.getUser().getId();
        //1.执行lua脚本进行查询库存和订单
        Long resultType = stringRedisTemplate.execute(redisScript,
                Collections.emptyList(),
                voucherId.toString(),id.toString());
        //2.判断是否是重复下单或库存不足
        int result = resultType.intValue();
        if( result == 1 || result == 2 ){
            return result == 1 ? Result.fail("库存不足") : Result.fail("不可重复下单");
        }
        //3.将订单信息存入队列（订单id，优惠券id，用户id）
        VoucherOrder voucherOrder = new VoucherOrder();
        Long orderId = redisIDWorker.nextID("Order");

        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setId(orderId);

        //添加到队列
        //voucherOrderQueue.add(voucherOrder);

        //添加到RabbitMQ队列
        rabbitTemplate.convertAndSend("seckill.direct","seckill.order",voucherOrder);

        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //4.返回订单id
        return Result.ok(orderId);
    }



    private void orderHandler(VoucherOrder voucherOrder) {
        Long id1 = voucherOrder.getUserId();
        //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate,"Order:"+id1 );
        RLock lock = redissonClient.getLock("Lock:Order:"+id1);
        boolean isAquireLock = lock.tryLock();
        if(!isAquireLock){
            return ;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        //查询该用户是否已经下了该订单
        int count = query().eq("user_id", userId ).eq("voucher_id", voucherId).count();
        if(count > 0){
            log.info("用户只能下一单");
            return ;

        }
        //5. 充足创建订单(保存在订单表中)
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock",  0)
                .update();
        if(!success){
            log.info("秒杀券不足");
            return ;
        }
        //调用生成订单ID

        save(voucherOrder);
        //6. 返回订单ID
        log.info("创建订单成功");
        return ;

    }

    //    @Override
//    public Result secVoucherOrder(Long voucherId) {
//        //1.查询优惠券信息
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始
//        if(LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())){
//            return Result.fail("秒杀尚未开始");
//        }
//        //3.判断秒杀是否结束
//        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
//            return Result.fail("秒杀已经结束");
//        }
//        //4.查询库存是否充足
//        int stock = seckillVoucher.getStock();
//        //4.1 不充足则返回异常信息
//        if(stock < 1){
//            return Result.fail("秒杀券库存不足");
//        }
//        Long id1 = UserHolder.getUser().getId();
//        //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate,"Order:"+id1 );
//        RLock lock = redissonClient.getLock("Lock:Order:"+id1);
//        boolean isAquireLock = lock.tryLock();
//        if(!isAquireLock){
//            return Result.fail("不可重复下单");
//        }
//        try {
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//
//    }
}
