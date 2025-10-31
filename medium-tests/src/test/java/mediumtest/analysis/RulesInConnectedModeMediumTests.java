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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;
import utils.TestPlugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability.SECURITY_HOTSPOTS;
import static org.sonarsource.sonarlint.core.test.utils.plugins.SonarPluginBuilder.newSonarPlugin;
import static utils.AnalysisUtils.createFile;

class RulesInConnectedModeMediumTests {

  private static final String CONFIG_SCOPE_ID = "config scope id";
  private static final String CONNECTION_ID = "myConnection";
  private static final String JAVA_MODULE_KEY = "sample-project";

  @SonarLintTest
  void should_ignore_unknown_active_rule_parameters_and_convert_deprecated_keys(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "Class.java", "");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    var activeRulesDumpingPlugin = newSonarPlugin("php")
      .withSensor(ActiveRulesDumpingSensor.class)
      .generate(baseDir);
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, harness.newFakeSonarQubeServer().start())
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, JAVA_MODULE_KEY)
      .withExtraEnabledLanguagesInConnectedMode(Language.JAVA)
      .withExtraEnabledLanguagesInConnectedMode(Language.PHP)
      .withStorage(CONNECTION_ID, s -> s
        .withPlugin(TestPlugin.JAVA)
        .withPlugin("php", activeRulesDumpingPlugin, "hash")
        .withProject(JAVA_MODULE_KEY, project -> project
          .withMainBranch("main")
          .withRuleSet("java", ruleSet -> ruleSet
            // Emulate server returning a deprecated key for local analyzer
            .withActiveRule("squid:S106", "BLOCKER")
            .withActiveRule("java:S3776", "BLOCKER", Map.of("blah", "blah"))
            // Emulate server returning a deprecated template key
            .withCustomActiveRule("squid:myCustomRule", "squid:S124", "MAJOR", Map.of("message", "Needs to be reviewed", "regularExpression", ".*REVIEW.*")))))
      .start(client);

    backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri), Map.of(), false, System.currentTimeMillis()));

    await().atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(baseDir.resolve("activerules.dump")).content()
        .contains("java:S106;java;null;")
        .contains("java:S3776;java;null;{Threshold=15}")
        .contains("java:myCustomRule;java;S124;{message=Needs to be reviewed, regularExpression=.*REVIEW.*}"));
  }

  @SonarLintTest
  void hotspot_rules_should_be_active_when_feature_flag_is_enabled(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "Class.java", "");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    var activeRulesDumpingPlugin = newSonarPlugin("php")
      .withSensor(ActiveRulesDumpingSensor.class)
      .generate(baseDir);
    var backend = harness.newBackend()
      .withBackendCapability(SECURITY_HOTSPOTS)
      .withSonarQubeConnection(CONNECTION_ID, harness.newFakeSonarQubeServer().start())
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, JAVA_MODULE_KEY)
      .withExtraEnabledLanguagesInConnectedMode(Language.JAVA)
      .withExtraEnabledLanguagesInConnectedMode(Language.PHP)
      .withStorage(CONNECTION_ID,
        s -> s
          .withServerVersion("9.7")
          .withPlugin(TestPlugin.JAVA)
          .withPlugin("php", activeRulesDumpingPlugin, "hash")
          .withProject(JAVA_MODULE_KEY, project -> project
            .withMainBranch("main")
            .withRuleSet("java", ruleSet -> ruleSet
              .withActiveRule("java:S4792", "INFO"))))
      .start(client);

    backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri), Map.of(), false, System.currentTimeMillis()));

    await().atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(baseDir.resolve("activerules.dump")).content()
        .contains("java:S4792;java;null;"));
  }

  @SonarLintTest
  void hotspot_rules_should_not_be_active_when_feature_flag_is_disabled(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "Class.java", "");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    var activeRulesDumpingPlugin = newSonarPlugin("php")
      .withSensor(ActiveRulesDumpingSensor.class)
      .generate(baseDir);
    var backend = harness.newBackend()
      .withSonarQubeConnection(CONNECTION_ID, harness.newFakeSonarQubeServer().start())
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, JAVA_MODULE_KEY)
      .withExtraEnabledLanguagesInConnectedMode(Language.JAVA)
      .withExtraEnabledLanguagesInConnectedMode(Language.PHP)
      .withStorage(CONNECTION_ID,
        s -> s
          .withServerVersion("9.7")
          .withPlugin("php", activeRulesDumpingPlugin, "hash")
          .withProject(JAVA_MODULE_KEY, project -> project
            .withMainBranch("main")
            .withRuleSet("java", ruleSet -> ruleSet
              .withActiveRule("java:S4792", "INFO"))))
      .start(client);

    backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri), Map.of(), false, System.currentTimeMillis()));

    await().atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(baseDir.resolve("activerules.dump")).content()
        .doesNotContain("java:S4792;java;null;"));
  }

  @SonarLintTest
  void should_use_ipython_standalone_active_rules_in_connected_mode(SonarLintTestHarness harness, @TempDir Path baseDir) {
    var filePath = createFile(baseDir, "mod.py", "");
    var fileUri = filePath.toUri();
    var client = harness.newFakeClient()
      .withInitialFs(CONFIG_SCOPE_ID, baseDir, List.of(new ClientFileDto(fileUri, baseDir.relativize(filePath), CONFIG_SCOPE_ID, false,
        null, filePath, null, null, true)))
      .build();
    var activeRulesDumpingPlugin = newSonarPlugin("php")
      .withSensor(ActiveRulesDumpingSensor.class)
      .generate(baseDir);
    var backend = harness.newBackend()
      .withStandaloneEmbeddedPlugin(TestPlugin.PYTHON)
      .withEnabledLanguageInStandaloneMode(Language.IPYTHON)
      .withExtraEnabledLanguagesInConnectedMode(Language.PHP)
      .withSonarQubeConnection(CONNECTION_ID, harness.newFakeSonarQubeServer().start())
      .withBoundConfigScope(CONFIG_SCOPE_ID, CONNECTION_ID, JAVA_MODULE_KEY)
      .withStorage(CONNECTION_ID,
        s -> s
          .withServerVersion("9.7")
          .withPlugin("php", activeRulesDumpingPlugin, "hash")
          .withProject(JAVA_MODULE_KEY, project -> project
            .withMainBranch("main")
            .withRuleSet("java", ruleSet -> ruleSet
              .withActiveRule("java:S4792", "INFO"))))
      .start(client);

    backend.getAnalysisService()
      .analyzeFilesAndTrack(new AnalyzeFilesAndTrackParams(CONFIG_SCOPE_ID, UUID.randomUUID(), List.of(fileUri), Map.of(), false, System.currentTimeMillis()));

    await().atMost(3, TimeUnit.SECONDS)
      .untilAsserted(() -> assertThat(baseDir.resolve("activerules.dump")).content()
        .contains("ipython:PrintStatementUsage"));
  }

}
