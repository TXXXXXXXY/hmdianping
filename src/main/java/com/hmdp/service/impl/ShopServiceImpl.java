package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
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
    private CacheClient cacheClient;
    @Override
    public Result queryByid(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);
        Shop shop=cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY,id
                        ,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期
//        Shop shop = queryWithLogicalExpire(id);
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,
//                Shop.class,this::getById,10L,TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        //7返回
        return Result.ok(shop);
    }
//    private static final ExecutorService CACHE_REBUILE_EXCUTOR = Executors.newFixedThreadPool(10);
//    //逻辑过期
//    public Shop queryWithLogicalExpire(Long id){
//        String key = CACHE_SHOP_KEY+id;
//        //1从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2判断是否存在
//        if(StrUtil.isBlank(shopJson)){
//            //3存在直接返回
//            return null;
//        }
//        RedisData redisData = JSONUtil.toBean(shopJson,RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
//        LocalDateTime expireTime =redisData.getExpireTime();
//
//        if(expireTime.isAfter(LocalDateTime.now())){
//            return shop;
//        }
//        //已过期需要实现缓存重建
//        //获取互斥锁
//        //判断是否获取成功
//        String lockKey = LOCK_SHOP_KEY+id;
//        boolean isLock = tryLock(lockKey);
//        if(isLock){
//            //成功，开启独立线程，实现缓存重建
//            CACHE_REBUILE_EXCUTOR.submit(()->{
//                //重建缓存
//                try {
//                    this.saveShop2Redis(id,20L);
//                } catch (Exception e) {
//                    throw new RuntimeException();
//                }finally {
//                    unLock(lockKey);
//                }
//            });
//        }
//
//        //7返回
//        return shop;
//    }
    //互斥锁
//    public Shop queryWithMutex(Long id){
//        String key = CACHE_SHOP_KEY+id;
//        //1从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3存在直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        if("".equals(shopJson)){
//            return null;
//        }
//        //实现缓存重建
//        //4.1获取互斥锁
//        //4.2判断是否获取成功
//        //4.3失败，则休眠并重试
//        String lockKey = LOCK_SHOP_KEY+id;
//        Shop shop =null;
//        try{
//            boolean isLokc = tryLock(lockKey);
//            if(!isLokc){
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //4.4成功，根据id查询数据库
//            shop = getById(id);
//            //模拟重建延迟
//            Thread.sleep(200);
//            //5不存在 返回错误
//            if (shop == null) {
//                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//                return null;
//            }
//            //6存在，写入redis
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException();
//        } finally {
//            unLock(key);
//        }
//        //7返回
//        return shop;
//    }
    //缓存穿透方案
//    public Shop queryWithPassThrough(Long id){
//        String key = CACHE_SHOP_KEY+id;
//        //1从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //3存在直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        if("".equals(shopJson)){
//            return null;
//        }
//        //4不存在根据id查询数据库
//        Shop shop = getById(id);
//        //5不存在 返回错误
//        if (shop == null) {
//            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//        //6存在，写入redis
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //7返回
//        return shop;
//    }
//    private boolean tryLock(String key){
//        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",LOCK_SHOP_TTL,TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//    private void unLock(String key){
//        stringRedisTemplate.delete(key);
//    }
//    //数据预热，测试用
//    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
//        //查询店铺数据
//        Shop shop = getById(id);
//        //模拟重建延迟
//        Thread.sleep(200);
//        //封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
//    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id =shop.getId();
        if (id == null) {
            return Result.fail("店铺id为空");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}
