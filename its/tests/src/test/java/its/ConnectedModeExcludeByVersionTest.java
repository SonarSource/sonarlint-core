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
import com.sonar.orchestrator.locator.MavenLocation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectionValidator;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static its.tools.ItUtils.SONAR_VERSION;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * This test verifies that SonarPython 1.13 gets excluded due to being below minimum supported version.
 */
public class ConnectedModeExcludeByVersionTest extends AbstractConnectedTest {

  @BeforeClass
  public static void beforeClass() {
    var isLowestSupportedVersion = SONAR_VERSION.contains("7.9");
    assumeTrue(isLowestSupportedVersion);
  }

  @Rule
  public Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .defaultForceAuthentication()
    .setSonarVersion(SONAR_VERSION)
    .addPlugin(MavenLocation.of("org.sonarsource.python", "sonar-python-plugin", "1.13.0.2922")).build();

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;
  private final List<String> logs = new ArrayList<>();

  @Before
  public void prepare() throws Exception {
    sonarUserHome = temp.newFolder().toPath();

    newAdminWsClient(ORCHESTRATOR).users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));
  }

  private ConnectedSonarLintEngine createEngine(Consumer<ConnectedGlobalConfiguration.Builder> configurator) {
    var builder = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
      .setLogOutput((msg, level) -> logs.add(msg));
    configurator.accept(builder);
    return new ConnectedSonarLintEngineImpl(builder.build());
  }

  @After
  public void stop() {
    try {
      engine.stop(true);
    } catch (Exception e) {
      // Ignore
    }
  }

  @Test
  public void dontDownloadPluginWithUnsupportedVersion() {
    engine = createEngine(e -> e.addEnabledLanguages(Language.PYTHON));
    assertThat(engine.getGlobalStorageStatus()).isNull();

    var update = engine.update(endpointParams(ORCHESTRATOR), sqHttpClient(), null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), emptySet(), null);
    assertThat(update.status().getLastUpdateDate()).isNotNull();
    assertThat(engine.getGlobalStorageStatus()).isNotNull();
    assertThat(engine.getPluginDetails().stream().map(PluginDetails::key)).doesNotContain(Language.PYTHON.getPluginKey());
    assertThat(logs).contains("[SYNC] Code analyzer 'python' version '1.13.0.2922' is not supported (minimal version is '1.14.0.3086'). Skip downloading it.");
  }

  @Test
  public void dontCheckMinimalPluginVersionWhenValidatingConnection() throws ExecutionException, InterruptedException {
    engine = createEngine(e -> e.addEnabledLanguages(Language.PYTHON));
    var result = new ConnectionValidator(new ServerApiHelper(endpointParams(ORCHESTRATOR), sqHttpClient())).validateConnection().get();
    assertThat(result.success()).isTrue();
  }

}
