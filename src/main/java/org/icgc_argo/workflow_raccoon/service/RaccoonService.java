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

import static java.time.ZonedDateTime.now;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.icgc_argo.workflow_raccoon.model.MealPlan;
import org.icgc_argo.workflow_raccoon.model.WesStates;
import org.icgc_argo.workflow_raccoon.model.kubernetes.ConfigMap;
import org.icgc_argo.workflow_raccoon.model.kubernetes.RunPod;
import org.icgc_argo.workflow_raccoon.model.rdpc.Run;
import org.icgc_argo.workflow_raccoon.model.weblog.RunStateUpdate;
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
  private final WeblogService weblogService;

  public Mono<Boolean> prepareAndExecuteMealPlan() {
    return prepareMealPlan().flatMap(this::executeMealPlan);
  }

  public Mono<MealPlan> prepareMealPlan() {
    val allRunPods = kubernetesService.getCurrentRunPods();
    val configMaps = kubernetesService.getCurrentRunConfigMaps();

    val podsBefore = now().minusDays(properties.getPodRotationDays().longValue());
    val configMapBefore = now().minusDays(properties.getConfigMapRotationDays().longValue());

    val staleRunPods = toCleanup(allRunPods, RunPod::getAge, podsBefore);
    val staleConfigMaps = toCleanup(configMaps, ConfigMap::getAge, configMapBefore);

    return runsToUpdate(rdpcService.getAlLActiveRuns(), allRunPods)
        .collectList()
        .map(
            runUpdates ->
                MealPlan.builder()
                    .runUpdates(runUpdates)
                    .staleConfigMaps(staleConfigMaps)
                    .staleRunPods(staleRunPods)
                    .build());
  }

  private Mono<Boolean> executeMealPlan(MealPlan mealPlan) {
    val updateRuns =
        Flux.fromIterable(mealPlan.getRunUpdates()).concatMap(weblogService::updateRunViaWeblog);

    val deleteStaleRunPods =
        Flux.fromIterable(mealPlan.getStaleRunPods()).map(kubernetesService::deletePod);

    val deleteStaleConfigMaps =
        Flux.fromIterable(mealPlan.getStaleConfigMaps()).map(kubernetesService::deleteConfigMap);

    return Flux.concat(updateRuns, deleteStaleRunPods, deleteStaleConfigMaps)
        .count() // to make sure all elements in flux complete
        .then(Mono.just(true))
        .onErrorReturn(false);
  }

  private Flux<RunStateUpdate> runsToUpdate(Flux<Run> activeRuns, List<RunPod> allRunPods) {
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
          RunStateUpdate dto = null;
          if (!kubeRunsLookUp.containsKey(runningRun.getRunId())) {
            dto =
                RunStateUpdate.builder()
                    .runId(runningRun.getRunId())
                    .currentState(runningRun.getState())
                    .newState(WesStates.SYSTEM_ERROR)
                    .logs("")
                    .build();
          } else {
            val kubeRun = kubeRunsLookUp.get(runningRun.getRunId());
            if (!kubeRun.getState().equals(runningRun.getState())) {
              dto =
                  RunStateUpdate.builder()
                      .runId(runningRun.getRunId())
                      .currentState(runningRun.getState())
                      .newState(kubeRun.getState())
                      .logs(kubeRun.getLog())
                      .build();
            }
          }
          return dto == null ? Mono.empty() : Mono.just(dto);
        });
  }

  private <T> List<T> toCleanup(
      List<T> resources,
      Function<T, ZonedDateTime> dateGetterFunction,
      ZonedDateTime filterBeforeDate) {
    return resources.stream()
        .filter(resource -> dateGetterFunction.apply(resource).isBefore(filterBeforeDate))
        .collect(toUnmodifiableList());
  }
}
