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
import its.utils.OrchestratorUtils;
import its.utils.PluginLocator;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectedEmbeddedPluginTest extends AbstractConnectedTest {
  private static final String PROJECT_KEY_C = "sample-c";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .setEdition(Edition.ENTERPRISE)
    .activateLicense()
    .keepBundledPlugins()
    .restoreProfileAtStartup(FileLocation.ofClasspath("/c-sonarlint.xml"))
    .build();

  @TempDir
  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;

  @BeforeAll
  static void prepare() throws Exception {
    var adminWsClient = newAdminWsClient(ORCHESTRATOR);

    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    provisionProject(ORCHESTRATOR, PROJECT_KEY_C, "Sample C");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_C, "c", "SonarLint IT C");
  }

  @BeforeEach
  void start() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
      .addEnabledLanguage(Language.C)
      .useEmbeddedPlugin(Language.C.getPluginKey(), PluginLocator.getCppPluginPath())
      .setLogOutput((msg, level) -> System.out.println(msg))
      .build());
  }

  @AfterEach
  void stop() {
    try {
      engine.stop(true);
    } catch (Exception e) {
      // Ignore
    }
  }

  /**
   * SLCORE-365 c:FunctionSinglePointOfExit has been deprecated in SonarCFamily 6.32.0.44918 (SQ 9.4) so older versions of SQ will return a QP with rule c:FunctionSinglePointOfExit,
   * while embedded analyzer contains the new rule key. So SLCORE should do the translation.
   */
  @Test
  void analysisWithDeprecatedRuleKey() throws Exception {
    updateProject(PROJECT_KEY_C);
    var issueListener = new SaveIssueListener();

    var buildWrapperContent = "{\"version\":0,\"captures\":[" +
      "{" +
      "\"compiler\": \"clang\"," +
      "\"executable\": \"compiler\"," +
      "\"stdout\": \"#define __STDC_VERSION__ 201112L\n\"," +
      "\"stderr\": \"\"" +
      "}," +
      "{" +
      "\"compiler\": \"clang\"," +
      "\"executable\": \"compiler\"," +
      "\"stdout\": \"#define __cplusplus 201703L\n\"," +
      "\"stderr\": \"\"" +
      "}," +
      "{\"compiler\":\"clang\",\"cwd\":\"" +
      Paths.get("projects/" + PROJECT_KEY_C).toAbsolutePath().toString().replace("\\", "\\\\") +
      "\",\"executable\":\"compiler\",\"cmd\":[\"cc\",\"src/file.c\"]}]}";

    var analysisConfiguration = createAnalysisConfiguration(PROJECT_KEY_C, PROJECT_KEY_C, "src/file.c", "sonar.cfamily.build-wrapper-content",
      buildWrapperContent);

    engine.analyze(analysisConfiguration, issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(2).extracting(Issue::getRuleKey).containsOnly("c:S3805", "c:S1005");
  }

  private void updateProject(String projectKey) {
    engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), projectKey, null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(projectKey), null);
  }

}
