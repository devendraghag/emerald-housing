package com.emerald.member.lostfound;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class LostFoundKafkaConfiguration {
    @Bean
    NewTopic lostFoundEventsTopic() {
        return TopicBuilder.name(LostFoundEventPublisher.TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
