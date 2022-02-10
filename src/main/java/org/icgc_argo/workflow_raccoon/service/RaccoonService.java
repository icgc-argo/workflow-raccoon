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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.icgc_argo.workflow_raccoon.model.RunStateUpdateDto;
import org.icgc_argo.workflow_raccoon.model.WesStates;
import org.icgc_argo.workflow_raccoon.model.api.DryRunResponse;
import org.icgc_argo.workflow_raccoon.model.kubernetes.ConfigMap;
import org.icgc_argo.workflow_raccoon.model.kubernetes.RunPod;
import org.icgc_argo.workflow_raccoon.model.rdpc.Run;
import org.icgc_argo.workflow_raccoon.properties.RaccoonProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RaccoonService {
  private final RaccoonProperties properties;
  private final KubernetesService kubernetesService;
  private final RdpcService rdpcService;

  public Mono<DryRunResponse> dryRun() {
    val kubeSummary = kubernetesService.getSummary();
    val podRotation = new Date(); // TODO use properties
    val podsToRemove =
        resourceCleanupStream(kubeSummary.getRunPods(), RunPod::getAge, podRotation).count();
    val configMapToRemove =
        resourceCleanupStream(kubeSummary.getConfigMaps(), ConfigMap::getAge, podRotation).count();
    return runsToUpdate(rdpcService.getAlLActiveRuns(), kubeSummary.getRunPods())
        .collectList()
        .map(
            runStateUpdateDtos ->
                DryRunResponse.builder()
                    .numJobsStuck(runStateUpdateDtos.size())
                    .numPodsToCleanup(podsToRemove)
                    .numSecretsToCleanup(configMapToRemove)
                    .build());
  }

  public Flux<RunStateUpdateDto> runsToUpdate(Flux<Run> activeRuns, List<RunPod> allRunPods) {
    val kubeRunsLookUp = new HashMap<String, RunPod>();
    allRunPods.forEach(
        kubeRun -> {
          if (kubeRunsLookUp.containsKey(kubeRun.getRunId())) {
            throw new Error("Found two kubernetes runs with same id! Shouldn't be possible!");
          }
          kubeRunsLookUp.put(kubeRun.getRunId(), kubeRun);
        });

    return activeRuns.flatMap(
        runningRun -> {
          RunStateUpdateDto dto = null;
          if (!kubeRunsLookUp.containsKey(runningRun.getRunId())) {
            dto =
                RunStateUpdateDto.builder()
                    .runId(runningRun.getRunId())
                    .currentState(runningRun.getState())
                    .newState(WesStates.SYSTEM_ERROR)
                    .logs("")
                    .build();
          }
          val kubeRun = kubeRunsLookUp.get(runningRun.getRunId());
          if (!kubeRun.getState().equals(runningRun.getState())) {
            dto =
                RunStateUpdateDto.builder()
                    .runId(runningRun.getRunId())
                    .currentState(runningRun.getState())
                    .newState(kubeRun.getState())
                    .logs(kubeRun.getLogs())
                    .build();
          }
          return dto == null ? Mono.empty() : Mono.just(dto);
        });
  }

  private <T> Stream<T> resourceCleanupStream(
      List<T> allRunPods, Function<T, Date> dateFunction, Date rotationDate) {
    return allRunPods.stream().filter(resource -> dateFunction.apply(resource).before(rotationDate));
  }
}
