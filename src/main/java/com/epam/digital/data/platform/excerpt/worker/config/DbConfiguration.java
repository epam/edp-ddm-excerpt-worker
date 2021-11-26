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

import com.epam.digital.data.platform.excerpt.worker.config.properties.DatabaseProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DatabaseProperties.class)
public class DbConfiguration {

  @Bean
  public DataSource datasource(DatabaseProperties databaseProperties) {
    HikariConfig configuration = new HikariConfig();
    configuration.setJdbcUrl(databaseProperties.getUrl());
    configuration.setUsername(databaseProperties.getUsername());
    configuration.setPassword(databaseProperties.getPassword());
    configuration.setConnectionTimeout(databaseProperties.getConnectionTimeout());
    return new HikariDataSource(configuration);
  }
}
