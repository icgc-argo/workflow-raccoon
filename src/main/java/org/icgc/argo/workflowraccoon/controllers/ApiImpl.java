package org.icgc.argo.workflowraccoon.controllers;

import org.icgc.argo.workflowraccoon.models.ApiResponse;
import org.icgc.argo.workflowraccoon.models.DryRunResponse;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class ApiImpl implements ApiDef {
  @Override
  public Mono<ApiResponse> run() {
    return Mono.just(ApiResponse.builder().code(200).message("I don't do anything yet.").build());
  }

  @Override
  public Mono<DryRunResponse> dryRun() {
    return Mono.just(
        DryRunResponse.builder()
            .numJobsStuck(0)
            .numPodsToCleanup(9)
            .numSecretsToCleanup(0)
            .build());
  }
}
