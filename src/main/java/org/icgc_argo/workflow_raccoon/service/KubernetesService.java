/*
 * Copyright (c) 2021 The Ontario Institute for Cancer Research. All rights reserved
 *
 * This program and the accompanying materials are made available under the terms of he GNU Affero General Public License v3.0.
 * You should have received a copy of the GNU Affero General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.icgc_argo.workflow_raccoon.service;

import static java.time.ZonedDateTime.parse;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.icgc_argo.workflow_raccoon.model.WesStates.*;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc_argo.workflow_raccoon.model.WesStates;
import org.icgc_argo.workflow_raccoon.model.kubernetes.ConfigMap;
import org.icgc_argo.workflow_raccoon.model.kubernetes.RunPod;
import org.icgc_argo.workflow_raccoon.model.kubernetes.Summary;
import org.icgc_argo.workflow_raccoon.properties.KubernetesProperties;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KubernetesService {
  private static final String RUNNING = "RUNNING";
  private static final String SUCCEEDED = "SUCCEEDED";
  private static final String FAILED = "FAILED";

  private static final String WORKFLOW_PARENT_POD_PREFIX = "wes-";
  private static final String WORKFLOW_CHILD_POD_PREFIX = "nf-";
  private static final String WORKFLOW_CONFIGMAP_PREFIX = "nf-config-";

  private final KubernetesProperties properties;
  private final DefaultKubernetesClient client;


  public KubernetesService(KubernetesProperties properties) {
    this.properties = properties;
    this.client = createKubernetesClient(properties);
  }

  public Summary getSummary() {
    return Summary.builder().runPods(getCurrentRunPods()).configMaps(getCurrentRunConfigMaps()).build();
  }

  public Boolean deletePod(String podName) {
    return client.pods().inNamespace(properties.getRunsNamespace()).withName(podName).delete();
  }

  public Boolean deleteConfigMap(String configMapName) {
    return client.configMaps().inNamespace(properties.getRunsNamespace()).withName(configMapName).delete();
  }

  private List<RunPod> getCurrentRunPods() {
    return client.pods().list().getItems().stream()
        .filter(pod -> pod.getMetadata().getName().startsWith(WORKFLOW_PARENT_POD_PREFIX) ||
                       pod.getMetadata().getName().startsWith(WORKFLOW_CHILD_POD_PREFIX))
        .map(
            pod ->
                RunPod.builder()
                    .runId(pod.getMetadata().getName())
                    .state(getRunExecutorState(pod))
                    .age(parse(pod.getStatus().getStartTime()))
                    .log(getPodLog(pod.getMetadata().getName()))
                    .build())
        .collect(toUnmodifiableList());
  }

  private String getPodLog(String podName) {
    return client.pods().inNamespace(properties.getRunsNamespace()).withName(podName).getLog();
  }

  private List<ConfigMap> getCurrentRunConfigMaps() {
    return client.configMaps().list().getItems().stream()
            .filter(configMap -> configMap.getMetadata().getName().startsWith(WORKFLOW_CONFIGMAP_PREFIX))
            .map(configMap -> ConfigMap.builder()
                    .name(configMap.getMetadata().getName())
                    .age(parse(configMap.getMetadata().getCreationTimestamp()))
                    .build())
            .collect(toUnmodifiableList());
  }


  private WesStates getRunExecutorState(Pod pod) {
    if (pod.getStatus().getPhase().equalsIgnoreCase(RUNNING)) {
      return WesStates.RUNNING;
    } else if (pod.getStatus().getPhase().equalsIgnoreCase(FAILED)) {
      return EXECUTOR_ERROR;
    } else if (pod.getStatus().getPhase().equalsIgnoreCase(SUCCEEDED)) {
      return COMPLETE;
    }
    return SYSTEM_ERROR;
  }

  private DefaultKubernetesClient createKubernetesClient(KubernetesProperties properties) {
    log.info("Init k8s client");
    try {
      val config =
          new ConfigBuilder()
              .withMasterUrl(properties.getMasterUrl())
              .withNamespace(properties.getRunsNamespace())
              .withTrustCerts(properties.getTrustCertificate())
              .build();
      return new DefaultKubernetesClient(config);
    } catch (KubernetesClientException e) {
      log.info("Failed to init k8s client");
      throw new RuntimeException(e.getLocalizedMessage());
    }
  }
}
