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
import com.epam.digital.data.platform.starter.audit.model.AuditUserInfo;
import com.epam.digital.data.platform.starter.audit.model.EventType;
import com.epam.digital.data.platform.starter.audit.service.AbstractAuditFacade;
import com.epam.digital.data.platform.starter.audit.service.AuditService;
import com.epam.digital.data.platform.starter.security.jwt.TokenParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class KafkaEventsFacade extends AbstractAuditFacade {

  static final String KAFKA_REQUEST = "Kafka request. Method: ";

  private final TraceProvider traceProvider;
  private final TokenParser tokenParser;
  private final AuditSourceInfoProvider auditSourceInfoProvider;

  public KafkaEventsFacade(
      AuditService auditService,
      @Value("${spring.application.name:excerpt-worker}") String appName,
      Clock clock,
      TraceProvider traceProvider,
      TokenParser tokenParser,
      AuditSourceInfoProvider auditSourceInfoProvider) {
    super(auditService, appName, clock);
    this.traceProvider = traceProvider;
    this.tokenParser = tokenParser;
    this.auditSourceInfoProvider = auditSourceInfoProvider;
  }

  public void sendKafkaAudit(EventType eventType, String methodName,
                             String action, String step, String id,
                             String result) {
    var event = createBaseAuditEvent(
        eventType, KAFKA_REQUEST + methodName, traceProvider.getRequestId())
            .setSourceInfo(auditSourceInfoProvider.getAuditSourceInfo());

    var context = auditService.createContext(action, step, null, id, null, result);
    event.setContext(context);
    setUserInfoToEvent(event, traceProvider.getAccessToken());

    auditService.sendAudit(event.build());
  }

  private void setUserInfoToEvent(GroupedAuditEventBuilder event, String jwt) {
    if (jwt == null) {
      return;
    }

    var jwtClaimsDto = tokenParser.parseClaims(jwt);
    var userInfo = AuditUserInfo.AuditUserInfoBuilder.anAuditUserInfo()
            .userName(jwtClaimsDto.getFullName())
            .userKeycloakId(jwtClaimsDto.getSubject())
            .userDrfo(jwtClaimsDto.getDrfo())
            .build();
    event.setUserInfo(userInfo);
  }
}