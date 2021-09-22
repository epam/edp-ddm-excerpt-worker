package com.epam.digital.data.platform.excerpt.worker.listener;

import com.epam.digital.data.platform.excerpt.model.ExcerptEventDto;
import com.epam.digital.data.platform.excerpt.model.Request;
import com.epam.digital.data.platform.excerpt.worker.service.ExcerptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ExcerptListener {

  private final Logger log = LoggerFactory.getLogger(ExcerptListener.class);

  private final ExcerptService excerptService;

  public ExcerptListener(ExcerptService excerptService) {
    this.excerptService = excerptService;
  }

  @KafkaListener(
      topics = "\u0023{kafkaProperties.topic}",
      groupId = "\u0023{kafkaProperties.groupId}",
      containerFactory = "concurrentKafkaListenerContainerFactory")
  public void generate(Request<ExcerptEventDto> input) {
    log.info("Kafka event received");
    if (input.getPayload() != null) {
      log.debug(
          "Generate Excerpt with template: {}, record id: {}",
          input.getPayload().getExcerptType(),
          input.getPayload().getRecordId());
    }

    excerptService.generateExcerpt(input.getPayload());
  }
}
