/*
 * Copyright 2021 EPAM Systems.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.digital.data.platform.excerpt.worker.listener;

import com.epam.digital.data.platform.excerpt.model.ExcerptEventDto;
import com.epam.digital.data.platform.excerpt.model.Request;
import com.epam.digital.data.platform.excerpt.worker.audit.AuditableListener;
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

  @AuditableListener(action = "EXCERPT GENERATION")
  @KafkaListener(
      topics = "\u0023{kafkaProperties.topics['generate-excerpt']}",
      groupId = "\u0023{kafkaProperties.consumer.groupId}",
      containerFactory = "concurrentKafkaListenerContainerFactory")
  public void generate(Request<ExcerptEventDto> input) {
    log.info("Kafka event received");
    if (input.getPayload() != null) {
      log.info(
          "Generate Excerpt with template: {}, record id: {}",
          input.getPayload().getExcerptType(),
          input.getPayload().getRecordId());
    }

    excerptService.generateExcerpt(input.getPayload());
  }
}
