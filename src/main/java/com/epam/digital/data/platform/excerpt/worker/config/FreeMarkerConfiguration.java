package com.epam.digital.data.platform.excerpt.worker.config;

import freemarker.template.TemplateExceptionHandler;
import java.util.TimeZone;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FreeMarkerConfiguration {

  @Bean
  public freemarker.template.Configuration getConfig() {
    var configuration = new freemarker.template.Configuration(
        freemarker.template.Configuration.VERSION_2_3_30);
    configuration.setDefaultEncoding("UTF-8");
    configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    configuration.setLogTemplateExceptions(false);
    configuration.setWrapUncheckedExceptions(true);
    configuration.setFallbackOnNullLoopVariable(false);
    configuration.setLocalizedLookup(false);
    configuration.setTagSyntax(freemarker.template.Configuration.SQUARE_BRACKET_TAG_SYNTAX);
    configuration.setInterpolationSyntax(
        freemarker.template.Configuration.SQUARE_BRACKET_INTERPOLATION_SYNTAX);
    configuration.setTimeZone(TimeZone.getTimeZone("Europe/Kiev"));
    return configuration;
  }

}
