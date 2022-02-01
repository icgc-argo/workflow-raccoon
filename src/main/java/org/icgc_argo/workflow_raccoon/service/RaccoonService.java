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
import org.icgc_argo.workflow_raccoon.model.DryRunResponse;
import org.icgc_argo.workflow_raccoon.service.infra.InfraService;
import org.icgc_argo.workflow_raccoon.service.infra.KubernetesService;
import org.icgc_argo.workflow_raccoon.service.rdpc.RdpcService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RaccoonService {
    private final InfraService infraService;
    private final RdpcService rdpcService;

    public Mono<DryRunResponse> dryRun() {
      return this.rdpcService.getRunningWorkflows()
              .filterWhen(infraService::isWorkflowNotRunning)
              .collectList()
              .map(runs -> DryRunResponse.builder()
                      .numJobsStuck(runs.size())
                      .numPodsToCleanup(0)
                      .numSecretsToCleanup(0)
                      .build());
    }
}
