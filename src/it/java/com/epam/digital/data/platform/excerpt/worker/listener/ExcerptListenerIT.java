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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.epam.digital.data.platform.excerpt.dao.ExcerptRecord;
import com.epam.digital.data.platform.excerpt.model.ExcerptEventDto;
import com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus;
import com.epam.digital.data.platform.excerpt.model.Request;
import com.epam.digital.data.platform.excerpt.worker.BaseIT;
import com.epam.digital.data.platform.excerpt.worker.TestUtils;
import com.epam.digital.data.platform.integration.ceph.service.CephService;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

class ExcerptListenerIT extends BaseIT {

  @Autowired
  ExcerptListener excerptListener;
  @Captor
  ArgumentCaptor<ByteArrayInputStream> captor;
  @MockBean
  CephService datafactoryCephService;

  @Test
  void shouldCreateExcerpt() throws IOException {
    // given
    var template = TestUtils.readClassPathResource("/template.ftl");
    saveExcerptTemplateToDatabase("template", template);

    var requestJson = TestUtils.readClassPathResource("/json/request.json");
    var excerptEventDto = new ObjectMapper().readValue(requestJson, ExcerptEventDto.class);
    var excerptRecord = saveExcerptRecordToDatabase(excerptEventDto);

    excerptEventDto.setRecordId(excerptRecord.getId());
    
    // when
    excerptListener.generate(new Request<>(excerptEventDto));

    // then
    verify(datafactoryCephService).put(any(), any(), any(), any(), captor.capture());

    PDFTextStripper pdfTextStripper = new PDFTextStripper();
    var expectedPdf = pdfTextStripper.getText(PDDocument.load(new File("src/it/resources/expected.pdf")));
    var resultPdf = pdfTextStripper.getText(PDDocument.load(captor.getValue().readAllBytes()));

    var status = excerptRecordRepository.findById(excerptEventDto.getRecordId()).get().getStatus();

    assertThat(resultPdf).isEqualTo(expectedPdf);
    assertThat(status).isEqualTo(ExcerptProcessingStatus.COMPLETED);
  }

  @Test
  void failWhenTemplateNotFoundInDatabase() throws IOException {
    // given
    var requestJson = TestUtils.readClassPathResource("/json/request.json");
    var excerptEventDto = new ObjectMapper().readValue(requestJson, ExcerptEventDto.class);
    var excerptRecord = saveExcerptRecordToDatabase(excerptEventDto);

    excerptEventDto.setRecordId(excerptRecord.getId());

    // when
    excerptListener.generate(new Request<>(excerptEventDto));

    // then
    verify(datafactoryCephService, times(0)).put(any(), any(), any(), any(), any());

    ExcerptRecord resultRecord = excerptRecordRepository.findById(excerptEventDto.getRecordId()).get();

    assertThat(resultRecord.getStatus()).isEqualTo(ExcerptProcessingStatus.FAILED);
    assertThat(resultRecord.getStatusDetails()).isEqualTo("Excerpt template not found");
  }

  @Test
  void failWhenTemplateHasWrongFormat() throws IOException {
    // given
    var template = TestUtils.readClassPathResource("/template.ftl");
    template = template.replace("<head>", "<ABC>");
    saveExcerptTemplateToDatabase("template", template);

    var requestJson = TestUtils.readClassPathResource("/json/request.json");
    var excerptEventDto = new ObjectMapper().readValue(requestJson, ExcerptEventDto.class);
    var excerptRecord = saveExcerptRecordToDatabase(excerptEventDto);

    excerptEventDto.setRecordId(excerptRecord.getId());

    // when
    excerptListener.generate(new Request<>(excerptEventDto));

    // then
    verify(datafactoryCephService, times(0)).put(any(), any(), any(), any(), any());

    ExcerptRecord resultRecord = excerptRecordRepository.findById(excerptEventDto.getRecordId()).get();

    assertThat(resultRecord.getStatus()).isEqualTo(ExcerptProcessingStatus.FAILED);
    assertThat(resultRecord.getStatusDetails()).isEqualTo("HTML to PDF conversion fails");
  }
}
