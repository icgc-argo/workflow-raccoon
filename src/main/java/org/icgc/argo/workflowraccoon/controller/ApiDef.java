package org.icgc.argo.workflowraccoon.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.icgc.argo.workflowraccoon.model.ApiResponse;
import org.icgc.argo.workflowraccoon.model.DryRunResponse;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

import static org.icgc.argo.workflowraccoon.configs.SwaggerConfig.RUN_TAG_NAME;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Api(tags = RUN_TAG_NAME)
public interface ApiDef {

    @ApiOperation(
            value = "Trigger Garbage Collection Asynchronously",
            notes = "Runs garbage collection asynchronous which will cause the cleanup of stuck workflows and kubernetes resources.",
            response = ApiResponse.class
    )
    @PostMapping(path = "/run", produces = APPLICATION_JSON_VALUE)
    Mono<ApiResponse> run();

    @ApiOperation(
            value = "Trigger Garbage Collection Dry Run Synchronously",
            notes = "Does a dry-run of garbage collection synchronously reporting what it would change.",
            response = ApiResponse.class
    )
    @PostMapping(path = "/dry-run", produces = APPLICATION_JSON_VALUE)
    Mono<DryRunResponse> dryRun();
}
