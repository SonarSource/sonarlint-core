/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2009-2020 SonarSource SA
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
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.user.UserParameters;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.WsHelperImpl;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine.State;
import org.sonarsource.sonarlint.core.client.api.connected.LoadedAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.UpdateResult;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;

import static its.tools.ItUtils.SONAR_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * This test verifies that SonarPython 1.9 gets excluded due to being below minimum supported version.
 */
public class ConnectedModeExcludeByVersionTest extends AbstractConnectedTest {

  @BeforeClass
  public static void beforeClass() {
    boolean atMost72 = SONAR_VERSION.contains("6.7");
    assumeTrue(atMost72);
  }

  @Rule
  public Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .setSonarVersion(SONAR_VERSION)
    .addPlugin(MavenLocation.of("org.sonarsource.python", "sonar-python-plugin", "1.9.0.2010")).build();

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;
  private List<String> logs = new ArrayList<>();

  @Before
  public void prepare() throws Exception {
    sonarUserHome = temp.newFolder().toPath();

    ORCHESTRATOR.getServer().adminWsClient().userClient()
      .create(UserParameters.create()
        .login(SONARLINT_USER)
        .password(SONARLINT_PWD)
        .passwordConfirmation(SONARLINT_PWD)
        .name("SonarLint"));
  }

  private ConnectedSonarLintEngine createEngine(Consumer<ConnectedGlobalConfiguration.Builder> configurator) {
    ConnectedGlobalConfiguration.Builder builder = ConnectedGlobalConfiguration.builder()
      .setServerId("orchestrator")
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
    assertThat(engine.getState()).isEqualTo(State.NEVER_UPDATED);

    UpdateResult update = engine.update(config(), null);
    assertThat(update.status().getLastUpdateDate()).isNotNull();
    assertThat(engine.getLoadedAnalyzers().stream().map(LoadedAnalyzer::key)).doesNotContain(Language.PYTHON.getPluginKey());
    assertThat(logs).contains("Code analyzer 'python' version '1.9.0.2010' is not supported (minimal version is '1.9.1.2080'). Skip downloading it.");
  }

  @Test
  public void dontCheckMinimalPluginVersionWhenValidatingConnection() {
    engine = createEngine(e -> e.addEnabledLanguages(Language.PYTHON));
    ValidationResult result = new WsHelperImpl().validateConnection(config());
    assertThat(result.success()).isTrue();
  }

  private ServerConfiguration config() {
    return ServerConfiguration.builder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .userAgent("SonarLint ITs")
      .credentials(SONARLINT_USER, SONARLINT_PWD)
      .build();
  }
}
