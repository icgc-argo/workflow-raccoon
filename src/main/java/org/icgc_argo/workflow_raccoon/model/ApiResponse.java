package org.icgc_argo.workflow_raccoon.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ApiResponse {
  Integer code;
  String message;
}
