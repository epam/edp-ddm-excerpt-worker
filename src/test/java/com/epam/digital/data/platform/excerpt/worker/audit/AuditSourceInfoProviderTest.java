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

import com.epam.digital.data.platform.excerpt.worker.util.Header;
import com.epam.digital.data.platform.starter.audit.model.AuditSourceInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static com.epam.digital.data.platform.excerpt.worker.audit.AuditEventUtils.BUSINESS_ACTIVITY;
import static com.epam.digital.data.platform.excerpt.worker.audit.AuditEventUtils.BUSINESS_PROCESS;
import static com.epam.digital.data.platform.excerpt.worker.audit.AuditEventUtils.SOURCE_APPLICATION;
import static com.epam.digital.data.platform.excerpt.worker.audit.AuditEventUtils.SOURCE_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

class AuditSourceInfoProviderTest {

  private final AuditSourceInfoProvider auditSourceInfoProvider = new AuditSourceInfoProvider();

  @Test
  void expectCorrectAuditSourceInfoRetrievedFromMdc() {
    MDC.put(Header.X_SOURCE_SYSTEM.getHeaderName().toLowerCase(), SOURCE_SYSTEM);
    MDC.put(Header.X_SOURCE_APPLICATION.getHeaderName().toLowerCase(), SOURCE_APPLICATION);
    MDC.put(Header.X_SOURCE_BUSINESS_PROCESS.getHeaderName().toLowerCase(), BUSINESS_PROCESS);
    MDC.put(Header.X_SOURCE_BUSINESS_ACTIVITY.getHeaderName().toLowerCase(), BUSINESS_ACTIVITY);

    var actualSourceInfo = auditSourceInfoProvider.getAuditSourceInfo();

    var expectedSourceInfo =
        AuditSourceInfo.AuditSourceInfoBuilder.anAuditSourceInfo()
            .system(SOURCE_SYSTEM)
            .application(SOURCE_APPLICATION)
            .businessProcess(BUSINESS_PROCESS)
            .businessActivity(BUSINESS_ACTIVITY)
            .build();

    assertThat(actualSourceInfo).usingRecursiveComparison().isEqualTo(expectedSourceInfo);
  }

  @AfterEach
  void afterEach() {
    MDC.clear();
  }
}
