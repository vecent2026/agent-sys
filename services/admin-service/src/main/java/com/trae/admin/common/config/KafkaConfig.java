package com.trae.admin.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Slf4j
public class KafkaConfig {

    // @Bean
    // public CommonErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
    //     log.info("Configuring Kafka DefaultErrorHandler with DeadLetterPublishingRecoverer");
    //     // Retry 3 times with 1 second interval, then send to DLT
    //     return new DefaultErrorHandler(
    //             new DeadLetterPublishingRecoverer(template),
    //             new FixedBackOff(1000L, 3)
    //     );
    // }
}
