package com.epam.digital.data.platform.excerpt.worker.service;

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.FAILED;

import com.epam.digital.data.platform.excerpt.dao.ExcerptTemplate;
import com.epam.digital.data.platform.excerpt.worker.exception.ExcerptProcessingException;
import com.itextpdf.html2pdf.HtmlConverter;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DocumentRenderer {

  private final Logger log = LoggerFactory.getLogger(DocumentRenderer.class);
  private final Configuration freemarker;

  public DocumentRenderer(Configuration freemarker) {
    this.freemarker = freemarker;
  }

  public String templateToHtml(ExcerptTemplate excerptTemplate, Object jsonData) {
    String generatingHtmlErrorMsg = "Generating HTML from template failed.Data: {}";
    try (var htmlReport = new StringWriter()) {
      var template = new Template(excerptTemplate.getTemplateName(),
          excerptTemplate.getTemplate(), freemarker);
      template.process(jsonData, htmlReport);
      return htmlReport.toString();
    } catch (TemplateException e) {
      log.error("Template ot html conversion exception", e);
      log.debug(generatingHtmlErrorMsg, jsonData);
      throw new ExcerptProcessingException(FAILED, "Template to HTML conversion fails");
    } catch (IOException e) {
      log.error("Template ot html conversion IOException", e);
      log.debug(generatingHtmlErrorMsg, jsonData);
      throw new ExcerptProcessingException(FAILED,
          "IOException occurred while converting template to HTML");
    } catch (Exception e) {
      log.error("Template ot html conversion Exception", e);
      log.debug(generatingHtmlErrorMsg, jsonData);
      throw new ExcerptProcessingException(FAILED, "Template to HTML conversion fails");
    }
  }

  public byte[] htmlToPdf(String html) {
    try (var result = new ByteArrayOutputStream()) {
      HtmlConverter.convertToPdf(html, result);
      return result.toByteArray();
    } catch (Exception e) {
      log.error("Html to pdf conversion exception", e);
      log.debug("Generating PDF from HTML failed. HTML document: {}", html);
      throw new ExcerptProcessingException(FAILED, "HTML to PDF conversion fails");
    }
  }
}
