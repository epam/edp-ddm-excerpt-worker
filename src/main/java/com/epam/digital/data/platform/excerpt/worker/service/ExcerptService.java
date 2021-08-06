package com.epam.digital.data.platform.excerpt.worker.service;

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.COMPLETED;
import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.FAILED;

import com.epam.digital.data.platform.dso.api.dto.SignFileRequestDto;
import com.epam.digital.data.platform.dso.client.DigitalSignatureFileRestClient;
import com.epam.digital.data.platform.excerpt.dao.ExcerptRecord;
import com.epam.digital.data.platform.excerpt.model.ExcerptEventDto;
import com.epam.digital.data.platform.excerpt.worker.exception.ExcerptProcessingException;
import com.epam.digital.data.platform.excerpt.worker.repository.ExcerptRecordRepository;
import com.epam.digital.data.platform.excerpt.worker.repository.ExcerptTemplateRepository;
import com.epam.digital.data.platform.integration.ceph.dto.CephObject;
import com.epam.digital.data.platform.integration.ceph.service.CephService;
import java.time.LocalDateTime;
import java.util.Map;
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
  private final DigitalSignatureFileRestClient digitalSignatureFileRestClient;
  private final String bucket;

  public ExcerptService(
      ExcerptTemplateRepository templateRepository,
      ExcerptRecordRepository recordRepository,
      DocumentRenderer renderer,
      CephService datafactoryCephService,
      DigitalSignatureFileRestClient digitalSignatureFileRestClient,
      @Value("${datafactory-excerpt-ceph.bucket}") String bucket) {
    this.templateRepository = templateRepository;
    this.recordRepository = recordRepository;
    this.renderer = renderer;
    this.datafactoryCephService = datafactoryCephService;
    this.digitalSignatureFileRestClient = digitalSignatureFileRestClient;
    this.bucket = bucket;
  }

  public void generateExcerpt(ExcerptEventDto event) {
    try {
      var excerptTemplate = templateRepository
          .findFirstByTemplateName(event.getExcerptType())
          .orElseThrow(() -> new ExcerptProcessingException(FAILED, "Excerpt template not found"));

      var html = renderer.templateToHtml(excerptTemplate, event.getExcerptInputData());
      var pdf = renderer.htmlToPdf(html);

      savePdf(event, pdf);
    } catch (ExcerptProcessingException e) {
      var excerptRecord = getRecordById(event.getRecordId());
      excerptRecord.setStatus(e.getStatus());
      excerptRecord.setStatusDetails(e.getDetails());

      excerptRecord.setUpdatedAt(LocalDateTime.now());
      recordRepository.save(excerptRecord);
    }
  }

  private void savePdf(ExcerptEventDto event, byte[] bytes) {
    var cephKey = UUID.randomUUID().toString();
    String checksum;
    try {
      datafactoryCephService.putObject(bucket, cephKey, new CephObject(bytes, Map.of()));
      if (event.isRequiresSystemSignature()) {
        var signExcerptResponse =
                digitalSignatureFileRestClient.sign(new SignFileRequestDto(cephKey));
        if (signExcerptResponse.isSigned()) {
          checksum = getSignedChecksum(cephKey);
        } else {
          throw new ExcerptProcessingException(FAILED, "Excerpt signage failed");
        }
      } else {
        checksum = DigestUtils.sha256Hex(bytes);
      }
    } catch (ExcerptProcessingException e) {
      throw e;
    } catch (Exception e) {
      throw new ExcerptProcessingException(FAILED, e.getMessage());
    }
    updateExcerpt(event.getRecordId(), cephKey, checksum);
  }

  private String getSignedChecksum(String cephKey) {
    var signedExcerptContent =
        datafactoryCephService
            .getObject(bucket, cephKey)
            .orElseThrow(
                () ->
                    new ExcerptProcessingException(FAILED, "Signed excerpt was not found in ceph"))
            .getContent();
    return DigestUtils.sha256Hex(signedExcerptContent);
  }

  private void updateExcerpt(UUID recordId, String cephKey, String checksum) {
    var excerptRecord = getRecordById(recordId);
    excerptRecord.setStatus(COMPLETED);
    excerptRecord.setExcerptKey(cephKey);
    excerptRecord.setChecksum(checksum);

    excerptRecord.setUpdatedAt(LocalDateTime.now());
    recordRepository.save(excerptRecord);
  }

  private ExcerptRecord getRecordById(UUID id) {
    return recordRepository.findById(id).
        orElseThrow(() -> new ExcerptProcessingException(FAILED, "Record not found"));
  }
}
