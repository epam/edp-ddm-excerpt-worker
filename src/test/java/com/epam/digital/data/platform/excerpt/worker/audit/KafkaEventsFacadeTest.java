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

import com.epam.digital.data.platform.excerpt.worker.service.TraceProvider;
import com.epam.digital.data.platform.starter.audit.model.AuditEvent;
import com.epam.digital.data.platform.starter.audit.model.AuditSourceInfo;
import com.epam.digital.data.platform.starter.audit.model.EventType;
import com.epam.digital.data.platform.starter.audit.service.AuditService;
import com.epam.digital.data.platform.starter.security.jwt.TokenParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static com.epam.digital.data.platform.excerpt.worker.audit.AuditEventUtils.createSourceInfo;
import static com.epam.digital.data.platform.excerpt.worker.audit.AuditEventUtils.createUserInfo;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TokenParser.class, ObjectMapper.class})
class KafkaEventsFacadeTest {

  private static final String APP_NAME = "application";
  private static final String REQUEST_ID = "1";
  private static final String METHOD_NAME = "method";
  private static final String ACTION = "CREATE";
  private static final String STEP = "BEFORE";
  private static final LocalDateTime CURR_TIME = LocalDateTime.of(2021, 4, 1, 11, 50);
  private static final String RESULT = "RESULT";

  private static final String EXCERPT_ID = "ID";

  private static String accessToken;

  private KafkaEventsFacade kafkaEventsFacade;

  @MockBean
  private AuditService auditService;
  @MockBean
  private AuditSourceInfoProvider auditSourceInfoProvider;
  @MockBean
  private TraceProvider traceProvider;
  @Autowired
  private TokenParser tokenParser;

  private final Clock clock =
          Clock.fixed(CURR_TIME.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

  private AuditSourceInfo mockSourceInfo;

  @BeforeAll
  static void init() throws IOException {
    accessToken = new String(IOUtils.toByteArray(
            KafkaEventsFacadeTest.class.getResourceAsStream("/accessToken.json")));
  }


  @BeforeEach
  void beforeEach() {
    kafkaEventsFacade =
        new KafkaEventsFacade(
            auditService, APP_NAME, clock, traceProvider, tokenParser, auditSourceInfoProvider);

    when(traceProvider.getRequestId()).thenReturn(REQUEST_ID);

    mockSourceInfo = createSourceInfo();
    when(auditSourceInfoProvider.getAuditSourceInfo())
            .thenReturn(mockSourceInfo);
  }

  @Test
  void expectCorrectAuditEventWhenJwtAbsent() {
    Map<String, Object> context = Map.of("action", ACTION, "step", STEP, "result", RESULT);
    when(auditService.createContext(ACTION, STEP, null, EXCERPT_ID, null, RESULT))
        .thenReturn(context);

    kafkaEventsFacade.sendKafkaAudit(
        EventType.USER_ACTION, METHOD_NAME, ACTION, STEP, EXCERPT_ID, RESULT);

    ArgumentCaptor<AuditEvent> auditEventCaptor = ArgumentCaptor.forClass(AuditEvent.class);

    verify(auditService).sendAudit(auditEventCaptor.capture());
    AuditEvent actualEvent = auditEventCaptor.getValue();

    var expectedEvent = AuditEvent.AuditEventBuilder.anAuditEvent()
            .application(APP_NAME)
            .name("Kafka request. Method: method")
            .requestId(REQUEST_ID)
            .sourceInfo(mockSourceInfo)
            .userInfo(null)
            .currentTime(clock.millis())
            .eventType(EventType.USER_ACTION)
            .context(context)
            .build();

    assertThat(actualEvent).usingRecursiveComparison().isEqualTo(expectedEvent);
  }

  @Test
  void expectCorrectAuditEventWhenJwtPresent() {
    Map<String, Object> context = Map.of("action", ACTION, "step", STEP, "result", RESULT);
    when(auditService.createContext(ACTION, STEP, null, EXCERPT_ID, null, RESULT))
        .thenReturn(context);
    when(traceProvider.getAccessToken()).thenReturn(accessToken);

    kafkaEventsFacade.sendKafkaAudit(
        EventType.USER_ACTION, METHOD_NAME, ACTION, STEP, EXCERPT_ID, RESULT);

    ArgumentCaptor<AuditEvent> auditEventCaptor = ArgumentCaptor.forClass(AuditEvent.class);

    verify(auditService).sendAudit(auditEventCaptor.capture());
    AuditEvent actualEvent = auditEventCaptor.getValue();

    var expectedEvent = AuditEvent.AuditEventBuilder.anAuditEvent()
            .application(APP_NAME)
            .name("Kafka request. Method: method")
            .requestId(REQUEST_ID)
            .sourceInfo(mockSourceInfo)
            .userInfo(createUserInfo())
            .currentTime(clock.millis())
            .eventType(EventType.USER_ACTION)
            .context(context)
            .build();

    assertThat(actualEvent).usingRecursiveComparison().isEqualTo(expectedEvent);
  }
}