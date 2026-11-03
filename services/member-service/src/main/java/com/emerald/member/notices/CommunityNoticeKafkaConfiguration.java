package com.emerald.member.notices;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class CommunityNoticeKafkaConfiguration {
    @Bean
    NewTopic communityNoticesTopic() {
        return TopicBuilder.name(CommunityNoticeEventPublisher.TOPIC).partitions(1).replicas(1).build();
    }
}
