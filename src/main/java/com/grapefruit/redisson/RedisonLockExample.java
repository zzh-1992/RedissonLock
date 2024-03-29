package com.grapefruit.redisson;

import org.redisson.Redisson;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author 柚子苦瓜茶
 * @version 1.0
 * @ModifyTime 2020/11/12 23:08:11
 */
@RestController
public class RedisonLockExample {

    static final String LOCK = "lock";

    public static void main(String[] args) throws InterruptedException {

        ThreadLocal<Long> threadLocal = new ThreadLocal<Long>();

        long threadId = Thread.currentThread().getId();
        threadLocal.set(threadId);

        Config config = new Config();
        //(config.useSingleServer().setTimeout(1000000)).setAddress("redis://192.168.2.115:6380").setPassword("123456");
        (config.useSingleServer().setTimeout(1000000)).setAddress("redis://47.115.42.52:6379").setPassword("123456");
        RedissonClient redissonClient = Redisson.create(config);

        RLock lock = redissonClient.getLock(LOCK + threadId);

        System.out.println(lock.tryLock()?"拿到锁了":"没拿到锁");

        Thread.sleep(1000);
        // 释放锁
        RFuture<Void> voidRFuture = lock.unlockAsync(threadLocal.get());
        voidRFuture.isSuccess();
        System.out.println(voidRFuture.toString());


    }
}
