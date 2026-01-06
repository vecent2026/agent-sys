package com.trae.admin.modules.log.consumer;

import com.trae.admin.modules.log.entity.SysLogDocument;
import com.trae.admin.modules.log.repository.SysLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class LogConsumer {

    private final SysLogRepository sysLogRepository;

    @KafkaListener(topics = "sys-log-topic", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(List<org.apache.kafka.clients.consumer.ConsumerRecord<String, SysLogDocument>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        try {
            log.info("Received {} records from Kafka", records.size());
            List<SysLogDocument> logs = records.stream()
                .map(record -> record.value())
                .filter(val -> val != null)
                .toList();
            
            if (!logs.isEmpty()) {
                sysLogRepository.saveAll(logs);
                log.info("Saved {} logs to Elasticsearch", logs.size());
            }
        } catch (Exception e) {
            log.error("Failed to save logs to Elasticsearch", e);
            throw e;
        }
    }
}
