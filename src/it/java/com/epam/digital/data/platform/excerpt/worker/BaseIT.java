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

package com.epam.digital.data.platform.excerpt.worker;

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.IN_PROGRESS;

import com.epam.digital.data.platform.excerpt.dao.ExcerptRecord;
import com.epam.digital.data.platform.excerpt.dao.ExcerptTemplate;
import com.epam.digital.data.platform.excerpt.model.ExcerptEventDto;
import com.epam.digital.data.platform.excerpt.worker.repository.ExcerptRecordRepository;
import com.epam.digital.data.platform.excerpt.worker.repository.ExcerptTemplateRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9092",
    "port=9092"})
public abstract class BaseIT {

  @Autowired
  protected ExcerptRecordRepository excerptRecordRepository;
  @Autowired
  protected ExcerptTemplateRepository excerptTemplateRepository;

  @AfterEach
  void cleanUp() {
    excerptRecordRepository.deleteAll();
    excerptTemplateRepository.deleteAll();
  }

  protected ExcerptTemplate saveExcerptTemplateToDatabase(String name, String template) {
    var excerptTemplate = new ExcerptTemplate();
    excerptTemplate.setTemplateName(name);
    excerptTemplate.setTemplate(template);
    return excerptTemplateRepository.save(excerptTemplate);
  }

  protected ExcerptRecord saveExcerptRecordToDatabase(ExcerptEventDto excerptEventDto) {
    var excerptRecord = new ExcerptRecord();
    excerptRecord.setStatus(IN_PROGRESS);
    excerptRecord.setSignatureRequired(excerptEventDto.isRequiresSystemSignature());
    return excerptRecordRepository.save(excerptRecord);
  }
}
