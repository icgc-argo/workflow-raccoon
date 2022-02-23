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

package org.icgc_argo.workflow_raccoon.model.weblog;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.val;

@Value
public class NextflowEvent {
  @NonNull String runName; // Rdpc.Run.runId is runName
  @NonNull String runId; // Rdpc.Run.sessionId is runId
  @NonNull String event;
  @NonNull String utcTime;
  @NonNull Metadata metadata;

  public NextflowEvent(
      @NonNull String runName,
      @NonNull String runId,
      @NonNull String event,
      @NonNull OffsetDateTime completeTime,
      @NonNull String errorReport,
      @NonNull Boolean success,
      @NonNull String repository) {
    this.runName = runName;
    this.runId = runId;
    this.event = event;
    this.utcTime = completeTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    val workflow = new Workflow(errorReport, success, completeTime, repository);
    this.metadata = new Metadata(workflow, Map.of());
  }

  @Value
  @RequiredArgsConstructor
  public static class Metadata {
    @NonNull Workflow workflow;
    @NonNull Map<String, Object> parameters;
  }

  @Value
  @RequiredArgsConstructor
  public static class Workflow {
    @NonNull String errorReport;
    @NonNull Boolean success;
    @NonNull OffsetDateTime complete;
    @NonNull String repository;
  }
}
