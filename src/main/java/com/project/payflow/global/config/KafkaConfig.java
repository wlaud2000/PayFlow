package com.project.payflow.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class KafkaConfig {

    // init 컨테이너와 이중화: Docker Compose 없이 앱만 기동해도 토픽 자동 생성
    @Bean
    public KafkaAdmin.NewTopics paymentTopics() {
        return new KafkaAdmin.NewTopics(
                // 일반 토픽: partition 3 (컨슈머 최대 3개 병렬 처리 가능)
                TopicBuilder.name("payment-approved").partitions(3).replicas(1).build(),
                TopicBuilder.name("payment-stock-decreased").partitions(3).replicas(1).build(),
                TopicBuilder.name("payment-stock-failed").partitions(3).replicas(1).build(),
                TopicBuilder.name("payment-point-earned").partitions(3).replicas(1).build(),
                TopicBuilder.name("payment-point-failed").partitions(3).replicas(1).build(),
                TopicBuilder.name("payment-compensation").partitions(3).replicas(1).build(),
                // DLQ 토픽: partition 1 (운영자 수동 재처리, 단일 Consumer 용도)
                TopicBuilder.name("payment-approved-dlq").partitions(1).replicas(1).build(),
                TopicBuilder.name("payment-stock-dlq").partitions(1).replicas(1).build(),
                TopicBuilder.name("payment-point-dlq").partitions(1).replicas(1).build()
        );
    }
}
