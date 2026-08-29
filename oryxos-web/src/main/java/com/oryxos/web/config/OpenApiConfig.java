package com.oryxos.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 / Swagger UI 配置类.
 *
 * @author OryxOS Team
 */
@Configuration
public class OpenApiConfig {

  /**
   * 配置全局 OpenAPI 元数据.
   *
   * @return OpenAPI 配置实例
   */
  @Bean
  public OpenAPI customOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("OryxOS REST API")
                .version("0.1.0-SNAPSHOT")
                .description("OryxOS Distributed AI Agent OS API Documentation")
                .contact(new Contact().name("OryxOS Team").url("https://github.com/oryxos/oryxos"))
                .license(
                    new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
  }
}
