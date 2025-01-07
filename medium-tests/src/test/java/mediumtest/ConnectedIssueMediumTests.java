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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.GetEffectiveIssueDetailsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.common.Language.JAVA;
import static utils.AnalysisUtils.createFile;

class ConnectedIssueMediumTests {
  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();
  private static final String CONFIG_SCOPE_ID = "configScopeId";
  private static final String CONNECTION_ID = "connectionId";

  @SonarLintTest
  void simpleJavaBound(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var inputFile = createFile(baseDir, "Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}");
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIG_SCOPE_ID, false, null, inputFile, null, null, true)))
      .build();
    var projectKey = "projectKey";
    var branchName = "main";
    var server = harness.newFakeSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile
        .withLanguage("java")
        .withActiveRule("java:S106", activeRule -> activeRule
          .withSeverity(IssueSeverity.MAJOR))
        .withActiveRule("java:S1220", activeRule -> activeRule
          .withSeverity(IssueSeverity.MINOR))
        .withActiveRule("java:S1481", activeRule -> activeRule
          .withSeverity(IssueSeverity.BLOCKER)))
      .withProject(projectKey,
        project -> project
          .withQualityProfile("qpKey")
          .withBranch(branchName))
      .withPlugin(TestPlugin.JAVA)
      .start();
    var backend = harness.newBackend()
      .withFullSynchronization()
      .withSecurityHotspotsEnabled()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, projectKey)
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .build(client);
    client.waitForSynchronization();

    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(inputFile.toUri()), Map.of(), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID)).isNotEmpty());

    var issues = client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID);
    assertThat(issues).extracting("ruleKey", "severity")
      .usingRecursiveFieldByFieldElementComparator()
      .containsOnly(
        tuple("java:S106", IssueSeverity.MAJOR),
        tuple("java:S1220", IssueSeverity.MINOR),
        tuple("java:S1481", IssueSeverity.BLOCKER));
  }

  @SonarLintTest
  void emptyQPJava(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var inputFile = createFile(baseDir, "Foo.java",
      "public class Foo {\n"
        + "  public void foo() {\n"
        + "    int x;\n"
        + "    System.out.println(\"Foo\");\n"
        + "    System.out.println(\"Foo\"); //NOSONAR\n"
        + "  }\n"
        + "}");
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, List.of(
        new ClientFileDto(inputFile.toUri(), baseDir.relativize(inputFile), CONFIG_SCOPE_ID, false, null, inputFile, null, null, true)))
      .build();
    var projectKey = "projectKey";
    var branchName = "main";
    var server = harness.newFakeSonarQubeServer()
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java"))
      .withProject(projectKey,
        project -> project
          .withQualityProfile("qpKey")
          .withBranch(branchName))
      .withPlugin(TestPlugin.JAVA)
      .start();
    var backend = harness.newBackend()
      .withFullSynchronization()
      .withSecurityHotspotsEnabled()
      .withSonarQubeConnection(CONNECTION_ID, server)
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, projectKey)
      .withExtraEnabledLanguagesInConnectedMode(JAVA)
      .build(client);
    client.waitForSynchronization();

    var analysisId = UUID.randomUUID();
    var analysisResult = backend.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(inputFile.toUri()), Map.of(), true, System.currentTimeMillis()))
      .join();
    assertThat(analysisResult.getFailedAnalysisFiles()).isEmpty();
    await().during(2, TimeUnit.SECONDS).untilAsserted(() -> assertThat(client.getRaisedIssuesForScopeIdAsList(CONFIG_SCOPE_ID)).isEmpty());
  }

  @SonarLintTest
  void it_should_get_hotspot_details(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var fileFoo = createFile(baseDir, "Foo.java", "public class Foo {\n" +
      "\n" +
      "  void foo() {\n" +
      "    String password = \"blue\";\n" +
      "  }\n" +
      "}\n");
    var fileFooUri = fileFoo.toUri();

    var connectionId = "connectionId";
    var branchName = "branchName";
    var projectKey = "projectKey";
    var serverWithHotspots = harness.newFakeSonarQubeServer("10.4")
      .withQualityProfile("qpKey", qualityProfile -> qualityProfile.withLanguage("java")
        .withActiveRule("java:S2068", activeRule -> activeRule.withSeverity(IssueSeverity.BLOCKER)))
      .withProject(projectKey,
        project -> project
          .withQualityProfile("qpKey")
          .withBranch(branchName))
      .start();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(
        new ClientFileDto(fileFooUri, baseDir.relativize(fileFoo), CONFIG_SCOPE_ID, false, null, fileFoo, null, null, true)))
      .build();
    var backend = harness.newBackend()
      .withFullSynchronization()
      .withSecurityHotspotsEnabled()
      .withSonarQubeConnection(connectionId, serverWithHotspots,
        storage -> storage.withServerVersion("10.4").withProject(projectKey,
          project -> project.withRuleSet("java", ruleSet -> ruleSet.withActiveRule("java:S2068", "BLOCKER"))))
      .withBoundConfigScope(CONFIG_SCOPE_ID, connectionId, projectKey)
      .withConnectedEmbeddedPluginAndEnabledLanguage(TestPlugin.JAVA)
      .build(client);
    await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> Assertions.assertThat(client.getSynchronizedConfigScopeIds()).contains(CONFIG_SCOPE_ID));

    var analysisId = UUID.randomUUID();

    backend.getAnalysisService().analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, analysisId, List.of(fileFooUri), Map.of(), true, System.currentTimeMillis()))
      .join();
    await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> AssertionsForInterfaceTypes.assertThat(client.getRaisedHotspotsForScopeIdAsList(CONFIG_SCOPE_ID)).hasSize(1));

    var raisedIssuesForFoo = client.getRaisedIssuesForScopeId(CONFIG_SCOPE_ID).get(fileFooUri);
    var raisedHotspotsForFoo = client.getRaisedHotspotsForScopeId(CONFIG_SCOPE_ID).get(fileFooUri);
    AssertionsForInterfaceTypes.assertThat(raisedIssuesForFoo).isEmpty();
    AssertionsForInterfaceTypes.assertThat(raisedHotspotsForFoo).hasSize(1);

    var result = backend.getIssueService().getEffectiveIssueDetails(new GetEffectiveIssueDetailsParams(CONFIG_SCOPE_ID, raisedHotspotsForFoo.get(0).getId())).join();
    assertThat(result.getDetails()).isNotNull();

    serverWithHotspots.shutdown();
  }
}
