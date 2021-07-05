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
import com.epam.digital.data.platform.starter.audit.model.EventType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Component;

@Component
public class KafkaAuditProcessor {

  static final String BEFORE = "BEFORE";
  static final String AFTER = "AFTER";

  private final KafkaEventsFacade kafkaEventsFacade;

  public KafkaAuditProcessor(KafkaEventsFacade kafkaEventsFacade) {
    this.kafkaEventsFacade = kafkaEventsFacade;
  }

  public Object process(ProceedingJoinPoint joinPoint, String action, Request<ExcerptEventDto> request)
      throws Throwable {
    return prepareAndSendKafkaAudit(joinPoint, action, request);
  }

  private Object prepareAndSendKafkaAudit(
      ProceedingJoinPoint joinPoint, String action, Request<ExcerptEventDto> request)
      throws Throwable {

    String methodName = joinPoint.getSignature().getName();

    kafkaEventsFacade.sendKafkaAudit(
        EventType.USER_ACTION,
        methodName,
        action,
        BEFORE,
        request.getPayload().getRecordId().toString(),
        null);

    Object result = joinPoint.proceed();

    kafkaEventsFacade.sendKafkaAudit(
        EventType.USER_ACTION,
        methodName,
        action,
        AFTER,
        request.getPayload().getRecordId().toString(),
        null);
    return result;
  }
}
