package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.Synchronized;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2判断秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        //3判断秒杀是否结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束");
        }
        //4判断库存是否充足
        if (voucher.getStock()<1) {
            return Result.fail("库存不足");
        }
        //5.一人一单
        Long userId = UserHolder.getUser().getId();
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean isLock = simpleRedisLock.tryLock(1200L);
        if(!isLock){
            return Result.fail("禁止重复购买！");
        }try{
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            simpleRedisLock.unLock();
        }

    }
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //5.1查询订单
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //5.2判断是否存在
        if (count > 0) return Result.fail("禁止重复购买");
        //6扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock =  stock - 1")
                //失败概率高，改进如下。。。乐观锁特性
//                .eq("voucher_id",voucherId).eq("stock",voucher.getStock())
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            //扣减失败
            return Result.fail("库存不足");
        }
        //7创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //7.2用户id
        voucherOrder.setUserId(userId);
        //7.3代金卷id
        voucherOrder.setVoucherId(voucherId);

        save(voucherOrder);
        //8返回订单id
        return Result.ok(orderId);

    }
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        //5.一人一单
//        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {
//            //5.1查询订单
//            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//            //5.2判断是否存在
//            if (count > 0) return Result.fail("禁止重复购买");
//            //6扣减库存
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock =  stock - 1")
//                    //失败概率高，改进如下。。。乐观锁特性
////                .eq("voucher_id",voucherId).eq("stock",voucher.getStock())
//                    .eq("voucher_id", voucherId).gt("stock", 0)
//                    .update();
//            if (!success) {
//                //扣减失败
//                return Result.fail("库存不足");
//            }
//            //7创建订单
//            VoucherOrder voucherOrder = new VoucherOrder();
//            //7.1订单id
//            long orderId = redisIdWorker.nextId("order");
//            voucherOrder.setId(orderId);
//            //7.2用户id
//            voucherOrder.setUserId(userId);
//            //7.3代金卷id
//            voucherOrder.setVoucherId(voucherId);
//
//            save(voucherOrder);
//            //8返回订单id
//            return Result.ok(orderId);
//        }
//    }
}
