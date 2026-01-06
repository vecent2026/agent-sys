package com.trae.admin.common.health;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.TopicListing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ExecutionException;

@Component
@Slf4j
public class KafkaHealthIndicator implements HealthIndicator {

    @Autowired
    private KafkaAdmin kafkaAdmin;

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            // Check cluster connection by listing topics
            ListTopicsResult topicsResult = adminClient.listTopics();
            Collection<TopicListing> topics = topicsResult.listings().get();
            
            // Check if specific topics exist
            boolean sysLogTopicExists = topics.stream()
                    .anyMatch(topic -> "sys-log-topic".equals(topic.name()));
            
            // Build health details
            return Health.up()
                    .withDetail("topicCount", topics.size())
                    .withDetail("sysLogTopicExists", sysLogTopicExists)
                    .withDetail("brokerUrl", kafkaAdmin.getConfigurationProperties().getOrDefault("bootstrap.servers", "unknown"))
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Kafka health check interrupted", e);
            return Health.down()
                    .withDetail("error", "Interrupted while checking Kafka health")
                    .build();
        } catch (ExecutionException e) {
            log.error("Kafka health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Unexpected error during Kafka health check", e);
            return Health.down()
                    .withDetail("error", "Unexpected error: " + e.getMessage())
                    .build();
        }
    }
}