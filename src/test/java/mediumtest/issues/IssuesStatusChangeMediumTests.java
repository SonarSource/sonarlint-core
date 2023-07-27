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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.ServerFixture;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.AddIssueCommentParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.ChangeIssueStatusParams;
import org.sonarsource.sonarlint.core.clientapi.backend.issue.IssueStatus;
import org.sonarsource.sonarlint.core.issue.AddIssueCommentException;
import org.sonarsource.sonarlint.core.issue.IssueStatusChangeException;

import static mediumtest.fixtures.ServerFixture.ServerStatus.DOWN;
import static mediumtest.fixtures.ServerFixture.newSonarQubeServer;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;

class IssuesStatusChangeMediumTests {

  private SonarLintBackendImpl backend;
  private ServerFixture.Server server;

  @Test
  void it_should_update_the_status_on_sonarqube_through_the_web_api() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", "myIssueKey",
      IssueStatus.WONT_FIX, false));

    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var lastRequest = server.lastRequest();
    assertThat(lastRequest.getPath()).isEqualTo("/api/issues/do_transition");
    assertThat(lastRequest.getHeader("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
    assertThat(lastRequest.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("issue=myIssueKey&transition=wontfix");
  }

  @Test
  void it_should_fail_the_future_when_the_server_returns_an_error() {
    server = newSonarQubeServer().withStatus(DOWN).start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().changeStatus(new ChangeIssueStatusParams("configScopeId", "myIssueKey",
      IssueStatus.WONT_FIX, false));

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(IssueStatusChangeException.class)
      .withMessage("Cannot change status on the issue");
  }

  @Test
  void it_should_add_new_comment_to_issue() {
    server = newSonarQubeServer().start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams("configScopeId", "myIssueKey", "That's " +
      "serious issue"));
    assertThat(response).succeedsWithin(Duration.ofSeconds(2));
    var lastRequest = server.lastRequest();
    assertThat(lastRequest.getPath()).isEqualTo("/api/issues/add_comment");
    assertThat(lastRequest.getHeader("Content-Type")).isEqualTo("application/x-www-form-urlencoded");
    assertThat(lastRequest.getBody().readString(StandardCharsets.UTF_8)).isEqualTo("issue=myIssueKey&text=That%27s+serious+issue");
  }

  @Test
  void it_should_throw_if_server_response_is_not_OK_during_add_new_comment_to_issue() {
    server = newSonarQubeServer().withStatus(DOWN).start();
    backend = newBackend()
      .withSonarQubeConnection("connectionId", server)
      .withBoundConfigScope("configScopeId", "connectionId", "projectKey")
      .build();

    var response = backend.getIssueService().addComment(new AddIssueCommentParams("configScopeId", "myIssueKey", "That's " +
      "serious issue"));

    assertThat(response)
      .failsWithin(Duration.ofSeconds(2))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(AddIssueCommentException.class)
      .withMessage("Cannot add comment to the issue");
  }

}
