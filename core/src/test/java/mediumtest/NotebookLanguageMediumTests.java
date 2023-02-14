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
package mediumtest;

import java.nio.file.Path;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileSystem;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.testutils.MockWebServerExtension;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import testutils.MockWebServerExtensionWithProtobuf;

import static mediumtest.fixtures.StorageFixture.newStorage;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.Mockito.mock;
import static testutils.TestUtils.createNoOpLogOutput;

class NotebookLanguageMediumTests {

  @RegisterExtension
  private final MockWebServerExtensionWithProtobuf mockWebServerExtension = new MockWebServerExtensionWithProtobuf();

  private static final String SERVER_ID = StringUtils.repeat("very-long-id", 30);
  private static final String JAVA_MODULE_KEY = "test-project-2";
  private static ConnectedSonarLintEngineImpl sonarlint;

  @BeforeAll
  static void prepare(@TempDir Path slHome) {
    var storage = newStorage(SERVER_ID)
      .withJSPlugin()
      .withJavaPlugin()
      .withProject("test-project")
      .withProject(JAVA_MODULE_KEY)
      .create(slHome);

    var nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    var config = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId(SERVER_ID)
      .setSonarLintUserHome(slHome)
      .setStorageRoot(storage.getPath())
      .setLogOutput(createNoOpLogOutput())
      .addEnabledLanguages(Language.JAVA, Language.JS, Language.IPYTHON)
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion())
      .setModulesProvider(() -> List.of(new ClientModuleInfo("key", mock(ClientModuleFileSystem.class))))
      .build();
    sonarlint = new ConnectedSonarLintEngineImpl(config);
  }

  @AfterAll
  static void stop() {
    if (sonarlint != null) {
      sonarlint.stop(true);
      sonarlint = null;
    }
  }

  @Test
  void should_not_pull_issues_for_notebook_python_language() {
    mockWebServerExtension.addStringResponse("/api/system/status", "{\"status\": \"UP\", \"version\": \"9.6\", \"id\": \"xzy\"}");
    var timestamp = Issues.IssuesPullQueryTimestamp.newBuilder().setQueryTimestamp(123L).build();
    var issue = Issues.IssueLite.newBuilder()
      .setKey("uuid")
      .setRuleKey("sonarjava:S123")
      .setType(Common.RuleType.BUG)
      .setMainLocation(Issues.Location.newBuilder().setFilePath("foo/bar/Hello.java").setMessage("Primary message")
        .setTextRange(org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues.TextRange.newBuilder().setStartLine(1).setStartLineOffset(2).setEndLine(3)
          .setEndLineOffset(4).setHash("hash")))
      .setCreationDate(123456789L)
      .build();

    mockWebServerExtension.addProtobufResponseDelimited("/api/issues/pull?projectKey=test-project&branchName=master&languages=java,js", timestamp, issue);


    assertThatCode(() -> sonarlint.downloadAllServerIssues(mockWebServerExtension.endpointParams(),
      MockWebServerExtension.httpClient(), "test-project", "master",null)).doesNotThrowAnyException();
  }

}
