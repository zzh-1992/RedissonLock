package com.grapefruit.redisson;

import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * redisson分布式锁的相关代码
 */
public class Lock {

    public static void main(String[] args) {

        Config config = new Config();
        config.useSingleServer().setAddress("47.115.42.52").setDatabase(5);
        RedissonClient client = Redisson.create(config);
        RLock lock = client.getLock("lock");
        lock.lock();
        // 异步线程 ，默认时间30秒 private long lockWatchdogTimeout = 30 * 1000;
    }
}

/**
 *
 * @Override =====>锁
 * public void lock() {
 *     try {
 *         lockInterruptibly();
 *     } catch (InterruptedException e) {
 *         Thread.currentThread().interrupt();
 *     }
 * }
 *
 * @Override =====>
 * public void lockInterruptibly() throws InterruptedException {
 *     lockInterruptibly(-1, null);
 * }
 *
 * @Override =====>
 * public void lockInterruptibly(long leaseTime, TimeUnit unit) throws InterruptedException {
 *     long threadId = Thread.currentThread().getId();
 *     Long ttl = tryAcquire(leaseTime, unit, threadId);
 *     // lock acquired
 *     if (ttl == null) {
 *         return;
 *     }
 *
 *     RFuture<RedissonLockEntry> future = subscribe(threadId);
 *     commandExecutor.syncSubscription(future);
 *
 *     try {
 *         while (true) {
 *             ttl = tryAcquire(leaseTime, unit, threadId);
 *             // lock acquired
 *             if (ttl == null) {
 *                 break;
 *             }
 *
 *             // waiting for message
 *             if (ttl >= 0) {
 *                 getEntry(threadId).getLatch().tryAcquire(ttl, TimeUnit.MILLISECONDS);
 *             } else {
 *                 getEntry(threadId).getLatch().acquire();
 *             }
 *         }
 *     } finally {
 *         unsubscribe(future, threadId);
 *     }
 * //        get(lockAsync(leaseTime, unit));
 * }
 *
 * =====>
 * private Long tryAcquire(long leaseTime, TimeUnit unit, long threadId) {
 *     return get(tryAcquireAsync(leaseTime, unit, threadId));
 * }
 *
 * =====>
 * private <T> RFuture<Long> tryAcquireAsync(long leaseTime, TimeUnit unit, final long threadId) {
 *     if (leaseTime != -1) {
 *         return tryLockInnerAsync(leaseTime, unit, threadId, RedisCommands.EVAL_LONG);
 *     }
 *     RFuture<Long> ttlRemainingFuture = tryLockInnerAsync(commandExecutor.getConnectionManager().getCfg().getLockWatchdogTimeout(), TimeUnit.MILLISECONDS, threadId, RedisCommands.EVAL_LONG);
 *     ttlRemainingFuture.addListener(new FutureListener<Long>() {
 *         @Override
 *         public void operationComplete(Future<Long> future) throws Exception {
 *             if (!future.isSuccess()) {
 *                 return;
 *             }
 *
 *             Long ttlRemaining = future.getNow();
 *             // lock acquired
 *             if (ttlRemaining == null) {
 *                 scheduleExpirationRenewal(threadId);
 *             }
 *         }
 *     });
 *     return ttlRemainingFuture;
 * }
 *
 * =====>关键代码,redis使用脚本保证有原子性
 * <T> RFuture<T> tryLockInnerAsync(long leaseTime, TimeUnit unit, long threadId, RedisStrictCommand<T> command) {
 *     internalLockLeaseTime = unit.toMillis(leaseTime);
 *
 *     return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, command,
 *             "if (redis.call('exists', KEYS[1]) == 0) then " +
 *                     "redis.call('hset', KEYS[1], ARGV[2], 1); " +
 *                     "redis.call('pexpire', KEYS[1], ARGV[1]); " +
 *                     "return nil; " +
 *                     "end; " +
 *                     "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
 *                     "redis.call('hincrby', KEYS[1], ARGV[2], 1); " +
 *                     "redis.call('pexpire', KEYS[1], ARGV[1]); " +
 *                     "return nil; " +
 *                     "end; " +
 *                     "return redis.call('pttl', KEYS[1]);",
 *             Collections.<Object>singletonList(getName()), internalLockLeaseTime, getLockName(threadId));
 * }
 *
 * =====>定时任务
 * private void scheduleExpirationRenewal(final long threadId) {
 *     if (expirationRenewalMap.containsKey(getEntryName())) {
 *         return;
 *     }
 *
 *     Timeout task = commandExecutor.getConnectionManager().newTimeout(new TimerTask() {
 *         @Override
 *         public void run(Timeout timeout) throws Exception {
 *
 *             RFuture<Boolean> future = commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
 *                     "if (redis.call('hexists', KEYS[1], ARGV[2]) == 1) then " +
 *                             "redis.call('pexpire', KEYS[1], ARGV[1]); " +
 *                             "return 1; " +
 *                             "end; " +
 *                             "return 0;",
 *                     Collections.<Object>singletonList(getName()), internalLockLeaseTime, getLockName(threadId));
 *
 *             future.addListener(new FutureListener<Boolean>() {
 *                 @Override
 *                 public void operationComplete(Future<Boolean> future) throws Exception {
 *                     expirationRenewalMap.remove(getEntryName());
 *                     if (!future.isSuccess()) {
 *                         log.error("Can't update lock " + getName() + " expiration", future.cause());
 *                         return;
 *                     }
 *
 *                     if (future.getNow()) {
 *                         // reschedule itself
 *                         scheduleExpirationRenewal(threadId);
 *                     }
 *                 }
 *             });
 *         }
 *     }, internalLockLeaseTime / 3, TimeUnit.MILLISECONDS);  =====>延时1/3的时间启动
 *
 *     if (expirationRenewalMap.putIfAbsent(getEntryName(), task) != null) {
 *         task.cancel();
 *     }
 * }
 *
 * public RedissonLock(CommandAsyncExecutor commandExecutor, String name) {
 *         super(commandExecutor, name);
 *         this.commandExecutor = commandExecutor;
 *         this.id = commandExecutor.getConnectionManager().getId();
 *         this.internalLockLeaseTime = commandExecutor.getConnectionManager().getCfg().getLockWatchdogTimeout();
 *     }
 *
 * public long getLockWatchdogTimeout() {
 *         return lockWatchdogTimeout;
 *     }
 *
 * private long lockWatchdogTimeout = 30 * 1000; =====>定时任务的默认时间30秒
 */


