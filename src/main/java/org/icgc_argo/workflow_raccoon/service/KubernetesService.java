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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc_argo.workflow_raccoon.model.WesStates;
import org.icgc_argo.workflow_raccoon.model.kubernetes.ConfigMap;
import org.icgc_argo.workflow_raccoon.model.kubernetes.RunPod;
import org.icgc_argo.workflow_raccoon.properties.KubernetesClientDetails;
import org.icgc_argo.workflow_raccoon.properties.KubernetesProperties;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KubernetesService {
  private static final String RUNNING = "RUNNING";
  private static final String SUCCEEDED = "SUCCEEDED";
  private static final String FAILED = "FAILED";

  private static final String WORKFLOW_PARENT_POD_PREFIX = "wes-";
  private static final String WORKFLOW_CHILD_POD_PREFIX = "nf-";
  private static final String WORKFLOW_CONFIGMAP_PREFIX = "nf-config-";

  private final KubernetesProperties properties;
  //private final DefaultKubernetesClient client;

  public KubernetesService(KubernetesProperties properties) {
    this.properties = properties;
    //this.client = createKubernetesClient(properties);
    log.info("KubernetesService is ready");
  }

  public Boolean deletePod(RunPod runPod, KubernetesClientDetails clientDetails) {
    log.info("Trying to remove pod {}", runPod.getRunId());
    val deleted =
        createKubernetesClient(clientDetails)
            .pods()
            .inNamespace(clientDetails.getRunsNamespace())
            .withName(runPod.getRunId())
            .delete();
    if (deleted) {
      log.info("Deleted pod {}", runPod.getRunId());
    } else {
      log.info("Failed to delete pod {}", runPod.getRunId());
    }
    return deleted;
  }

  public Boolean deleteConfigMap(ConfigMap configMap, KubernetesClientDetails clientDetails) {
    log.info("Trying to remove config map {}", configMap.getName());

    val deleted =
        createKubernetesClient(clientDetails)
            .configMaps()
            .inNamespace(clientDetails.getRunsNamespace())
            .withName(configMap.getName())
            .delete();

    if (deleted) {
      log.info("Deleted config map {}", configMap.getName());
    } else {
      log.info("Failed to delete config map {}", configMap.getName());
    }
    return deleted;
  }


  public Boolean deleteAllPod(RunPod runPod) {
    log.info("Trying to remove pod {}", runPod.getRunId());
    val clients = createKubernetesClient();
    boolean deleted = false;
    for (val client : clients) {
      deleted =
          client
              .pods()
              .inNamespace(client.getConfiguration().getNamespace())
              .withName(runPod.getRunId())
              .delete();
      if (deleted) {
        log.info("Deleted pod {}", runPod.getRunId());
      } else {
        log.info("Failed to delete pod {}", runPod.getRunId());
      }
    }
    return deleted;
  }

  public Boolean deleteAllConfigMap(ConfigMap configMap) {
    log.info("Trying to remove config map {}", configMap.getName());
    val clients = createKubernetesClient();
    boolean deleted = false;
    for (val client : clients) {
      deleted =
          client
              .configMaps()
              .inNamespace(client.getConfiguration().getNamespace())
              .withName(configMap.getName())
              .delete();

      if (deleted) {
        log.info("Deleted config map {}", configMap.getName());
      } else {
        log.info("Failed to delete config map {}", configMap.getName());
      }
    }

    return deleted;
  }

  public List<ConfigMap> getCurrentRunConfigMaps(KubernetesClientDetails clientDetails) {
    return createKubernetesClient(clientDetails).configMaps().list().getItems().stream()
        .filter(
            configMap -> configMap.getMetadata().getName().startsWith(WORKFLOW_CONFIGMAP_PREFIX))
        .map(
            configMap ->
                ConfigMap.builder()
                    .name(configMap.getMetadata().getName())
                    .age(parse(configMap.getMetadata().getCreationTimestamp()).toOffsetDateTime())
                    .build())
        .collect(toUnmodifiableList());
  }

  public List<RunPod> getCurrentRunPods(KubernetesClientDetails clientDetails) {
    val client = createKubernetesClient(clientDetails);
    return client.pods().list().getItems().stream()
        .filter(
            pod ->
                pod.getMetadata().getName().startsWith(WORKFLOW_PARENT_POD_PREFIX)
                    || pod.getMetadata().getName().startsWith(WORKFLOW_CHILD_POD_PREFIX))
        .map(
            pod ->
                RunPod.builder()
                    .runId(pod.getMetadata().getName())
                    .state(getRunExecutorState(pod))
                    .age(parse(pod.getStatus().getStartTime()).toOffsetDateTime())
                    .log(getPodLog(pod.getMetadata().getName(),client))
                    .build())
        .collect(toUnmodifiableList());
  }

  public List<ConfigMap> getAllCurrentRunConfigMaps() {
    val clients = createKubernetesClient();
    List<ConfigMap> currentConfigMaps = new ArrayList<>();
    for (val client : clients) {
      currentConfigMaps.addAll(client.configMaps().list().getItems().stream()
          .filter(
              configMap -> configMap.getMetadata().getName().startsWith(WORKFLOW_CONFIGMAP_PREFIX))
          .map(
              configMap ->
                  ConfigMap.builder()
                      .name(configMap.getMetadata().getName())
                      .age(parse(configMap.getMetadata().getCreationTimestamp()).toOffsetDateTime())
                      .build())
          .collect(toUnmodifiableList()));

    }
    return currentConfigMaps;
  }

  public List<RunPod> getAllCurrentRunPods() {
    val clients = createKubernetesClient();
    List<RunPod> currentPods = new ArrayList<>();

    for (val client : clients) {
      currentPods.addAll(client.pods().list().getItems().stream()
          .filter(
              pod ->
                  pod.getMetadata().getName().startsWith(WORKFLOW_PARENT_POD_PREFIX)
                      || pod.getMetadata().getName().startsWith(WORKFLOW_CHILD_POD_PREFIX))
          .map(
              pod ->
                  RunPod.builder()
                      .runId(pod.getMetadata().getName())
                      .state(getRunExecutorState(pod))
                      .age(parse(pod.getStatus().getStartTime()).toOffsetDateTime())
                      .log(getPodLog(pod.getMetadata().getName(), client))
                      .build())
          .collect(toUnmodifiableList()));
    }
    return currentPods;
  }

  private String getPodLog(String podName, DefaultKubernetesClient client) {
    return client.pods().inNamespace(client.getConfiguration().getNamespace()).withName(podName).getLog();
  }

  private WesStates getRunExecutorState(Pod pod) {
    if (pod.getStatus().getPhase().equalsIgnoreCase(RUNNING)) {
      return WesStates.RUNNING;
    } else if (pod.getStatus().getPhase().equalsIgnoreCase(FAILED)) {
      return WesStates.EXECUTOR_ERROR;
    } else if (pod.getStatus().getPhase().equalsIgnoreCase(SUCCEEDED)) {
      return WesStates.COMPLETE;
    }
    return WesStates.SYSTEM_ERROR;
  }

  private DefaultKubernetesClient createKubernetesClient(KubernetesClientDetails clientDetails) {
    log.info("Init k8s client");
    try {
      val config =
          new ConfigBuilder()
              .withMasterUrl(clientDetails.getMasterUrl())
              .withNamespace(clientDetails.getRunsNamespace())
              .withTrustCerts(clientDetails.getTrustCertificate())
              .build();
      return new DefaultKubernetesClient(config);
    } catch (KubernetesClientException e) {
      log.info("Failed to init k8s client");
      throw new RuntimeException(e.getLocalizedMessage());
    }
  }


  public List<DefaultKubernetesClient> createKubernetesClient() {
    log.info("Init k8s client");
    List<DefaultKubernetesClient> k8sClients = new ArrayList<>();
    try {
      List<KubernetesClientDetails> clientDetails = properties.getClientList();

      for (val client : clientDetails) {
        val config =
            new ConfigBuilder()
                .withMasterUrl(client.getMasterUrl())
                .withNamespace(client.getRunsNamespace())
                .withTrustCerts(client.getTrustCertificate())
                .build();
        k8sClients.add(new DefaultKubernetesClient(config));

      }
    } catch (KubernetesClientException e) {
      log.info("Failed to init k8s client");
      throw new RuntimeException(e.getLocalizedMessage());
    }
    return k8sClients;
  }

}
