package org.icgc_argo.workflow_raccoon.controller;

import static org.icgc_argo.workflow_raccoon.configs.SwaggerConfig.RUN_TAG_NAME;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.icgc_argo.workflow_raccoon.model.ApiResponse;
import org.icgc_argo.workflow_raccoon.model.DryRunResponse;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

@Api(tags = RUN_TAG_NAME)
public interface ApiDef {

  @ApiOperation(
      value = "Trigger Garbage Collection Asynchronously",
      notes =
          "Runs garbage collection asynchronous which will cause the cleanup of stuck workflows and kubernetes resources.",
      response = ApiResponse.class)
  @PostMapping(path = "/run", produces = APPLICATION_JSON_VALUE)
  Mono<ApiResponse> run();

  @ApiOperation(
      value = "Trigger Garbage Collection Dry Run Synchronously",
      notes = "Does a dry-run of garbage collection synchronously reporting what it would change.",
      response = ApiResponse.class)
  @PostMapping(path = "/dry-run", produces = APPLICATION_JSON_VALUE)
  Mono<DryRunResponse> dryRun();
}
