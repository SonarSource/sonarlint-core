/*
 * SonarLint Core - ITs - Tests
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
package its;

import com.sonar.orchestrator.OrchestratorExtension;
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.version.Version;
import its.utils.OrchestratorUtils;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.settings.ResetRequest;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;

class ConnectedModeHotspotsTests extends AbstractConnectedTests {

  private static final String PROJECT_KEY_JAVA_HOTSPOT = "sample-java-hotspot";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .setEdition(Edition.DEVELOPER)
    .activateLicense()
    .keepBundledPlugins()
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-with-hotspot.xml"))
    // Ensure SSE are processed correctly just after SQ startup
    .setServerProperty("sonar.pushevents.polling.initial.delay", "2")
    .setServerProperty("sonar.pushevents.polling.period", "1")
    .setServerProperty("sonar.pushevents.polling.last.timestamp", "1")
    .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
    .build();

  private static WsClient adminWsClient;
  @TempDir
  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;

  @BeforeAll
  static void prepare() throws Exception {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);

    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_HOTSPOT, "Sample Java Hotspot");

    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_HOTSPOT, "java", "SonarLint IT Java Hotspot");

    // Build project to have bytecode and analyze
    analyzeMavenProject(ORCHESTRATOR, PROJECT_KEY_JAVA_HOTSPOT, Map.of("sonar.projectKey", PROJECT_KEY_JAVA_HOTSPOT));
  }

  @BeforeEach
  void start() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
    var globalProps = new HashMap<String, String>();
    globalProps.put("sonar.global.label", "It works");

    var globalConfig = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
      .addEnabledLanguage(Language.JAVA)
      .setLogOutput((msg, level) -> {
        System.out.println(msg);
      })
      .setExtraProperties(globalProps)
      .enableHotspots()
      .build();
    engine = new ConnectedSonarLintEngineImpl(globalConfig);
  }

  @AfterEach
  void stop() {
    adminWsClient.settings().reset(new ResetRequest().setKeys(singletonList("sonar.java.file.suffixes")));
    try {
      engine.stop(true);
    } catch (Exception e) {
      // Ignore
    }
  }

  @Test
  void reportHotspots() throws Exception {
    updateProject();

    var issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVA_HOTSPOT, PROJECT_KEY_JAVA_HOTSPOT,
      "src/main/java/foo/Foo.java",
      "sonar.java.binaries", new File("projects/sample-java-hotspot/target/classes").getAbsolutePath()),
      issueListener, null, null);

    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 7)) {
      assertThat(issueListener.getIssues()).hasSize(1)
        .extracting(Issue::getRuleKey, Issue::getType)
        .containsExactly(tuple(javaRuleKey(ORCHESTRATOR, "S4792"), RuleType.SECURITY_HOTSPOT));
    } else {
      // no hotspot detection when connected to SQ < 9.7
      assertThat(issueListener.getIssues()).isEmpty();
    }
  }

  @Test
  void loadHotspotRuleDescription() throws Exception {
    assumeThat(ORCHESTRATOR.getServer().version()).isGreaterThanOrEqualTo(Version.create("9.7"));
    updateProject();

    var ruleDetails = engine.getActiveRuleDetails(endpointParams(ORCHESTRATOR), sqHttpClient(), javaRuleKey(ORCHESTRATOR, "S4792"), PROJECT_KEY_JAVA_HOTSPOT).get();

    assertThat(ruleDetails.getName()).isEqualTo("Configuring loggers is security-sensitive");
    // HTML description is null for security hotspots when accessed through the deprecated engine API
    // When accessed through the backend service, the rule descriptions are split into sections
    // see its.ConnectedModeBackendTest.returnConvertedDescriptionSectionsForHotspotRules
    assertThat(ruleDetails.getHtmlDescription()).isNull();
  }

  @Test
  void downloadsServerHotspotsForProject() {
    updateProject();

    engine.downloadAllServerHotspots(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY_JAVA_HOTSPOT, "master", null);

    var serverHotspots = engine.getServerHotspots(new ProjectBinding(PROJECT_KEY_JAVA_HOTSPOT, "", "ide"), "master", "ide/src/main/java/foo/Foo.java");
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 7)) {
      assertThat(serverHotspots)
        .extracting("ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "resolved")
        .containsExactly(tuple("java:S4792", "Make sure that this logger's configuration is safe.", "ide/src/main/java/foo/Foo.java", 9, 4, 9, 45, false));
    } else {
      assertThat(serverHotspots).isEmpty();
    }
  }

  @Test
  void downloadsServerHotspotsForFile() {
    updateProject();
    var projectBinding = new ProjectBinding(PROJECT_KEY_JAVA_HOTSPOT, "", "ide");

    engine.downloadAllServerHotspotsForFile(endpointParams(ORCHESTRATOR), sqHttpClient(), projectBinding, "ide/src/main/java/foo/Foo.java", "master", null);

    var serverHotspots = engine.getServerHotspots(projectBinding, "master", "ide/src/main/java/foo/Foo.java");
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 7)) {
      assertThat(serverHotspots)
        .extracting("ruleKey", "message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "resolved")
        .containsExactly(tuple("java:S4792", "Make sure that this logger's configuration is safe.", "ide/src/main/java/foo/Foo.java", 9, 4, 9, 45, false));
    } else {
      assertThat(serverHotspots).isEmpty();
    }
  }

  private void updateProject() {
    var projectKey = PROJECT_KEY_JAVA_HOTSPOT;
    engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), projectKey, null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(projectKey), null);
    engine.syncServerIssues(endpointParams(ORCHESTRATOR), sqHttpClient(), projectKey, MAIN_BRANCH_NAME, null);
  }
}
