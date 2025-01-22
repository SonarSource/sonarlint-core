/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource SA
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
package mediumtest.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidOpenFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static utils.AnalysisUtils.getPublishedIssues;

class AnalysisReadinessMediumTests {

  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";

  @SonarLintTest
  void it_should_be_immediately_consider_analysis_to_be_ready_when_adding_a_non_bound_configuration_scope(SonarLintTestHarness harness) {
    var client = harness.newFakeClient().build();
    var backend = harness.newBackend()
      .start(client);

    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(List.of(new ConfigurationScopeDto(CONFIG_SCOPE_ID, null, true, "name", null))));

    verify(client, timeout(1000)).didChangeAnalysisReadiness(Set.of(CONFIG_SCOPE_ID), true);
  }

  @SonarLintTest
  void it_should_analyze_xml_file_in_connected_mode(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "pom.xml",
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.foo</groupId>
          <artifactId>bar</artifactId>
          <version>${pom.version}</version>
        </project>""");
    var fileUri = filePath.toUri();
    var server = harness.newFakeSonarQubeServer()
      .withProject("projectKey")
      .start();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false, null, filePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withSonarQubeConnection("connectionId", server.baseUrl(),
        storage -> storage.withPlugin(TestPlugin.XML).withProject("projectKey", project -> project.withRuleSet("xml", ruleSet -> ruleSet.withActiveRule("xml:S3421", "BLOCKER"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, "connectionId", "projectKey")
      .withFullSynchronization()
      .withExtraEnabledLanguagesInConnectedMode(Language.XML)
      .start(client);

    verify(client, never()).didChangeAnalysisReadiness(Set.of(CONFIG_SCOPE_ID), true);

    //File opened but not analyzed since analysis is not ready yet
    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));
    verify(client, never()).raiseIssues(eq(CONFIG_SCOPE_ID), any(), eq(false), any());

    client.waitForSynchronization();

    //analysis is ready
    await().atMost(1, TimeUnit.SECONDS)
      .untilAsserted(() -> verify(client).didChangeAnalysisReadiness(Set.of(CONFIG_SCOPE_ID), true));
    await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID)).isNotEmpty());

    var publishedIssues = getPublishedIssues(client, CONFIG_SCOPE_ID);
    assertThat(publishedIssues).containsOnlyKeys(fileUri);
  }

  private static Path createFile(Path folderPath, String fileName, String content) {
    var filePath = folderPath.resolve(fileName);
    try {
      Files.writeString(filePath, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return filePath;
  }
}
