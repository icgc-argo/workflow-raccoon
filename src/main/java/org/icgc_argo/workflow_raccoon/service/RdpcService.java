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

import static org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS;

import java.util.Map;
import lombok.NonNull;
import lombok.val;
import org.icgc_argo.workflow_raccoon.model.WesStates;
import org.icgc_argo.workflow_raccoon.model.rdpc.GqlRunsResponse;
import org.icgc_argo.workflow_raccoon.model.rdpc.Run;
import org.icgc_argo.workflow_raccoon.properties.RdpcProperties;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class RdpcService {
  private static final Integer DEFAULT_SIZE = 20;
  private static final String RESOURCE_ID_HEADER = "X-Resource-ID";
  private static final String OUATH_RESOURCE_ID = "rdpcOauth";

  private final WebClient webClient;

  public RdpcService(RdpcProperties properties) {
    val oauthFilter =
        createOauthFilter(
            properties.getTokenUrl(), properties.getClientId(), properties.getClientSecret());

    webClient =
        WebClient.builder()
            .baseUrl(properties.getUrl())
            .filter(oauthFilter)
            .defaultHeader(RESOURCE_ID_HEADER, OUATH_RESOURCE_ID)
            .build();
  }

  public Flux<Run> getAlLActiveRuns() {
    return getAllRunsWithState(WesStates.RUNNING);
  }

  public Flux<Run> getAllRunsWithState(@NonNull WesStates state) {
    return getActiveRunsInPage(0, state)
        .expand(
            tuple2 -> {
              val currentPageNum = tuple2.getT1();
              val gqlRunsRes = tuple2.getT2();
              if (gqlRunsRes.getData().getRuns().getInfo().getHasNextFrom()) {
                return getActiveRunsInPage(currentPageNum + 1, state);
              }
              return Mono.empty();
            })
        .flatMapIterable(tuple2 -> tuple2.getT2().getData().getRuns().getContent());
  }

  private Mono<Tuple2<Integer, GqlRunsResponse>> getActiveRunsInPage(
      Integer page, WesStates state) {
    return getActiveRunsFrom(page * DEFAULT_SIZE, DEFAULT_SIZE, state)
        .map(gqlRunsResponse -> Tuples.of(page, gqlRunsResponse));
  }

  private Mono<GqlRunsResponse> getActiveRunsFrom(Integer from, Integer size, WesStates state) {
    val body = createBody(from, size, state);
    return webClient
        .post()
        .uri("")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(GqlRunsResponse.class)
        .log("RdpcService");
  }

  private Map<String, Object> createBody(Integer from, Integer size, WesStates state) {
    val QUERY =
        "query ($from: Int!, $size: Int!, $state:String!) {\n"
            + "  runs(filter: {state: $state}, sorts: {fieldName: startTime, order: asc}, page: {from: $from, size: $size}) {\n"
            + "    info {\n"
            + "      hasNextFrom\n"
            + "    }\n"
            + "    content {\n"
            + "      runId\n"
            + "      state\n"
            + "      startTime\n"
            + "    }\n"
            + "  }\n"
            + "}\n";
    val variables = Map.of("from", from, "size", size, "state", state.getValue());

    return Map.of("query", QUERY, "variables", variables);
  }

  private ExchangeFilterFunction createOauthFilter(
      String tokenUrl, String clientId, String clientSecret) {
    // create client registration with Id for lookup by filter when needed
    val registration =
        ClientRegistration.withRegistrationId(OUATH_RESOURCE_ID)
            .tokenUri(tokenUrl)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .authorizationGrantType(CLIENT_CREDENTIALS)
            .build();
    val repo = new InMemoryReactiveClientRegistrationRepository(registration);

    // create new client manager to isolate from server oauth2 manager
    // more info: https://github.com/spring-projects/spring-security/issues/7984
    val authorizedClientService = new InMemoryReactiveOAuth2AuthorizedClientService(repo);
    val authorizedClientManager =
        new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
            repo, authorizedClientService);
    authorizedClientManager.setAuthorizedClientProvider(
        new ClientCredentialsReactiveOAuth2AuthorizedClientProvider());

    // create filter function
    val oauth = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
    oauth.setDefaultClientRegistrationId(OUATH_RESOURCE_ID);
    return oauth;
  }
}
