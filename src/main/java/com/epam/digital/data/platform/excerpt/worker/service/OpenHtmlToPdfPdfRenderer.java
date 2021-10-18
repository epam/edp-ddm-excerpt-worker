package com.epam.digital.data.platform.excerpt.worker.service;

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.FAILED;

import com.epam.digital.data.platform.excerpt.worker.exception.ExcerptProcessingException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

@Component
public class OpenHtmlToPdfPdfRenderer implements PdfRenderer {

  private final Logger log = LoggerFactory.getLogger(OpenHtmlToPdfPdfRenderer.class);

  @Override
  public byte[] render(String html) {
    try (var result = new ByteArrayOutputStream()) {
      var font = ResourceUtils.getFile("classpath:fonts/Helvetica.ttf");

      new PdfRendererBuilder().toStream(result)
          .useFont(font, "Helvetica")
          .withHtmlContent(html, "/")
          .run();

      return result.toByteArray();
    } catch (Exception e) {
      log.error("Html to pdf conversion exception", e);
      throw new ExcerptProcessingException(FAILED, "HTML to PDF conversion fails");
    }
  }
}
