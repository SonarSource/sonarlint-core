/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2023 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package mediumtest.issues;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueStatus;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CheckIssueStatusChangePermittedMediumTests {

  private SonarLintBackendImpl backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_fail_when_the_connection_is_unknown() {
    backend = newBackend().build();

    var response = checkStatusChangePermitted("connectionId");

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(IllegalArgumentException.class)
      .withMessage("Connection with ID 'connectionId' does not exist");
  }

  @Test
  void it_should_return_2_statuses() {
    backend = newBackend()
      .withSonarQubeConnection("connectionId")
      .build();

    var response = checkStatusChangePermitted("connectionId");

    assertThat(response)
      .succeedsWithin(Duration.ofSeconds(2))
      .extracting(CheckStatusChangePermittedResponse::getAllowedStatuses)
      .asInstanceOf(InstanceOfAssertFactories.list(IssueStatus.class))
      .extracting(IssueStatus::getTitle, IssueStatus::getDescription)
      .containsExactly(
        tuple("Won't Fix", "The issue is irrelevant in this context."),
        tuple("False Positive", "The issue is not accurate."));
  }

  private CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(String connectionId) {
    return backend.getIssueService().checkStatusChangePermitted(new CheckStatusChangePermittedParams(connectionId));
  }
}
