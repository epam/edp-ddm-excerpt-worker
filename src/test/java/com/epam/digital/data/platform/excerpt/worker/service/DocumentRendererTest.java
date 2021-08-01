package com.epam.digital.data.platform.excerpt.worker.service;

import static com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus.FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.epam.digital.data.platform.excerpt.dao.ExcerptTemplate;
import com.epam.digital.data.platform.excerpt.worker.config.FreeMarkerConfiguration;
import com.epam.digital.data.platform.excerpt.worker.config.GenericConfig;
import com.epam.digital.data.platform.excerpt.worker.exception.ExcerptProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import({FreeMarkerConfiguration.class, GenericConfig.class})
class DocumentRendererTest {

  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private Configuration freemarker;

  private DocumentRenderer documentRenderer;

  @BeforeEach
  void init() {
    documentRenderer = new DocumentRenderer(freemarker, objectMapper);
  }

  @Test
  void shouldThrowExceptionWithHtmlToPdfConversionError() {
    var exception = assertThrows(ExcerptProcessingException.class,
        () -> documentRenderer.htmlToPdf(""));

    assertThat(exception.getStatus()).isEqualTo(FAILED);
    assertThat(exception.getDetails()).isEqualTo("HTML to PDF conversion fails");
  }

  @Test
  void templateRenderingHappyPath() {
    var excerptTemplate = new ExcerptTemplate();
    excerptTemplate.setTemplateName("Test");
    excerptTemplate.setTemplate("My name is [=name]");

    var html = documentRenderer.templateToHtml(excerptTemplate, Map.of("name", "Alex"));

    assertThat(html).isEqualTo("My name is Alex");
  }

  @Test
  void shouldThrowExceptionWithSomeTemplateToHtmlConversionError() {
    var excerptTemplate = new ExcerptTemplate();
    excerptTemplate.setTemplateName("Test");
    excerptTemplate.setTemplate("");
    var exception = assertThrows(ExcerptProcessingException.class,
        () -> documentRenderer.templateToHtml(excerptTemplate, "{}"));

    assertThat(exception.getStatus()).isEqualTo(FAILED);
  }
}