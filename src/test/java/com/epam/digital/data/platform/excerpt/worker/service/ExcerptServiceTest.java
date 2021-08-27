package com.epam.digital.data.platform.excerpt.worker.service;

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.COMPLETED;
import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.epam.digital.data.platform.integration.ceph.dto.CephObject;
import com.epam.digital.data.platform.integration.ceph.service.CephService;
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
  DocumentRenderer renderer;
  @Mock
  CephService datafactoryCephService;
  @Mock
  DigitalSignatureFileRestClient digitalSignatureFileRestClient;
  @Captor
  ArgumentCaptor<ExcerptRecord> excerptRecordCaptor;

  @BeforeEach
  void init() {
    excerptService = new ExcerptService(templateRepository,
        recordRepository, renderer, datafactoryCephService, digitalSignatureFileRestClient, BUCKET);
  }

  @Test
  void saveOnGenerationWithoutSignature() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));

    when(templateRepository.findFirstByTemplateName(templateName))
        .thenReturn(Optional.of(mockExcerptTemplate()));

    when(renderer.htmlToPdf(any())).thenReturn(RENDERED_PDF_BYTES);

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
    when(datafactoryCephService.getObject(eq(BUCKET), anyString()))
            .thenReturn(Optional.of(new CephObject(SIGNED_OBJ_BYTES, Map.of())));

    when(renderer.htmlToPdf(any())).thenReturn(RENDERED_PDF_BYTES);

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
  void verifyGenerateExcerpt() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));

    when(templateRepository.findFirstByTemplateName(templateName))
        .thenReturn(Optional.of(mockExcerptTemplate()));

    when(renderer.htmlToPdf(any())).thenReturn(RENDERED_PDF_BYTES);

    // when
    excerptService.generateExcerpt(mockExcerptEventDto(false));

    // then
    verify(datafactoryCephService)
        .putObject(eq(BUCKET), anyString(), eq(new CephObject(RENDERED_PDF_BYTES, Map.of())));
    verify(renderer).templateToHtml(any(), any());
    verify(renderer).htmlToPdf(any());
    verify(recordRepository).save(any());
  }

  @Test
  void writeErrorToDatabaseWhenPutContentToCephThrowsException() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));

    when(templateRepository.findFirstByTemplateName(templateName))
        .thenReturn(Optional.of(mockExcerptTemplate()));

    when(renderer.htmlToPdf(any())).thenReturn(RENDERED_PDF_BYTES);

    doThrow(new RuntimeException("message"))
        .when(datafactoryCephService).putObject(any(), any(), any());

    // when
    excerptService.generateExcerpt(mockExcerptEventDto(false));

    // then
    verify(recordRepository).save(excerptRecordCaptor.capture());
    var res = excerptRecordCaptor.getValue();
    assertThat(res.getStatus()).isEqualTo(FAILED);
    assertThat(res.getStatusDetails()).isEqualTo("message");
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
    assertThat(exception.getDetails()).isEqualTo("Record not found");
  }

  @Test
  void writeErrorToDatabaseIfExceprtSignageFailure() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));
    when(templateRepository.findFirstByTemplateName(templateName))
            .thenReturn(Optional.of(mockExcerptTemplate()));
    when(renderer.htmlToPdf(any())).thenReturn(RENDERED_PDF_BYTES);

    when(digitalSignatureFileRestClient.sign(any()))
            .thenReturn(new SignFileResponseDto(false));

    // when
    excerptService.generateExcerpt(mockExcerptEventDto(true));

    // then
    verify(recordRepository).save(excerptRecordCaptor.capture());
    var res = excerptRecordCaptor.getValue();
    assertThat(res.getStatus()).isEqualTo(FAILED);
    assertThat(res.getStatusDetails()).isEqualTo("Excerpt signage failed");
    assertThat(mockExcerptRecord.getUpdatedAt()).isNotNull();
  }

  @Test
  void writeErrorToDatabaseIfSignedContentNotFoundInCeph() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));
    when(templateRepository.findFirstByTemplateName(templateName))
            .thenReturn(Optional.of(mockExcerptTemplate()));
    when(renderer.htmlToPdf(any())).thenReturn(RENDERED_PDF_BYTES);
    when(digitalSignatureFileRestClient.sign(any()))
            .thenReturn(new SignFileResponseDto(true));

    when(datafactoryCephService.getObject(eq(BUCKET), anyString()))
            .thenReturn(Optional.empty());

    // when
    excerptService.generateExcerpt(mockExcerptEventDto(true));

    // then
    verify(recordRepository).save(excerptRecordCaptor.capture());
    var res = excerptRecordCaptor.getValue();
    assertThat(res.getStatus()).isEqualTo(FAILED);
    assertThat(res.getStatusDetails()).isEqualTo("Signed excerpt was not found in ceph");
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

