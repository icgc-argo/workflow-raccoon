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

import static org.icgc_argo.workflow_raccoon.utils.JacksonUtils.toJsonString;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc_argo.workflow_raccoon.model.RunUpdate;
import org.icgc_argo.workflow_raccoon.model.WesStates;
import org.icgc_argo.workflow_raccoon.model.weblog.NextflowEvent;
import org.icgc_argo.workflow_raccoon.model.weblog.WfMgmtEvent;
import org.icgc_argo.workflow_raccoon.properties.WeblogProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelayWeblogService {
  private final WeblogProperties properties;

  @PostConstruct
  public void postConstruct() {
    log.info("RelayWeblogService is ready");
  }

  public Mono<Boolean> updateRunViaWeblog(RunUpdate runUpdate) {
    log.info(
        "Trying to update run {} from {} to {}",
        runUpdate.getRunId(),
        runUpdate.getCurrentState(),
        runUpdate.getNewState());
    Object event;
    if (runUpdate.getNewState().equals(WesStates.EXECUTOR_ERROR)) {
      // executor error events need to be sent via a NextflowEvent
      event =
          new NextflowEvent(
              runUpdate.getRunId(),
              runUpdate.getSessionId(),
              "ERROR",
              runUpdate.getStartTime(),
              runUpdate.getCompleteTime(),
              runUpdate.getLogs(),
              false,
              runUpdate.getWorkflowUrl());
    } else {
      event =
          WfMgmtEvent.builder()
              .runId(runUpdate.getRunId())
              .workflowUrl(runUpdate.getWorkflowUrl())
              .event(runUpdate.getNewState().getValue())
              .utcTime(OffsetDateTime.now(ZoneOffset.UTC))
              .build();
    }
    return sendHttpMessage(event)
        .doOnNext(
            success -> {
              if (success) {
                log.info(
                    "Message sent to weblog to update run {} from {} to {}",
                    runUpdate.getRunId(),
                    runUpdate.getCurrentState(),
                    runUpdate.getNewState());
              } else {
                log.info(
                    "Failed to send message to weblog to update run {} from {} to {}",
                    runUpdate.getRunId(),
                    runUpdate.getCurrentState(),
                    runUpdate.getNewState());
              }
            });
  }

  private Mono<Boolean> sendHttpMessage(Object obj) {
    val jsonStr = toJsonString(obj);
    return WebClient.create(properties.getUrl())
        .post()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(jsonStr)
        .retrieve()
        .toEntity(Boolean.class)
        .flatMap(
            res -> {
              // Don't want to proceed with stream if response from weblog is bad, so throw error
              if (!res.getStatusCode().is2xxSuccessful() || !Objects.equals(res.getBody(), true)) {
                log.debug("*** Failed to send event to weblog! ***");
                return Mono.error(new Exception("Failed to send event to weblog!"));
              }
              log.debug("Message sent to weblog: " + jsonStr);
              return Mono.just(res.getBody());
            });
  }
}
