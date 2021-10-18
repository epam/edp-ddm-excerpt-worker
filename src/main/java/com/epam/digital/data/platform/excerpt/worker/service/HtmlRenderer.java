package com.epam.digital.data.platform.excerpt.worker.service;

import com.epam.digital.data.platform.excerpt.dao.ExcerptTemplate;

public interface HtmlRenderer {

  String render(ExcerptTemplate excerptTemplate, Object jsonData);
}
