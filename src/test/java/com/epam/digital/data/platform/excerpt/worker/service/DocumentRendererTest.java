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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.epam.digital.data.platform.excerpt.dao.ExcerptTemplate;
import com.epam.digital.data.platform.excerpt.worker.config.FreeMarkerConfiguration;
import com.epam.digital.data.platform.excerpt.worker.config.GenericConfig;
import com.epam.digital.data.platform.excerpt.worker.exception.ExcerptProcessingException;
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
  private Configuration freemarker;

  private HtmlRenderer htmlRenderer;
  private PdfRenderer pdfRenderer = new OpenHtmlToPdfPdfRenderer();

  @BeforeEach
  void init() {
    htmlRenderer = new FreemarkerHtmlRenderer(freemarker);
  }

  @Test
  void shouldThrowExceptionWithHtmlToPdfConversionError() {
    var exception = assertThrows(ExcerptProcessingException.class,
        () -> pdfRenderer.render(""));

    assertThat(exception.getStatus()).isEqualTo(FAILED);
    assertThat(exception.getDetails()).isEqualTo("HTML to PDF conversion fails");
  }

  @Test
  void htmlToPdfHappyPath() {
    var bytes = pdfRenderer.render("<html><head></head><body>Hello</body></html>");

    assertThat(bytes.length).isNotNull();
  }

  @Test
  void templateRenderingHappyPath() {
    var excerptTemplate = new ExcerptTemplate();
    excerptTemplate.setTemplateName("Test");
    excerptTemplate.setTemplate("My name is [=name]");

    var html = htmlRenderer.render(excerptTemplate, Map.of("name", "Alex"));

    assertThat(html).isEqualTo("My name is Alex");
  }

  @Test
  void shouldThrowExceptionWithSomeTemplateToHtmlConversionError() {
    var excerptTemplate = new ExcerptTemplate();
    excerptTemplate.setTemplateName("Test");
    excerptTemplate.setTemplate("");
    var exception = assertThrows(ExcerptProcessingException.class,
        () -> htmlRenderer.render(excerptTemplate, "{}"));

    assertThat(exception.getStatus()).isEqualTo(FAILED);
  }

  @Test
  void shouldConvertTemplateExceptionToExcerptProcessingException() {
    var excerptTemplate = new ExcerptTemplate();
    excerptTemplate.setTemplateName("Test");
    excerptTemplate.setTemplate("My name is [=name]");

    var exception = assertThrows(ExcerptProcessingException.class,
        () -> htmlRenderer.render(excerptTemplate, Map.of(5, "Alex")));

    assertThat(exception.getStatus()).isEqualTo(FAILED);
  }
}
