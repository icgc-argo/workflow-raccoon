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
 *
 */

package org.icgc_argo.workflow_raccoon.controller;

import static org.icgc_argo.workflow_raccoon.configs.SwaggerConfig.RUN_TAG_NAME;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.icgc_argo.workflow_raccoon.model.api.ApiResponse;
import org.icgc_argo.workflow_raccoon.model.api.DryRunResponse;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

@Api(tags = RUN_TAG_NAME)
public interface ApiDef {

  @ApiOperation(
      value = "Trigger Garbage Collection Asynchronously",
      notes =
          "Runs garbage collection asynchronous which will cause the cleanup of stuck workflows and kubernetes resources.",
      response = ApiResponse.class)
  @PostMapping(path = "/run", produces = APPLICATION_JSON_VALUE)
  Mono<ApiResponse> run();

  @ApiOperation(
      value = "Trigger Garbage Collection Dry Run Synchronously",
      notes = "Does a dry-run of garbage collection synchronously reporting what it would change.",
      response = ApiResponse.class)
  @PostMapping(path = "/dry-run", produces = APPLICATION_JSON_VALUE)
  Mono<DryRunResponse> dryRun();
}
