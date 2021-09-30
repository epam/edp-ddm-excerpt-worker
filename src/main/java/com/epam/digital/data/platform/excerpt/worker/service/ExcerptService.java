package com.epam.digital.data.platform.excerpt.worker.service;

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.COMPLETED;
import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.FAILED;

import com.epam.digital.data.platform.dso.api.dto.SignFileRequestDto;
import com.epam.digital.data.platform.dso.api.dto.SignFileResponseDto;
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
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExcerptService {

  private final Logger log = LoggerFactory.getLogger(ExcerptService.class);

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

      log.info("Generating HTML");
      var html = renderer.templateToHtml(excerptTemplate, event.getExcerptInputData());

      log.info("Generating PDF");
      var pdf = renderer.htmlToPdf(html);

      savePdf(event, pdf);
      log.info("Excerpt generated");
    } catch (ExcerptProcessingException e) {
      log.error("Can not generate excerpt", e);

      var excerptRecord = getRecordById(event.getRecordId());
      excerptRecord.setStatus(e.getStatus());
      excerptRecord.setStatusDetails(e.getDetails());

      excerptRecord.setUpdatedAt(LocalDateTime.now());
      recordRepository.save(excerptRecord);
    }
  }

  private void savePdf(ExcerptEventDto event, byte[] bytes) {
    var cephKey = UUID.randomUUID().toString();

    saveFileToCeph(cephKey, bytes);

    String checksum =
        event.isRequiresSystemSignature()
            ? signFileAndGetChecksum(cephKey)
            : DigestUtils.sha256Hex(bytes);

    updateExcerpt(event.getRecordId(), cephKey, checksum);
  }

  private void saveFileToCeph(String cephKey, byte[] bytes) {
    log.info("Storing Excerpt to Ceph. Key: {}", cephKey);
    try {
      datafactoryCephService.putObject(bucket, cephKey, new CephObject(bytes, Map.of()));
    } catch (Exception e) {
      throw new ExcerptProcessingException(FAILED, "Failed saving file to ceph", e);
    }
  }

  private String signFileAndGetChecksum(String cephKey) {
    log.info("Signing Excerpt. Key: {}", cephKey);
    SignFileResponseDto signExcerptResponse;
    try {
      signExcerptResponse = digitalSignatureFileRestClient.sign(new SignFileRequestDto(cephKey));
    } catch (Exception e) {
      throw new ExcerptProcessingException(FAILED, "Excerpt signing failed. Key: " + cephKey, e);
    }

    if (signExcerptResponse.isSigned()) {
      return getSignedChecksum(cephKey);
    } else {
      throw new ExcerptProcessingException(FAILED, "Excerpt signing failed. Key: " + cephKey);
    }
  }

  private String getSignedChecksum(String cephKey) {
    Optional<CephObject> cephObject;
    try {
      cephObject = datafactoryCephService.getObject(bucket, cephKey);
    } catch (Exception e) {
      throw new ExcerptProcessingException(FAILED, "Failed retrieving ceph object by key: " + cephKey, e);
    }

    var signedExcerptContent = cephObject.orElseThrow(
        () -> new ExcerptProcessingException(
            FAILED, "Signed excerpt was not found in ceph. Key: " + cephKey)).getContent();

    return DigestUtils.sha256Hex(signedExcerptContent);
  }

  private void updateExcerpt(UUID recordId, String cephKey, String checksum) {
    log.info("Updating excerpt record. RecordId: {}. CephKey: {}. Checksum: {}",
        recordId, cephKey, checksum);
    var excerptRecord = getRecordById(recordId);
    excerptRecord.setStatus(COMPLETED);
    excerptRecord.setExcerptKey(cephKey);
    excerptRecord.setChecksum(checksum);

    excerptRecord.setUpdatedAt(LocalDateTime.now());
    recordRepository.save(excerptRecord);
    log.info("Excerpt record updated");
  }

  private ExcerptRecord getRecordById(UUID id) {
    return recordRepository.findById(id).
        orElseThrow(() -> new ExcerptProcessingException(FAILED, "Record not found. Id: " + id));
  }
}
