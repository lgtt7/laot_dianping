package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1678665600;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        //生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //拼接并返回

        return timeStamp<<32|count;
    }


    //查询开始时间
    public static void main(String[] args) {
        LocalDateTime t = LocalDateTime.of(2023, 3, 13, 0, 0, 0);
        long l = t.toEpochSecond(ZoneOffset.UTC);
        System.out.println(l);
    }
}
