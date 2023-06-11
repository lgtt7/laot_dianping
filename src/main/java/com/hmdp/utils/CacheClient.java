package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        String jsonValue = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key,jsonValue,time,timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){

        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));


        //写入Redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    //缓存穿透
    public <R,ID> R queryWithPassThrough(String keyPreFix,ID id,Class<R> type,Function<ID,R> dbFallback,
                                         Long time,TimeUnit timeUnit){
        String key = keyPreFix + id;
        //从Redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json,type);
        }

        //判断命中的是否是空值
        if(json!=null){
            return null;
        }

        //不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        //不存在，返回错误
        if(r==null){
            //将空值缓存进Redis
            stringRedisTemplate.opsForValue().set(key,"",30,TimeUnit.MINUTES);
        }
        //存在，缓存至Redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(r),time,timeUnit);

        return r;
    }


    //缓存击穿
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,String lockKeyPre,
                                           Function<ID,R> dbFallBack,Long time,TimeUnit timeUnit){
        String key = keyPrefix + id;
        //从Redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在 如果不存在
        if(StrUtil.isBlank(json)){
            return  null;
        }
        //先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断数据是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期，直接返回店铺信息
            return r;
        }
        //过期需要缓存重建
        //获取互斥锁
        String lockKey = lockKeyPre + id;
        boolean isLock = tryLock(lockKey);
        //判断是否取锁成功
        if(isLock){
            //成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //查询数据库
                    R r1 = dbFallBack.apply(id);
                    //写入Redis
                    this.set(key,r1,time,timeUnit);
                }catch (Exception e){
                    throw new RuntimeException();
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });


        }
        return r;
    }



    //尝试获取锁
    private boolean tryLock(String key){
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    //释放锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }





}
