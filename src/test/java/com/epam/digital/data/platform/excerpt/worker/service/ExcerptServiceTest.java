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

package com.epam.digital.data.platform.excerpt.worker.service;

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.COMPLETED;
import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.FAILED;
import static com.epam.digital.data.platform.excerpt.worker.service.ExcerptService.EXCERPT_CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.epam.digital.data.platform.dso.api.dto.SignFileResponseDto;
import com.epam.digital.data.platform.dso.client.DigitalSignatureFileRestClient;
import com.epam.digital.data.platform.excerpt.dao.ExcerptRecord;
import com.epam.digital.data.platform.excerpt.dao.ExcerptTemplate;
import com.epam.digital.data.platform.excerpt.model.ExcerptEventDto;
import com.epam.digital.data.platform.excerpt.worker.exception.ExcerptProcessingException;
import com.epam.digital.data.platform.excerpt.worker.repository.ExcerptRecordRepository;
import com.epam.digital.data.platform.excerpt.worker.repository.ExcerptTemplateRepository;
import com.epam.digital.data.platform.integration.ceph.model.CephObject;
import com.epam.digital.data.platform.integration.ceph.model.CephObjectMetadata;
import com.epam.digital.data.platform.integration.ceph.service.CephService;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExcerptServiceTest {

  private static final String BUCKET = "bucket";
  private static final UUID excerptId = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String templateName = "excerptName";
  private static final Map<String, Object> excerptData = Map.of("field", "data");
  private static final byte[] RENDERED_PDF_BYTES = {70, 71, 72};
  private static final byte[] SIGNED_OBJ_BYTES = {70, 71, 72, 73};

  ExcerptService excerptService;

  @Mock
  ExcerptTemplateRepository templateRepository;
  @Mock
  ExcerptRecordRepository recordRepository;
  @Mock
  HtmlRenderer htmlRenderer;
  @Mock
  PdfRenderer pdfRenderer;
  @Mock
  CephService datafactoryCephService;
  @Mock
  DigitalSignatureFileRestClient digitalSignatureFileRestClient;
  @Captor
  ArgumentCaptor<ExcerptRecord> excerptRecordCaptor;

  @BeforeEach
  void init() {
    excerptService =
        new ExcerptService(
            templateRepository,
            recordRepository,
            htmlRenderer,
            pdfRenderer,
            datafactoryCephService,
            digitalSignatureFileRestClient,
            true,
            BUCKET);
  }

  @Test
  void saveOnGenerationWithoutSignature() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));

    when(templateRepository.findFirstByTemplateName(templateName))
        .thenReturn(Optional.of(mockExcerptTemplate()));

    when(pdfRenderer.render(any())).thenReturn(RENDERED_PDF_BYTES);

    // when
    excerptService.generateExcerpt(mockExcerptEventDto(false));

    // then
    assertThat(mockExcerptRecord.getStatus()).isEqualTo(COMPLETED);
    assertThat(mockExcerptRecord.getStatusDetails()).isNull();
    assertThat(UUID.fromString(mockExcerptRecord.getExcerptKey())).isNotNull();
    assertThat(mockExcerptRecord.getChecksum())
        .isEqualTo(DigestUtils.sha256Hex(RENDERED_PDF_BYTES));
    assertThat(mockExcerptRecord.getUpdatedAt()).isNotNull();
  }

  @Test
  void saveOnGenerationWithSignature() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));

    when(templateRepository.findFirstByTemplateName(templateName))
        .thenReturn(Optional.of(mockExcerptTemplate()));
    when(digitalSignatureFileRestClient.sign(any()))
        .thenReturn(new SignFileResponseDto(true));
    when(datafactoryCephService.get(eq(BUCKET), anyString()))
        .thenReturn(Optional.of(CephObject.builder()
                .content(new ByteArrayInputStream(SIGNED_OBJ_BYTES))
                .metadata(CephObjectMetadata.builder().build())
                .build()));

    when(pdfRenderer.render(any())).thenReturn(RENDERED_PDF_BYTES);

    // when
    excerptService.generateExcerpt(mockExcerptEventDto(true));

    // then
    assertThat(mockExcerptRecord.getStatus()).isEqualTo(COMPLETED);
    assertThat(mockExcerptRecord.getStatusDetails()).isNull();
    assertThat(UUID.fromString(mockExcerptRecord.getExcerptKey())).isNotNull();
    assertThat(mockExcerptRecord.getChecksum())
        .isEqualTo(DigestUtils.sha256Hex(SIGNED_OBJ_BYTES));
    assertThat(mockExcerptRecord.getUpdatedAt()).isNotNull();
  }

  @Test
  void saveOnGenerationWithDisabledDigSignProcessing() {
    excerptService =
            new ExcerptService(
                    templateRepository,
                    recordRepository,
                    htmlRenderer,
                    pdfRenderer,
                    datafactoryCephService,
                    digitalSignatureFileRestClient,
                    false,
                    BUCKET);
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));

    when(templateRepository.findFirstByTemplateName(templateName))
            .thenReturn(Optional.of(mockExcerptTemplate()));

    when(pdfRenderer.render(any())).thenReturn(RENDERED_PDF_BYTES);

    // when
    excerptService.generateExcerpt(mockExcerptEventDto(true));

    // then
    verify(digitalSignatureFileRestClient, never()).sign(any());
    verify(datafactoryCephService, never()).get(any(), any());
  }

  @Test
  void verifyGenerateExcerpt() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));

    when(templateRepository.findFirstByTemplateName(templateName))
        .thenReturn(Optional.of(mockExcerptTemplate()));

    when(pdfRenderer.render(any())).thenReturn(RENDERED_PDF_BYTES);

    // when
    excerptService.generateExcerpt(mockExcerptEventDto(false));

    var actualContentCapture = ArgumentCaptor.forClass(ByteArrayInputStream.class);
    // then
    verify(datafactoryCephService)
        .put(
            eq(BUCKET),
            anyString(),
            eq(EXCERPT_CONTENT_TYPE),
            eq(Collections.emptyMap()),
            actualContentCapture.capture());
    assertThat(actualContentCapture.getValue().readAllBytes()).isEqualTo(RENDERED_PDF_BYTES);
    verify(htmlRenderer).render(any(), any());
    verify(pdfRenderer).render(any());
    verify(recordRepository).save(any());
  }

  @Test
  void writeErrorToDatabaseWhenPutContentToCephThrowsException() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));

    when(templateRepository.findFirstByTemplateName(templateName))
        .thenReturn(Optional.of(mockExcerptTemplate()));

    when(pdfRenderer.render(any())).thenReturn(RENDERED_PDF_BYTES);

    doThrow(new RuntimeException("message"))
        .when(datafactoryCephService)
        .put(any(), any(), any(), any(), any());

    // when
    excerptService.generateExcerpt(mockExcerptEventDto(false));

    // then
    verify(recordRepository).save(excerptRecordCaptor.capture());
    var res = excerptRecordCaptor.getValue();
    assertThat(res.getStatus()).isEqualTo(FAILED);
    assertThat(res.getStatusDetails()).isEqualTo("Failed saving file to ceph");
    assertThat(mockExcerptRecord.getUpdatedAt()).isNotNull();
  }

  @Test
  void writeErrorToDatabaseWhenTemplateNotFound() {
    // given
    var mockExcerptRecord = new ExcerptRecord();

    when(templateRepository.findFirstByTemplateName(templateName)).thenReturn(Optional.empty());
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));

    // when
    excerptService.generateExcerpt(mockExcerptEventDto(false));

    // then
    verify(recordRepository).save(excerptRecordCaptor.capture());
    var res = excerptRecordCaptor.getValue();
    assertThat(res.getStatus()).isEqualTo(FAILED);
    assertThat(res.getStatusDetails()).isEqualTo("Excerpt template not found");
    assertThat(mockExcerptRecord.getUpdatedAt()).isNotNull();
  }

  @Test
  void shouldWriteErrorToDatabaseWhenRecordNotFound() {
    // given
    when(templateRepository.findFirstByTemplateName(templateName)).thenReturn(Optional.empty());
    when(recordRepository.findById(excerptId)).thenReturn(Optional.empty());

    ExcerptEventDto input = mockExcerptEventDto(false);

    // when-then
    var exception = assertThrows(ExcerptProcessingException.class,
        () -> excerptService.generateExcerpt(input));

    assertThat(exception.getStatus()).isEqualTo(FAILED);
    assertThat(exception.getDetails()).isEqualTo(
        "Record not found. Id: 11111111-1111-1111-1111-111111111111");
  }

  @Test
  void writeErrorToDatabaseIfExcerptSigningFailure() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));
    when(templateRepository.findFirstByTemplateName(templateName))
        .thenReturn(Optional.of(mockExcerptTemplate()));
    when(pdfRenderer.render(any())).thenReturn(RENDERED_PDF_BYTES);

    when(digitalSignatureFileRestClient.sign(any()))
        .thenReturn(new SignFileResponseDto(false));

    // when
    excerptService.generateExcerpt(mockExcerptEventDto(true));

    // then
    verify(recordRepository).save(excerptRecordCaptor.capture());
    var res = excerptRecordCaptor.getValue();
    assertThat(res.getStatus()).isEqualTo(FAILED);
    assertThat(res.getStatusDetails()).startsWith("Excerpt signing failed");
    assertThat(mockExcerptRecord.getUpdatedAt()).isNotNull();
  }

  @Test
  void writeErrorToDatabaseIfSignedContentNotFoundInCeph() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));
    when(templateRepository.findFirstByTemplateName(templateName))
        .thenReturn(Optional.of(mockExcerptTemplate()));
    when(pdfRenderer.render(any())).thenReturn(RENDERED_PDF_BYTES);
    when(digitalSignatureFileRestClient.sign(any()))
        .thenReturn(new SignFileResponseDto(true));

    when(datafactoryCephService.get(eq(BUCKET), anyString()))
        .thenReturn(Optional.empty());

    // when
    excerptService.generateExcerpt(mockExcerptEventDto(true));

    // then
    verify(recordRepository).save(excerptRecordCaptor.capture());
    var res = excerptRecordCaptor.getValue();
    assertThat(res.getStatus()).isEqualTo(FAILED);
    assertThat(res.getStatusDetails()).startsWith("Signed excerpt was not found in ceph");
    assertThat(mockExcerptRecord.getUpdatedAt()).isNotNull();
  }

  private ExcerptEventDto mockExcerptEventDto(boolean requiresSystemSignature) {
    return new ExcerptEventDto(excerptId, templateName, excerptData, requiresSystemSignature);
  }

  private ExcerptTemplate mockExcerptTemplate() {
    var excerptTemplate = new ExcerptTemplate();
    excerptTemplate.setTemplateName(templateName);
    excerptTemplate.setTemplate("My name is [=name]");
    return excerptTemplate;
  }
}

