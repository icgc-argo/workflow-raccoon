package org.icgc_argo.workflow_raccoon.properties;

import lombok.Data;

@Data
public class KubernetesClientDetails {
  String name;
  String runsNamespace;
  String masterUrl;
  Boolean trustCertificate;
  String context;
}
