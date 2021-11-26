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

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.FAILED;

import com.epam.digital.data.platform.excerpt.worker.exception.ExcerptProcessingException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OpenHtmlToPdfPdfRenderer implements PdfRenderer {

  private final Logger log = LoggerFactory.getLogger(OpenHtmlToPdfPdfRenderer.class);

  @Override
  public byte[] render(String html) {
    try (var result = new ByteArrayOutputStream()) {
      var font = this.getClass().getResourceAsStream("/fonts/Roboto.ttf");

      new PdfRendererBuilder().toStream(result)
          .useFont(() -> font, "Roboto")
          .withHtmlContent(html, "/")
          .run();

      return result.toByteArray();
    } catch (Exception e) {
      log.error("Html to pdf conversion exception", e);
      throw new ExcerptProcessingException(FAILED, "HTML to PDF conversion fails");
    }
  }
}
