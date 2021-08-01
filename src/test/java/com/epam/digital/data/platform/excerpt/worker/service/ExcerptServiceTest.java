package com.epam.digital.data.platform.excerpt.worker.service;

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.COMPLETED;
import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.epam.digital.data.platform.excerpt.dao.ExcerptRecord;
import com.epam.digital.data.platform.excerpt.dao.ExcerptTemplate;
import com.epam.digital.data.platform.excerpt.model.ExcerptEventDto;
import com.epam.digital.data.platform.excerpt.worker.exception.ExcerptProcessingException;
import com.epam.digital.data.platform.excerpt.worker.repository.ExcerptRecordRepository;
import com.epam.digital.data.platform.excerpt.worker.repository.ExcerptTemplateRepository;
import com.epam.digital.data.platform.integration.ceph.service.CephService;
import java.util.Base64;
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
  private static final String OBJ_KEY = templateName + "-" + excerptId + ".pdf";

  ExcerptService excerptService;

  @Mock
  ExcerptTemplateRepository templateRepository;
  @Mock
  ExcerptRecordRepository recordRepository;
  @Mock
  DocumentRenderer renderer;
  @Mock
  CephService datafactoryCephService;
  @Captor
  ArgumentCaptor<ExcerptRecord> excerptRecordCaptor;

  @BeforeEach
  void init() {
    excerptService = new ExcerptService(templateRepository,
        recordRepository, renderer, datafactoryCephService, BUCKET);
  }

  @Test
  void saveOnGeneration() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));

    when(templateRepository.findFirstByTemplateName(templateName))
        .thenReturn(Optional.of(mockExcerptTemplate()));

    byte[] bytes = {70, 71, 72};
    when(renderer.htmlToPdf(any())).thenReturn(bytes);

    // when
    excerptService.generateExcerpt(mockExcerptEventDto());

    // then
    assertThat(mockExcerptRecord.getStatus()).isEqualTo(COMPLETED);
    assertThat(mockExcerptRecord.getStatusDetails()).isNull();
    assertThat(mockExcerptRecord.getExcerptKey()).isEqualTo(OBJ_KEY);
    assertThat(mockExcerptRecord.getChecksum())
        .isEqualTo(DigestUtils.sha256Hex(Base64.getEncoder().encodeToString(bytes)));
    assertThat(mockExcerptRecord.getUpdatedAt()).isNotNull();
  }

  @Test
  void verifyGenerateExcerpt() {
    // given
    var mockExcerptRecord = new ExcerptRecord();
    when(recordRepository.findById(excerptId)).thenReturn(Optional.of(mockExcerptRecord));

    when(templateRepository.findFirstByTemplateName(templateName))
        .thenReturn(Optional.of(mockExcerptTemplate()));

    byte[] bytes = {70, 71, 72};
    when(renderer.htmlToPdf(any())).thenReturn(bytes);

    // when
    excerptService.generateExcerpt(mockExcerptEventDto());

    // then
    verify(datafactoryCephService)
        .putContent(BUCKET, OBJ_KEY, Base64.getEncoder().encodeToString(bytes));
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

    byte[] bytes = {70, 71, 72};
    when(renderer.htmlToPdf(any())).thenReturn(bytes);

    doThrow(new RuntimeException("message"))
        .when(datafactoryCephService).putContent(any(), any(), any());

    // when
    excerptService.generateExcerpt(mockExcerptEventDto());

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
    excerptService.generateExcerpt(mockExcerptEventDto());

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

    ExcerptEventDto input = mockExcerptEventDto();

    // when-then
    var exception = assertThrows(ExcerptProcessingException.class,
        () -> excerptService.generateExcerpt(input));

    assertThat(exception.getStatus()).isEqualTo(FAILED);
    assertThat(exception.getDetails()).isEqualTo("Record not found");
  }

  private ExcerptEventDto mockExcerptEventDto() {
    return new ExcerptEventDto(excerptId, templateName, excerptData, false);
  }

  private ExcerptTemplate mockExcerptTemplate() {
    var excerptTemplate = new ExcerptTemplate();
    excerptTemplate.setTemplateName(templateName);
    excerptTemplate.setTemplate("My name is [=name]");
    return excerptTemplate;
  }
}
