package org.icgc.argo.workflowraccoon.controllers;

import org.icgc.argo.workflowraccoon.models.ApiResponse;
import org.icgc.argo.workflowraccoon.models.DryRunResponse;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

public interface ApiDef {

  @PostMapping(path = "/run")
  Mono<ApiResponse> run();

  @PostMapping(path = "/dry-run")
  Mono<DryRunResponse> dryRun();
}
