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

package org.icgc_argo.workflow_raccoon.service.infra;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc_argo.workflow_raccoon.properties.KubernetesProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class KubernetesService implements InfraService {
  private static final String RUNNING = "RUNNING";
  private static final String SUCCEEDED = "SUCCEEDED";
  private static final String FAILED = "FAILED";

  private final DefaultKubernetesClient client;

  Map<String, Boolean> lookUp = Map.of("wes-123", false, "wes-456", true);

  public Mono<Boolean> isWorkflowNotRunning(String id) {
    return Mono.just(lookUp.getOrDefault(id, false));
  }

  public Boolean isPodNotRunning(String name) {
    return !isPodRunning(name);
  }

  public Boolean isPodRunning(String name) {
    val pod = client.pods().withName(name).get();
    val phase = pod.getStatus().getPhase();
    log.info("Pod {} has phase {}", name, phase);
    return phase.equalsIgnoreCase(RUNNING);
  }

  private DefaultKubernetesClient kubernetesClient(KubernetesProperties properties) {
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
