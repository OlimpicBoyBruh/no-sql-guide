package dev.nosqlguide.redis.examples.driver;

import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Подписчик занимает соединение целиком: публиковать с него нельзя.
 * Пока подписка не подтверждена, PUBLISH некому доставлять — сообщение пропадёт.
 */
public final class PubSubDemo {

    public static void main(String[] args) {
        RedisClient client = RedisClient.create("redis://localhost:6379");
        try {
            run(client, System.out);
        } finally {
            client.shutdown();
        }
    }

    /** Весь сценарий демо; печатает шаги в out. Ключи демо начинаются с "demo:" и удаляются в конце. */
    public static void run(RedisClient client, PrintStream out) {
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch received = new CountDownLatch(3);
        StatefulRedisPubSubConnection<String, String> subscriber = client.connectPubSub();
        try {
            subscriber.addListener(new RedisPubSubAdapter<>() {
                @Override
                public void subscribed(String channel, long count) {
                    subscribed.countDown();
                }

                @Override
                public void message(String channel, String message) {
                    out.println("received: " + message);
                    received.countDown();
                }
            });
            subscriber.sync().subscribe("demo:news");
            if (!subscribed.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("подписка не подтвердилась");
            }
            try (var publisher = client.connect()) {
                var redis = publisher.sync();
                redis.publish("demo:news", "заказ принят");
                redis.publish("demo:news", "заказ собран");
                redis.publish("demo:news", "заказ выдан");
            }
            if (!received.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("не все сообщения дошли");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ожидание прервали", e);
        } finally {
            subscriber.close();
        }
    }

    private PubSubDemo() {
    }
}
