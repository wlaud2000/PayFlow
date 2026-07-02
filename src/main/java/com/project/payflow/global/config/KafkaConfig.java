package com.project.payflow.global.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    // Spring Boot 4.x는 Kafka 자동 구성 없음 → KafkaTemplate 빈을 직접 정의해야 함
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.producer.acks}") String acks) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.ACKS_CONFIG, acks);
        var producerFactory = new DefaultKafkaProducerFactory<>(
                config, new StringSerializer(), new JacksonJsonSerializer<>());
        return new KafkaTemplate<>(producerFactory);
    }

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
