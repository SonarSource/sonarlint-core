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

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.locator.FileLocation;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;

import static its.tools.ItUtils.SONAR_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class ConnectedModeSynchronizationTest extends AbstractConnectedTest {
  private static final String PROJECT_KEY_LANGUAGE_MIX = "sample-language-mix";

  @ClassRule
  public static Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .defaultForceAuthentication()
    .setSonarVersion(SONAR_VERSION)
    .keepBundledPlugins()
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/python-sonarlint.xml"))
    .build();

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  private static ConnectedSonarLintEngine engine;

  @BeforeClass
  public static void prepare() throws IOException {
    var adminWsClient = newAdminWsClient(ORCHESTRATOR);
    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_LANGUAGE_MIX, "Sample Language Mix");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_LANGUAGE_MIX, "java", "SonarLint IT Java");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_LANGUAGE_MIX, "py", "SonarLint IT Python");

    // Build project to have bytecode and analyze
    ORCHESTRATOR.executeBuild(MavenBuild.create(new File("projects/sample-language-mix/pom.xml"))
      .setCleanPackageSonarGoals()
      .setProperty("sonar.projectKey", PROJECT_KEY_LANGUAGE_MIX)
      .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
      .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD));

    Path sonarUserHome = temp.newFolder().toPath();
    engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
      .setExtraProperties(new HashMap<>())
      // authorize only Java to check that Python is left aside during sync
      .addEnabledLanguage(Language.JAVA)
      .build());

  }

  @AfterClass
  public static void stop() {
    engine.stop(true);
  }

  @Test
  public void sync_all_issues_of_enabled_languages() {
    assumeTrue("SonarQube should support pulling issues",
      ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 5));

    // to replace with the sync call when implemented
    engine.downloadAllServerIssues(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY_LANGUAGE_MIX, "master", null);

    var javaIssues = engine.getServerIssues(new ProjectBinding(PROJECT_KEY_LANGUAGE_MIX, "", ""), "master", "src/main/java/foo/Foo.java");
    var pythonIssues = engine.getServerIssues(new ProjectBinding(PROJECT_KEY_LANGUAGE_MIX, "", ""), "master", "src/main/java/foo/main.py");

    assertThat(javaIssues).hasSize(2);
    assertThat(pythonIssues).isEmpty();
  }
}
