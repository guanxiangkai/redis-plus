package com.actomize.redis.plus.queue;

/**
 * 队列订阅句柄。
 */
public interface QueueSubscription {

    /**
     * 停止订阅。
     */
    void stop();

    /**
     * 当前订阅是否仍在运行。
     */
    boolean isRunning();
}
