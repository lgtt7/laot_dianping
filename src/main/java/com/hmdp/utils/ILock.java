package com.hmdp.utils;

public interface ILock {
    /**
     * 尝试获取锁
     * @param timeOutSec 超时时间
     * @return
     */
    boolean tryLock(long timeOutSec);


    /**
     * 释放锁
     */
    void unlock();
}
