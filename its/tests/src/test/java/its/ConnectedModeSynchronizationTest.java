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
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.locator.FileLocation;
import its.utils.OrchestratorUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ConnectedModeSynchronizationTest extends AbstractConnectedTest {
  private static final String PROJECT_KEY_LANGUAGE_MIX = "sample-language-mix";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .keepBundledPlugins()
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/python-sonarlint.xml"))
    .build();

  private static ConnectedSonarLintEngine engine;

  @BeforeAll
  static void prepare(@TempDir Path sonarUserHome) throws IOException {
    var adminWsClient = newAdminWsClient(ORCHESTRATOR);
    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    provisionProject(ORCHESTRATOR, PROJECT_KEY_LANGUAGE_MIX, "Sample Language Mix");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_LANGUAGE_MIX, "java", "SonarLint IT Java");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_LANGUAGE_MIX, "py", "SonarLint IT Python");

    // Build project to have bytecode and analyze
    ORCHESTRATOR.executeBuild(MavenBuild.create(new File("projects/sample-language-mix/pom.xml"))
      .setCleanPackageSonarGoals()
      .setProperty("sonar.projectKey", PROJECT_KEY_LANGUAGE_MIX)
      .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
      .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD));

    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
      .setExtraProperties(new HashMap<>())
      // authorize only Java to check that Python is left aside during sync
      .addEnabledLanguage(Language.JAVA)
      .build());

  }

  @AfterAll
  public static void stop() {
    engine.stop(true);
  }

  @Test
  void sync_all_issues_of_enabled_languages() {
    assumeTrue(ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 6), "SonarQube should support pulling issues");

    engine.syncServerIssues(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY_LANGUAGE_MIX, MAIN_BRANCH_NAME, null);

    var javaIssues = engine.getServerIssues(new ProjectBinding(PROJECT_KEY_LANGUAGE_MIX, "", ""), MAIN_BRANCH_NAME, "src/main/java/foo/Foo.java");
    var pythonIssues = engine.getServerIssues(new ProjectBinding(PROJECT_KEY_LANGUAGE_MIX, "", ""), MAIN_BRANCH_NAME, "src/main/java/foo/main.py");

    assertThat(javaIssues).hasSize(2);
    assertThat(pythonIssues).isEmpty();
  }
}
