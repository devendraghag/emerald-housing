package com.emerald.member.lock;

import com.emerald.member.exception.MembershipLockUnavailableException;
import com.emerald.member.exception.MembershipUpdateInProgressException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Coordinates membership writes for the same member across Member service instances. */
@Component
public class RedisMembershipLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisMembershipLock.class);
    private static final String KEY_PREFIX = "emerald:member:membership-lock:";
    private static final long RETRY_INTERVAL_MILLIS = 50;
    private static final DefaultRedisScript<Long> RELEASE_IF_OWNED = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redis;
    private final Duration waitTime;
    private final Duration leaseTime;

    public RedisMembershipLock(StringRedisTemplate redis,
            @Value("${membership.lock.wait-time:2s}") Duration waitTime,
            @Value("${membership.lock.lease-time:30s}") Duration leaseTime) {
        if (waitTime.isNegative() || leaseTime.isNegative() || leaseTime.isZero()) {
            throw new IllegalArgumentException("Membership lock wait must be non-negative and lease must be positive");
        }
        this.redis = redis;
        this.waitTime = waitTime;
        this.leaseTime = leaseTime;
    }

    public <T> T withMemberLock(UUID memberId, Supplier<T> action) {
        String key = KEY_PREFIX + memberId;
        String ownerToken = UUID.randomUUID().toString();
        acquire(key, ownerToken);
        try {
            return action.get();
        } finally {
            release(key, ownerToken);
        }
    }

    private void acquire(String key, String ownerToken) {
        long deadline = System.nanoTime() + waitTime.toNanos();
        while (true) {
            try {
                Boolean acquired = redis.opsForValue().setIfAbsent(key, ownerToken, leaseTime);
                if (Boolean.TRUE.equals(acquired)) {
                    return;
                }
                if (acquired == null) {
                    throw new MembershipLockUnavailableException("Redis did not confirm the membership lock");
                }
            } catch (DataAccessException exception) {
                throw new MembershipLockUnavailableException("Redis is unavailable for membership locking", exception);
            }

            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new MembershipUpdateInProgressException();
            }
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos,
                        TimeUnit.MILLISECONDS.toNanos(RETRY_INTERVAL_MILLIS)));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new MembershipLockUnavailableException("Waiting for the membership lock was interrupted", exception);
            }
        }
    }

    private void release(String key, String ownerToken) {
        try {
            redis.execute(RELEASE_IF_OWNED, List.of(key), ownerToken);
        } catch (DataAccessException exception) {
            // The lock has a lease and will expire. Do not hide a completed database result if Redis disconnected.
            LOGGER.warn("Could not release membership lock {}; it will expire after its lease", key, exception);
        }
    }
}
