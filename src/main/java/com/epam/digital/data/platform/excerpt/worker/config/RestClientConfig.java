package com.epam.digital.data.platform.excerpt.worker.config;

import com.epam.digital.data.platform.dso.client.DigitalSignatureFileRestClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@EnableFeignClients(clients = DigitalSignatureFileRestClient.class)
@Configuration
public class RestClientConfig {
}
