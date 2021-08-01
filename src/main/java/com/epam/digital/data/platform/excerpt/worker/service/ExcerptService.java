package com.epam.digital.data.platform.excerpt.worker.service;

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.COMPLETED;
import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.FAILED;

import com.epam.digital.data.platform.excerpt.dao.ExcerptRecord;
import com.epam.digital.data.platform.excerpt.model.ExcerptEventDto;
import com.epam.digital.data.platform.excerpt.worker.exception.ExcerptProcessingException;
import com.epam.digital.data.platform.excerpt.worker.repository.ExcerptRecordRepository;
import com.epam.digital.data.platform.excerpt.worker.repository.ExcerptTemplateRepository;
import com.epam.digital.data.platform.integration.ceph.service.CephService;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExcerptService {

  private final ExcerptTemplateRepository templateRepository;
  private final ExcerptRecordRepository recordRepository;
  private final DocumentRenderer renderer;
  private final CephService datafactoryCephService;
  private final String bucket;

  public ExcerptService(
      ExcerptTemplateRepository templateRepository,
      ExcerptRecordRepository recordRepository,
      DocumentRenderer renderer,
      CephService datafactoryCephService,
      @Value("${datafactory-excerpt-ceph.bucket}") String bucket) {
    this.templateRepository = templateRepository;
    this.recordRepository = recordRepository;
    this.renderer = renderer;
    this.datafactoryCephService = datafactoryCephService;
    this.bucket = bucket;
  }

  public void generateExcerpt(ExcerptEventDto event) {
    try {
      var excerptTemplate = templateRepository
          .findFirstByTemplateName(event.getExcerptType())
          .orElseThrow(() -> new ExcerptProcessingException(FAILED, "Excerpt template not found"));

      var html = renderer.templateToHtml(excerptTemplate, event.getExcerptInputData());
      var pdf = renderer.htmlToPdf(html);

      save(event, pdf);
    } catch (ExcerptProcessingException e) {
      var excerptRecord = getRecordById(event.getRecordId());
      excerptRecord.setStatus(e.getStatus());
      excerptRecord.setStatusDetails(e.getDetails());

      excerptRecord.setUpdatedAt(LocalDateTime.now());
      recordRepository.save(excerptRecord);
    }
  }

  private void save(ExcerptEventDto event, byte[] bytes) {
    var cephKey = event.getExcerptType() + "-" + event.getRecordId() + ".pdf";
    var cephValue = Base64.getEncoder().encodeToString(bytes);

    try {
      datafactoryCephService.putContent(bucket, cephKey, cephValue);
    } catch (Exception e) {
      throw new ExcerptProcessingException(FAILED, e.getMessage());
    }

    var excerptRecord = getRecordById(event.getRecordId());
    excerptRecord.setStatus(COMPLETED);
    excerptRecord.setExcerptKey(cephKey);
    excerptRecord.setChecksum(DigestUtils.sha256Hex(cephValue));

    excerptRecord.setUpdatedAt(LocalDateTime.now());
    recordRepository.save(excerptRecord);
  }

  private ExcerptRecord getRecordById(UUID id) {
    return recordRepository.findById(id).
        orElseThrow(() -> new ExcerptProcessingException(FAILED, "Record not found"));
  }
}
