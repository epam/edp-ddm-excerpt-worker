package com.epam.digital.data.platform.excerpt.worker.exception;

import com.epam.digital.data.platform.excerpt.model.ExcerptProcessingStatus;

public class ExcerptProcessingException extends RuntimeException {

  private final ExcerptProcessingStatus status;
  private final String details;

  public ExcerptProcessingException(ExcerptProcessingStatus status, String details) {
    this.status = status;
    this.details = details;
  }

  public ExcerptProcessingStatus getStatus() {
    return status;
  }

  public String getDetails() {
    return details;
  }
}
