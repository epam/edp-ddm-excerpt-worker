package com.epam.digital.data.platform.excerpt.worker.service;

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.FAILED;

import com.epam.digital.data.platform.excerpt.dao.ExcerptTemplate;
import com.epam.digital.data.platform.excerpt.worker.exception.ExcerptProcessingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.html2pdf.HtmlConverter;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import org.springframework.stereotype.Component;

@Component
public class DocumentRenderer {

  private final Configuration freemarker;
  private final ObjectMapper objectMapper;

  public DocumentRenderer(Configuration freemarker, ObjectMapper objectMapper) {
    this.freemarker = freemarker;
    this.objectMapper = objectMapper;
  }

  public String templateToHtml(ExcerptTemplate excerptTemplate, Object jsonData) {
    try (var htmlReport = new StringWriter()) {
      var template = new Template(excerptTemplate.getTemplateName(),
          excerptTemplate.getTemplate(), freemarker);
      template.process(jsonData, htmlReport);
      return htmlReport.toString();
    } catch (TemplateException e) {
      throw new ExcerptProcessingException(FAILED, "Template to HTML conversion fails");
    } catch (IOException e) {
      throw new ExcerptProcessingException(FAILED,
          "IOException occurred while converting template to HTML");
    } catch (Exception e) {
      throw new ExcerptProcessingException(FAILED, e.getMessage());
    }
  }

  public byte[] htmlToPdf(String html) {
    try (var result = new ByteArrayOutputStream()) {
      HtmlConverter.convertToPdf(html, result);
      return result.toByteArray();
    } catch (Exception e) {
      throw new ExcerptProcessingException(FAILED, "HTML to PDF conversion fails");
    }
  }
}
