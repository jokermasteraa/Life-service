package com.hmdp.utils;

public interface ILock {

     boolean tryLock(Long TimeoutSec);

     void unlock();
}
