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
package mediumtest;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidOpenFileParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.issue.RaisedFindingDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

public class SecurityMediumTest {
  private static final String CONFIG_SCOPE_ID = "CONFIG_SCOPE_ID";
  private static final boolean COMMERCIAL_ENABLED = System.getProperty("commercial") != null;

  @SonarLintTest
  void it_should_find_taint_issues(SonarLintTestHarness harness) {
    assumeTrue(COMMERCIAL_ENABLED);
    var projectWithTaint = new File("src/test/projects/project-with-taint").getAbsoluteFile().toPath();
    var srcFilePath = projectWithTaint.resolve("src/main/java/org/owasp/benchmark/testcode/BenchmarkTest00008.java");
    var fileUri = srcFilePath.toUri();
    var fakeClient = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, projectWithTaint, List.of(new ClientFileDto(fileUri, projectWithTaint.relativize(srcFilePath), CONFIG_SCOPE_ID, false,
        null, srcFilePath, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withUnboundConfigScope(CONFIG_SCOPE_ID, "My Project 1")
      .withStandaloneRuleConfig("javasecurity:S3649", true, Map.of())
      .withStandaloneEmbeddedPlugin(TestPlugin.JAVA)
      .withStandaloneEmbeddedPlugin(TestPlugin.SECURITY)
      .withStandaloneEmbeddedPluginAndEnabledLanguage(TestPlugin.SECURITY_JAVA_FRONTEND)
      .start(fakeClient);
    fakeClient.setInferredAnalysisProperties(CONFIG_SCOPE_ID, Map.of("sonar.java.libraries",
      "/home/damien.urruty/.m2/repository/javax/javaee-api/7.0/javaee-api-7.0.jar"));

    backend.getFileService().didOpenFile(new DidOpenFileParams(CONFIG_SCOPE_ID, fileUri));

    verify(fakeClient, timeout(4000).times(1)).raiseIssues(eq(CONFIG_SCOPE_ID), any(), eq(false), any());
    var raisedIssuesForScopeId = fakeClient.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID);
    assertThat(raisedIssuesForScopeId)
      .containsOnlyKeys(fileUri);
    assertThat(raisedIssuesForScopeId.get(fileUri))
      .extracting(RaisedFindingDto::getRuleKey)
      .contains("javasecurity:S3649");
  }
}
