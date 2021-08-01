package com.epam.digital.data.platform.excerpt.worker.listener;

import com.epam.digital.data.platform.excerpt.model.ExcerptEventDto;
import com.epam.digital.data.platform.excerpt.model.Request;
import com.epam.digital.data.platform.excerpt.worker.service.ExcerptService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ExcerptListener {

  private final ExcerptService excerptService;

  public ExcerptListener(ExcerptService excerptService) {
    this.excerptService = excerptService;
  }

  @KafkaListener(
      topics = "\u0023{kafkaProperties.topic}",
      groupId = "\u0023{kafkaProperties.groupId}",
      containerFactory = "concurrentKafkaListenerContainerFactory")
  public void read(Request<ExcerptEventDto> input) {
    excerptService.generateExcerpt(input.getPayload());
  }
}
