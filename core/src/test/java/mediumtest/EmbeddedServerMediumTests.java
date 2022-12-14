/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package mediumtest;

import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension.httpClient;

class EmbeddedServerMediumTests {

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_return_the_ide_name_and_empty_description_if_the_origin_is_not_trusted() {
    var fakeClient = newFakeClient().withName("ClientName").build();
    backend = newBackend().withEmbeddedServer().build(fakeClient);
    var embeddedServerPort = getEmbeddedServerPort();

    var response = httpClient().get("http://localhost:" + embeddedServerPort + "/sonarlint/api/status");

    assertThat(response)
      .extracting(HttpClient.Response::isSuccessful, HttpClient.Response::code, HttpClient.Response::bodyAsString)
      .containsExactly(true, 200, "{\"ideName\":\"ClientName\",\"description\":\"\"}");
  }

  @Test
  void it_should_return_the_ide_name_and_full_description_if_the_origin_is_trusted() {
    var fakeClient = newFakeClient().withName("ClientName").withVersion("1.2.3").withEdition("Edition").withWorkspaceTitle("WorkspaceTitle").build();
    backend = newBackend().withEmbeddedServer().withSonarQubeConnection("connectionId", "https://sonar.my").build(fakeClient);
    var embeddedServerPort = getEmbeddedServerPort();

    var response = httpClient().withHeader("Origin","https://sonar.my")
      .get("http://localhost:" + embeddedServerPort + "/sonarlint/api/status");

    assertThat(response)
      .extracting(HttpClient.Response::isSuccessful, HttpClient.Response::code, HttpClient.Response::bodyAsString)
      .containsExactly(true, 200, "{\"ideName\":\"ClientName\",\"description\":\"1.2.3 - WorkspaceTitle (Edition)\"}");
  }

  @Test
  void it_should_return_the_ide_name_and_partial_description_if_the_origin_is_trusted_and_no_edition() {
    var fakeClient = newFakeClient().withName("ClientName").withVersion("1.2.3").withWorkspaceTitle("WorkspaceTitle").build();
    backend = newBackend().withEmbeddedServer().withSonarQubeConnection("connectionId", "https://sonar.my").build(fakeClient);
    var embeddedServerPort = getEmbeddedServerPort();

    var response = httpClient().withHeader("Origin","https://sonar.my")
      .get("http://localhost:" + embeddedServerPort + "/sonarlint/api/status");

    assertThat(response)
      .extracting(HttpClient.Response::isSuccessful, HttpClient.Response::code, HttpClient.Response::bodyAsString)
      .containsExactly(true, 200, "{\"ideName\":\"ClientName\",\"description\":\"1.2.3 - WorkspaceTitle\"}");
  }

  private int getEmbeddedServerPort() {
    var embeddedServerStartupLogRegex = Pattern.compile("Started embedded server on port ([0-9]+)");
    return logTester.logs().stream()
      .map(embeddedServerStartupLogRegex::matcher)
      .filter(Matcher::matches)
      .findFirst()
      .map(matcher -> Integer.parseInt(matcher.group(1)))
      .orElseThrow(() -> new IllegalStateException("Embedded server is not started"));
  }

  @RegisterExtension
  SonarLintLogTester logTester = new SonarLintLogTester();

  private SonarLintBackendImpl backend;
}
