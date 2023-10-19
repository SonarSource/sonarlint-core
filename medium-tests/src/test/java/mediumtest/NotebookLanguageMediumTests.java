/*
 * SonarLint Core - Medium Tests
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
package mediumtest;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestBackend;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static mediumtest.fixtures.SonarLintBackendFixture.newFakeClient;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.Mockito.mock;
import static testutils.TestUtils.createNoOpLogOutput;
import static testutils.TestUtils.protobufBodyDelimited;

class NotebookLanguageMediumTests {

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private static final String CONNECTION_ID = StringUtils.repeat("very-long-id", 30);
  private static final String JAVA_MODULE_KEY = "test-project-2";
  private static ConnectedSonarLintEngineImpl sonarlint;
  private static SonarLintTestBackend backend;

  @BeforeAll
  static void prepare() {
    var fakeClient = newFakeClient()
      .build();
    backend = newBackend()
      .withSonarQubeConnection(CONNECTION_ID, sonarqubeMock.baseUrl())
      .withStorage(CONNECTION_ID, s -> s.withJSPlugin()
        .withJavaPlugin()
        .withProject("test-project")
        .withProject(JAVA_MODULE_KEY))
      .build(fakeClient);

    var nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId(CONNECTION_ID)
      .setSonarLintUserHome(backend.getUserHome())
      .setStorageRoot(backend.getStorageRoot())
      .setLogOutput(createNoOpLogOutput())
      .addEnabledLanguages(Language.JAVA, Language.JS, Language.IPYTHON)
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .setModulesProvider(() -> List.of(new ClientModuleInfo("key", mock(ClientModuleFileSystem.class))))
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);
  }

  @AfterAll
  static void stop() throws ExecutionException, InterruptedException {
    if (sonarlint != null) {
      sonarlint.stop(true);
      sonarlint = null;
    }
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void should_not_pull_issues_for_notebook_python_language() {
    sonarqubeMock.stubFor(get("/api/system/status")
      .willReturn(aResponse().withStatus(200).withBody("{\"status\": \"UP\", \"version\": \"9.6\", \"id\": \"xzy\"}")));
    var timestamp = Issues.IssuesPullQueryTimestamp.newBuilder().setQueryTimestamp(123L).build();
    var issue = Issues.IssueLite.newBuilder()
      .setKey("uuid")
      .setRuleKey("sonarjava:S123")
      .setType(Common.RuleType.BUG)
      .setMainLocation(Issues.Location.newBuilder().setFilePath("foo/bar/Hello.java").setMessage("Primary message")
        .setTextRange(Issues.TextRange.newBuilder().setStartLine(1).setStartLineOffset(2).setEndLine(3)
          .setEndLineOffset(4).setHash("hash")))
      .setCreationDate(123456789L)
      .build();

    sonarqubeMock.stubFor(get("/api/issues/pull?projectKey=test-project&branchName=master&languages=java,js")
      .willReturn(aResponse().withStatus(200).withResponseBody(protobufBodyDelimited(timestamp, issue))));

    assertThatCode(() -> sonarlint.downloadAllServerIssues(new EndpointParams(sonarqubeMock.baseUrl(), false, null),
      backend.getHttpClient(CONNECTION_ID), "test-project", "master", null)).doesNotThrowAnyException();
  }

}
