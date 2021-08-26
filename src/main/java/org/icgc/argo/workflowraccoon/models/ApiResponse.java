package org.icgc.argo.workflowraccoon.models;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ApiResponse {
  Integer code;
  String message;
}
