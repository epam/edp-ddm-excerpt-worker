package com.epam.digital.data.platform.excerpt.worker.config;

import com.epam.digital.data.platform.excerpt.worker.config.properties.KafkaProperties;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
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
}
