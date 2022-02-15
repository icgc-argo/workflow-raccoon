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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.icgc_argo.workflow_raccoon.model.weblog.RunStateUpdate;
import org.icgc_argo.workflow_raccoon.model.weblog.WfMgmtEvent;
import org.icgc_argo.workflow_raccoon.properties.WeblogProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.ZonedDateTime;
import java.util.Objects;

import static org.icgc_argo.workflow_raccoon.utils.JacksonUtils.toJsonString;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeblogService {
    private final WeblogProperties properties;

    public Mono<Boolean> updateRunViaWeblog(RunStateUpdate dto) {
        val event =
                WfMgmtEvent.builder()
                        .runId(dto.getRunId())
                        .event(dto.getNewState().getValue())
                        .utcTime(String.valueOf(ZonedDateTime.now().toEpochSecond()))
                        .build();

       return sendHttpMessage(event);
    }

    private Mono<Boolean> sendHttpMessage(WfMgmtEvent wfMgmtEvent) {
       val jsonStr =  toJsonString(wfMgmtEvent);
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
                                log.info("*** Failed to send event to weblog! ***");
                                return Mono.error(new Exception("Failed to send event to weblog!"));
                            }
                            log.debug("Message sent to weblog: " + jsonStr);
                            return Mono.just(res.getBody());
                        });
    }
}
