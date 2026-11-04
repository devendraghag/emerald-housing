package com.emerald.member.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisMembershipLockIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private RedisMembershipLock firstLock;
    private RedisMembershipLock secondLock;

    @BeforeEach
    void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        firstLock = new RedisMembershipLock(redis, Duration.ofSeconds(3), Duration.ofSeconds(10));
        secondLock = new RedisMembershipLock(redis, Duration.ofSeconds(3), Duration.ofSeconds(10));
    }

    @AfterEach
    void disconnect() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void independentLockObjectsSerializeTheSameMemberAcrossThreads() throws Exception {
        UUID memberId = UUID.randomUUID();
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> firstLock.withMemberLock(memberId, () -> {
                updateMaximum(active.incrementAndGet(), maximumActive);
                firstInside.countDown();
                await(releaseFirst);
                active.decrementAndGet();
                return "first";
            }));
            assertTrue(firstInside.await(5, TimeUnit.SECONDS), "first lock holder did not enter");
            var second = workers.submit(() -> secondLock.withMemberLock(memberId, () -> {
                updateMaximum(active.incrementAndGet(), maximumActive);
                active.decrementAndGet();
                return "second";
            }));
            Thread.sleep(200);
            assertEquals(1, maximumActive.get(), "the second owner must wait for the first Redis lease holder");
            releaseFirst.countDown();
            assertEquals("first", first.get(5, TimeUnit.SECONDS));
            assertEquals("second", second.get(5, TimeUnit.SECONDS));
            assertEquals(1, maximumActive.get());
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
        }
    }

    private static void updateMaximum(int value, AtomicInteger maximum) {
        maximum.accumulateAndGet(value, Math::max);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("test release latch timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
