package org.icgc.argo.workflowraccoon.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DryRunResponse {
  Integer numJobsStuck;
  Integer numPodsToCleanup;
  Integer numSecretsToCleanup;
}
