package com.epam.digital.data.platform.excerpt.worker.service;

public interface PdfRenderer {

  byte[] render(String html);
}
