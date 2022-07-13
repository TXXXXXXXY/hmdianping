package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //缓存穿透方案
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback
            ,Long time, TimeUnit unit){
        String key = keyPrefix+id;
        //1从redis查询商铺缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        //2判断是否存在
        if(StrUtil.isNotBlank(Json)){
            //3存在直接返回
            return JSONUtil.toBean(Json, type);
        }
        if("".equals(Json)){
            return null;
        }
        //4不存在根据id查询数据库
        R r = dbFallback.apply(id);
        //5不存在 返回错误
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //6存在，写入redis
        this.set(key,r,time,unit);
        //7返回
        return r;
    }
    private static final ExecutorService CACHE_REBUILE_EXCUTOR = Executors.newFixedThreadPool(10);
    //逻辑过期
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id
            ,Class<R> type,Function<ID,R> dbFallback,Long time, TimeUnit unit){
        String key = keyPrefix+id;
        //1从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2判断是否存在
        if(StrUtil.isBlank(json)){
            //3存在直接返回
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json,RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime =redisData.getExpireTime();

        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        //已过期需要实现缓存重建
        //获取互斥锁
        //判断是否获取成功
        String lockKey = LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            //成功，开启独立线程，实现缓存重建
            CACHE_REBUILE_EXCUTOR.submit(()->{
                //重建缓存
                try {
                    //先查数据库
                    R r1 = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key, r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException();
                }finally {
                    unLock(lockKey);
                }
            });
        }

        //7返回
        return r;
    }
    private boolean tryLock(String key){
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",LOCK_SHOP_TTL,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
