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

import com.epam.digital.data.platform.dso.api.dto.SignFileRequestDto;
import com.epam.digital.data.platform.dso.api.dto.SignFileResponseDto;
import com.epam.digital.data.platform.dso.client.DigitalSignatureFileRestClient;
import com.epam.digital.data.platform.excerpt.dao.ExcerptRecord;
import com.epam.digital.data.platform.excerpt.model.ExcerptEventDto;
import com.epam.digital.data.platform.excerpt.worker.exception.ExcerptProcessingException;
import com.epam.digital.data.platform.excerpt.worker.repository.ExcerptRecordRepository;
import com.epam.digital.data.platform.excerpt.worker.repository.ExcerptTemplateRepository;
import com.epam.digital.data.platform.integration.ceph.model.CephObject;
import com.epam.digital.data.platform.integration.ceph.service.CephService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
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

  static final String EXCERPT_CONTENT_TYPE = "application/octet-stream";

  private final ExcerptTemplateRepository templateRepository;
  private final ExcerptRecordRepository recordRepository;
  private final HtmlRenderer htmlRenderer;
  private final PdfRenderer pdfRenderer;
  private final CephService datafactoryCephService;
  private final DigitalSignatureFileRestClient digitalSignatureFileRestClient;
  private final boolean isDigitalSignatureEnabled;
  private final String bucket;

  public ExcerptService(
      ExcerptTemplateRepository templateRepository,
      ExcerptRecordRepository recordRepository,
      HtmlRenderer htmlRenderer,
      PdfRenderer pdfRenderer,
      CephService datafactoryCephService,
      DigitalSignatureFileRestClient digitalSignatureFileRestClient,
      @Value("${data-platform.signature.enabled}") boolean isDigitalSignatureEnabled,
      @Value("${datafactory-excerpt-ceph.bucket}") String bucket) {
    this.templateRepository = templateRepository;
    this.htmlRenderer = htmlRenderer;
    this.recordRepository = recordRepository;
    this.pdfRenderer = pdfRenderer;
    this.datafactoryCephService = datafactoryCephService;
    this.digitalSignatureFileRestClient = digitalSignatureFileRestClient;
    this.isDigitalSignatureEnabled = isDigitalSignatureEnabled;
    this.bucket = bucket;
  }

  public void generateExcerpt(ExcerptEventDto event) {
    try {
      var excerptTemplate = templateRepository
          .findFirstByTemplateName(event.getExcerptType())
          .orElseThrow(() -> new ExcerptProcessingException(FAILED, "Excerpt template not found"));

      log.info("Generating HTML");
      var html = htmlRenderer.render(excerptTemplate, event.getExcerptInputData());

      log.info("Generating PDF");
      var pdf = pdfRenderer.render(html);

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

    var shouldSign = event.isRequiresSystemSignature() && isDigitalSignatureEnabled;
    String checksum = shouldSign ? signFileAndGetChecksum(cephKey) : DigestUtils.sha256Hex(bytes);

    updateExcerpt(event.getRecordId(), cephKey, checksum);
  }

  private void saveFileToCeph(String cephKey, byte[] bytes) {
    log.info("Storing Excerpt to Ceph. Key: {}", cephKey);
    try {
      datafactoryCephService.put(
          bucket, cephKey, EXCERPT_CONTENT_TYPE, Collections.emptyMap(), new ByteArrayInputStream(bytes));
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
      cephObject = datafactoryCephService.get(bucket, cephKey);
    } catch (Exception e) {
      throw new ExcerptProcessingException(FAILED,
          "Failed retrieving ceph object by key: " + cephKey, e);
    }

    var signedExcerptContentStream =
        cephObject
            .orElseThrow(
                () ->
                    new ExcerptProcessingException(
                        FAILED, "Signed excerpt was not found in ceph. Key: " + cephKey))
            .getContent();

    try {
      return DigestUtils.sha256Hex(signedExcerptContentStream);
    } catch (IOException e) {
      throw new ExcerptProcessingException(FAILED, "Failed reading excerpt content from stream", e);
    }
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
