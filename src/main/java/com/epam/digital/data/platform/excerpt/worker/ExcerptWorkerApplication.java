package com.epam.digital.data.platform.excerpt.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@EntityScan("com.epam.digital.data.platform.excerpt.dao")
@SpringBootApplication
public class ExcerptWorkerApplication {

  public static void main(String[] args) {
    SpringApplication.run(ExcerptWorkerApplication.class, args);
  }
}
