package com.epam.digital.data.platform.excerpt.worker.service;

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.FAILED;

import com.epam.digital.data.platform.excerpt.dao.ExcerptTemplate;
import com.epam.digital.data.platform.excerpt.worker.exception.ExcerptProcessingException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.StringWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FreemarkerHtmlRenderer implements HtmlRenderer {

  private final Logger log = LoggerFactory.getLogger(FreemarkerHtmlRenderer.class);
  private final Configuration freemarker;

  public FreemarkerHtmlRenderer(Configuration freemarker) {
    this.freemarker = freemarker;
  }

  @Override
  public String render(ExcerptTemplate excerptTemplate, Object jsonData) {
    try (var htmlReport = new StringWriter()) {
      var template = new Template(excerptTemplate.getTemplateName(),
          excerptTemplate.getTemplate(), freemarker);
      template.process(jsonData, htmlReport);
      return htmlReport.toString();
    } catch (TemplateException e) {
      log.error("Template to html conversion exception", e);
      throw new ExcerptProcessingException(FAILED, "Template to HTML conversion fails");
    } catch (IOException e) {
      log.error("Template to html conversion IOException", e);
      throw new ExcerptProcessingException(FAILED,
          "IOException occurred while converting template to HTML");
    } catch (Exception e) {
      log.error("Template to html conversion Exception", e);
      throw new ExcerptProcessingException(FAILED, "Template to HTML conversion fails");
    }
  }
}
