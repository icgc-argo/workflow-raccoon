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

import static java.time.OffsetDateTime.now;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc_argo.workflow_raccoon.model.MealPlan;
import org.icgc_argo.workflow_raccoon.model.RunUpdate;
import org.icgc_argo.workflow_raccoon.model.RunUpdatesRequest;
import org.icgc_argo.workflow_raccoon.model.WesStates;
import org.icgc_argo.workflow_raccoon.model.kubernetes.ConfigMap;
import org.icgc_argo.workflow_raccoon.model.kubernetes.RunPod;
import org.icgc_argo.workflow_raccoon.model.rdpc.Run;
import org.icgc_argo.workflow_raccoon.properties.RaccoonProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaccoonService {
  private final RaccoonProperties properties;
  private final KubernetesService kubernetesService;
  private final RdpcGatewayService rdpcGatewayService;
  private final RelayWeblogService relayWeblogService;

  @PostConstruct
  public void postConstruct() {
    log.info("RaccoonService is ready");
  }

  public Mono<Boolean> prepareAndExecuteMealPlan(RunUpdatesRequest req) {
    return prepareMealPlan(req)
        .doOnNext(mp -> log.info("Meal plan to execute: {}", mp))
        .flatMap(this::executeMealPlan)
        .log("RaccoonService");
  }

  public Mono<MealPlan> prepareMealPlan(RunUpdatesRequest req) {
    /*val allRunPods = kubernetesService.getCurrentRunPods();
    val configMaps = kubernetesService.getCurrentRunConfigMaps();*/

    val allRunPods = kubernetesService.getAllCurrentRunPods();
    val configMaps = kubernetesService.getAllCurrentRunConfigMaps();

    val staleRunPods = toCleanup(allRunPods, RunPod::getAge, properties.getPodRotationDays());
    val staleConfigMaps =
        toCleanup(configMaps, ConfigMap::getAge, properties.getConfigMapRotationDays());

    return createActiveToInactiveRunUpdates(rdpcGatewayService.getAlLActiveRuns(req), allRunPods)
        .map(
            runUpdates ->
                MealPlan.builder()
                    .runUpdates(runUpdates)
                    .staleConfigMaps(staleConfigMaps)
                    .staleRunPods(staleRunPods)
                    .build());
  }

  private Mono<Boolean> executeMealPlan(MealPlan mealPlan) {
    val clientList = kubernetesService.createKubernetesClient();
    val updateRuns =
        Flux.fromIterable(mealPlan.getRunUpdates())
            .delayElements(Duration.ofSeconds(properties.getRelayWeblogDelaySec()))
            .concatMap(relayWeblogService::updateRunViaWeblog);

    val deleteStaleRunPods =
        Flux.fromIterable(mealPlan.getStaleRunPods())
            .delayElements(Duration.ofSeconds(properties.getKubeCleanUpDelaySec()))
            .map(kubernetesService::deleteAllPod);

    val deleteStaleConfigMaps =
        Flux.fromIterable(mealPlan.getStaleConfigMaps())
            .delayElements(Duration.ofSeconds(properties.getKubeCleanUpDelaySec()))
            .map(kubernetesService::deleteAllConfigMap);

    return Flux.concat(updateRuns, deleteStaleRunPods, deleteStaleConfigMaps)
        .count() // to make sure all elements in flux complete
        .map(count -> count == mealPlan.getOperationsCount())
        .onErrorReturn(false);
  }

  private Mono<List<RunUpdate>> createActiveToInactiveRunUpdates(
      Flux<Run> activeRdpcRuns, List<RunPod> allRunPods) {
    val kubeRunsLookUp = new HashMap<String, RunPod>();
    allRunPods.forEach(
        kubeRun -> {
          if (kubeRunsLookUp.containsKey(kubeRun.getRunId())) {
            throw new Error("Found two kubernetes runs with same id! Shouldn't be possible!");
          }
          kubeRunsLookUp.put(kubeRun.getRunId(), kubeRun);
        });

    return activeRdpcRuns
        .flatMap(
            rdpcRun -> {
              val kubeRun = kubeRunsLookUp.get(rdpcRun.getRunId());
              val builder =
                  RunUpdate.builder()
                      .runId(rdpcRun.getRunId())
                      .currentState(rdpcRun.getState())
                      .sessionId(rdpcRun.getSessionId())
                      .workflowUrl(rdpcRun.getRepository());
              if (kubeRun == null) {
                builder
                    .newState(WesStates.SYSTEM_ERROR)
                    .logs("")
                    .startTime(rdpcRun.getStartTime().orElse(OffsetDateTime.now(ZoneOffset.UTC)))
                    .completeTime(OffsetDateTime.now(ZoneOffset.UTC));
                return Mono.just(builder.build());
              } else if (!kubeRun.getState().equals(rdpcRun.getState())) {
                builder
                    .newState(kubeRun.getState())
                    .logs(kubeRun.getLog())
                    .startTime(rdpcRun.getStartTime().orElse(kubeRun.getAge()))
                    .completeTime(OffsetDateTime.now(ZoneOffset.UTC));
                return Mono.just(builder.build());
              } else {
                return Mono.empty();
              }
            })
        .collectList();
  }

  private <T> List<T> toCleanup(
      List<T> resources, Function<T, OffsetDateTime> dateGetterFunction, Integer rotationDays) {
    if (rotationDays < 0) {
      return List.of();
    }
    val rotationTime = now().minusDays(rotationDays.longValue());
    return resources.stream()
        .filter(resource -> dateGetterFunction.apply(resource).isBefore(rotationTime))
        .collect(toUnmodifiableList());
  }
}
