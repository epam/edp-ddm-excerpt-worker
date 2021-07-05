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

package com.epam.digital.data.platform.excerpt.worker.audit;

import com.epam.digital.data.platform.excerpt.model.ExcerptEventDto;
import com.epam.digital.data.platform.excerpt.model.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import({AopAutoConfiguration.class})
@SpringBootTest(
    classes = {
      AuditAspect.class,
      KafkaAuditProcessor.class,
      AuditKafkaEventsAspectTest.MockListener.class
    })
class AuditKafkaEventsAspectTest {

  @Autowired
  private MockListener mockListener;

  @MockBean
  private KafkaEventsFacade kafkaEventsFacade;

  @Test
  void expectAuditAspectBeforeAndAfterListener() {
    var excerptEventDto = new ExcerptEventDto();
    excerptEventDto.setRecordId(UUID.randomUUID());
    mockListener.generate(new Request<>(excerptEventDto));

    verify(kafkaEventsFacade, times(2))
            .sendKafkaAudit(any(), any(), any(), any(), any(), any());
  }

  @TestComponent
  static class MockListener {

    @AuditableListener(action = "GENERATE EXCERPT")
    @KafkaListener
    void generate(Request<ExcerptEventDto> request) {}
  }
}
