package com.oryxos.storage.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * OryxOS Storage 自动装配配置类.
 *
 * @author oryxos
 */
@AutoConfiguration
@EntityScan(basePackages = "com.oryxos.storage.entity")
@EnableJpaRepositories(basePackages = "com.oryxos.storage.repository")
public class StorageAutoConfiguration {}
