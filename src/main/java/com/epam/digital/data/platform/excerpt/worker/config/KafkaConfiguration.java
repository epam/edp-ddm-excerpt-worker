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

package com.epam.digital.data.platform.excerpt.worker.config;

import com.epam.digital.data.platform.excerpt.worker.config.properties.KafkaProperties;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.LoggingErrorHandler;
import org.springframework.kafka.listener.SeekToCurrentErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfiguration {

  private static final String CERTIFICATES_TYPE = "PEM";
  private static final String SECURITY_PROTOCOL = "SSL";

  private final KafkaProperties kafkaProperties;

  public KafkaConfiguration(KafkaProperties kafkaProperties) {
    this.kafkaProperties = kafkaProperties;
  }

  @Bean
  @Primary
  public <I> ProducerFactory<String, I> requestProducerFactory() {
    return new DefaultKafkaProducerFactory<>(producerConfigs(), keySerializer(), valueSerializer());
  }

  @Bean
  public Map<String, Object> consumerConfigs() {
    Map<String, Object> props = new HashMap<>();

    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrap());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getGroupId());
    props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
    props.put(JsonDeserializer.TRUSTED_PACKAGES,
        String.join(",", kafkaProperties.getTrustedPackages()));

    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
    props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
    props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

    if (kafkaProperties.getSsl().isEnabled()) {
      props.putAll(createSslProperties());
    }
    return props;
  }

  @Bean
  public <O> ConsumerFactory<String, O> replyConsumerFactory() {
    return new DefaultKafkaConsumerFactory<>(consumerConfigs());
  }

  @Bean
  public <O>
  ConcurrentKafkaListenerContainerFactory<String, O> concurrentKafkaListenerContainerFactory(
      ConsumerFactory<String, O> cf) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, O>();
    factory.setConsumerFactory(cf);
    return factory;
  }

  @Bean
  public Map<String, Object> producerConfigs() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrap());
    if (kafkaProperties.getSsl().isEnabled()) {
      props.putAll(createSslProperties());
    }
    return props;
  }

  @Bean
  public Serializer<String> keySerializer() {
    return new StringSerializer();
  }

  @Bean
  public <I> Serializer<I> valueSerializer() {
    return new JsonSerializer<>();
  }

  @Bean
  public <O> KafkaTemplate<String, O> errorResponseKafkaTemplate(ProducerFactory<String, O> pf,
      ConcurrentKafkaListenerContainerFactory<String, O> factory) {
    KafkaTemplate<String, O> kafkaTemplate = new KafkaTemplate<>(pf);
    factory.getContainerProperties().setMissingTopicsFatal(false);
    factory.setReplyTemplate(kafkaTemplate);
    factory.setErrorHandler(deadLetterErrorHandler(kafkaTemplate));
    return kafkaTemplate;
  }

  @Bean
  public LoggingErrorHandler errorHandler() {
    return new LoggingErrorHandler();
  }

  private SeekToCurrentErrorHandler deadLetterErrorHandler(
      KafkaOperations<String, ?> kafkaTemplate) {
    var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
    var errorHandler = kafkaProperties.getErrorHandler();
    var backOff = new ExponentialBackOff(errorHandler.getInitialInterval(),
        errorHandler.getMultiplier());
    backOff.setMaxElapsedTime(errorHandler.getMaxElapsedeTime());
    return new SeekToCurrentErrorHandler(recoverer, backOff);
  }

  private Map<String, Object> createSslProperties() {
    return Map.of(
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SECURITY_PROTOCOL,
            SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, CERTIFICATES_TYPE,
            SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, CERTIFICATES_TYPE,
            SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG, kafkaProperties.getSsl().getTruststoreCertificate(),
            SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG, kafkaProperties.getSsl().getKeystoreCertificate(),
            SslConfigs.SSL_KEYSTORE_KEY_CONFIG, kafkaProperties.getSsl().getKeystoreKey()
    );
  }
}
