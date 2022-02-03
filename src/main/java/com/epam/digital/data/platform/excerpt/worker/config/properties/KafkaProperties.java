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

package com.epam.digital.data.platform.excerpt.worker.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("data-platform.kafka")
public class KafkaProperties {

  private String bootstrap;
  private String groupId;
  private String topic;
  private ErrorHandler errorHandler = new ErrorHandler();
  private List<String> trustedPackages;
  private SslProperties ssl;

  public List<String> getTrustedPackages() {
    return trustedPackages;
  }

  public void setTrustedPackages(List<String> trustedPackages) {
    this.trustedPackages = trustedPackages;
  }

  public String getBootstrap() {
    return bootstrap;
  }

  public void setBootstrap(String bootstrap) {
    this.bootstrap = bootstrap;
  }

  public ErrorHandler getErrorHandler() {
    return errorHandler;
  }

  public void setErrorHandler(ErrorHandler errorHandler) {
    this.errorHandler = errorHandler;
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(String groupId) {
    this.groupId = groupId;
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public SslProperties getSsl() {
    return ssl;
  }

  public void setSsl(SslProperties ssl) {
    this.ssl = ssl;
  }

  public static class ErrorHandler {

    private Long initialInterval;
    private Long maxElapsedeTime;
    private Double multiplier;

    public Long getInitialInterval() {
      return initialInterval;
    }

    public void setInitialInterval(Long initialInterval) {
      this.initialInterval = initialInterval;
    }

    public Long getMaxElapsedeTime() {
      return maxElapsedeTime;
    }

    public void setMaxElapsedeTime(Long maxElapsedeTime) {
      this.maxElapsedeTime = maxElapsedeTime;
    }

    public Double getMultiplier() {
      return multiplier;
    }

    public void setMultiplier(Double multiplier) {
      this.multiplier = multiplier;
    }
  }


  public static class SslProperties {

    private boolean enabled;
    private String keystoreKey;
    private String keystoreCertificate;
    private String truststoreCertificate;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getKeystoreKey() {
      return keystoreKey;
    }

    public void setKeystoreKey(String keystoreKey) {
      this.keystoreKey = keystoreKey;
    }

    public String getKeystoreCertificate() {
      return keystoreCertificate;
    }

    public void setKeystoreCertificate(String keystoreCertificate) {
      this.keystoreCertificate = keystoreCertificate;
    }

    public String getTruststoreCertificate() {
      return truststoreCertificate;
    }

    public void setTruststoreCertificate(String truststoreCertificate) {
      this.truststoreCertificate = truststoreCertificate;
    }
  }
}
