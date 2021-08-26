package org.icgc_argo.workflow_raccoon.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Tag;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@EnableSwagger2
@Configuration
public class SwaggerConfig {
  public static final String RUN_TAG_NAME = "Run";

  @Value("${spring.application.name}")
  private String appName;

  @Value("${spring.application.description}")
  private String appDescription;

  @Value("${spring.application.version}")
  private String appVersion;

  ApiInfo apiInfo() {
    return new ApiInfoBuilder()
        .title(appName)
        .description(appDescription)
        .license("Apache 2.0")
        .version(appVersion)
        .build();
  }

  @Bean
  public Docket docket() {
    return new Docket(DocumentationType.SWAGGER_2)
        .select()
        .apis(RequestHandlerSelectors.basePackage("org.icgc_argo.workflow_raccoon"))
        .build()
        .tags(new Tag(RUN_TAG_NAME, "Run Garbage Collection"))
        .apiInfo(apiInfo());
  }
}
