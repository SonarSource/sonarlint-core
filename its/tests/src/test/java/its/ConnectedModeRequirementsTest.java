/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2009-2019 SonarSource SA
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
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import its.tools.ItUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
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
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.LoadedAnalyzer;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;

import static its.tools.ItUtils.SONAR_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class ConnectedModeRequirementsTest extends AbstractConnectedTest {

  private static final String PROJECT_KEY_JAVASCRIPT = "sample-javascript";
  private static final String PROJECT_KEY_TYPESCRIPT = "sample-typescript";

  @ClassRule
  public static Orchestrator ORCHESTRATOR = Orchestrator.builderEnv().setSonarVersion(SONAR_VERSION)
    .addPlugin(MavenLocation.of("org.sonarsource.java", "sonar-java-plugin", ItUtils.javaVersion))
    .addPlugin(MavenLocation.of("org.sonarsource.php", "sonar-php-plugin", ItUtils.phpVersion))
    .addPlugin(MavenLocation.of("org.sonarsource.javascript", "sonar-javascript-plugin", ItUtils.javascriptVersion))
    // With recent version of SonarJS, SonarTS is required
    .addPlugin(MavenLocation.of("org.sonarsource.typescript", "sonar-typescript-plugin", ItUtils.typescriptVersion))
    .addPlugin(FileLocation.of("../plugins/javascript-custom-rules/target/javascript-custom-rules-plugin.jar"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/javascript-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/typescript-sonarlint.xml"))
    .build();

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;
  private List<String> logs = new ArrayList<>();

  @BeforeClass
  public static void prepare() throws Exception {
    sonarUserHome = temp.newFolder().toPath();

    ORCHESTRATOR.getServer().adminWsClient().userClient()
      .create(UserParameters.create()
        .login(SONARLINT_USER)
        .password(SONARLINT_PWD)
        .passwordConfirmation(SONARLINT_PWD)
        .name("SonarLint"));

    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_JAVASCRIPT, "Sample Javascript");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_TYPESCRIPT, "Sample Typescript");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVASCRIPT, "js", "SonarLint IT Javascript");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_TYPESCRIPT, "ts", "SonarLint IT Typescript");
  }

  @Before
  public void start() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
  }

  private ConnectedSonarLintEngine createEngine() {
    return createEngine(b -> {
    });
  }

  private ConnectedSonarLintEngine createEngine(Consumer<ConnectedGlobalConfiguration.Builder> configurator) {
    ConnectedGlobalConfiguration.Builder builder = ConnectedGlobalConfiguration.builder()
      .setServerId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
      .setLogOutput((msg, level) -> {
        logs.add(msg);
        System.out.println(msg);
      });
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
  public void dontDownloadExcludedPlugin() {
    engine = createEngine(e -> e.addExcludedCodeAnalyzer("java"));
    engine.update(config(), null);
    assertThat(logs).contains("Code analyzer 'java' is not compatible with SonarLint. Skip downloading it.");
    assertThat(engine.getLoadedAnalyzers().stream().map(LoadedAnalyzer::key)).doesNotContain("java");
  }

  @Test
  public void dontFailIfMissingBasePlugin() {
    engine = createEngine(e -> e.addExcludedCodeAnalyzer("javascript"));
    engine.update(config(), null);
    assertThat(logs).contains("Code analyzer 'JavaScript Custom Rules Plugin' is transitively excluded in this version of SonarLint. Skip loading it.");
    assertThat(engine.getLoadedAnalyzers().stream().map(LoadedAnalyzer::key)).doesNotContain("custom");
  }

  @Test
  public void dontLoadExcludedPlugin() {
    engine = createEngine();
    engine.update(config(), null);
    assertThat(engine.getLoadedAnalyzers().stream().map(LoadedAnalyzer::key)).contains("java");
    engine.stop(false);

    engine = createEngine(e -> e.addExcludedCodeAnalyzer("java"));
    assertThat(logs).contains("Code analyzer 'SonarJava' is excluded in this version of SonarLint. Skip loading it.");
    assertThat(engine.getLoadedAnalyzers().stream().map(LoadedAnalyzer::key)).doesNotContain("java");
  }

  // SLCORE-259
  @Test
  public void analysisJavascriptWithoutTypescript() throws Exception {
    engine = createEngine(e -> e.addExcludedCodeAnalyzer("typescript"));
    engine.update(config(), null);
    assertThat(logs).doesNotContain("Code analyzer 'SonarJS' is transitively excluded in this version of SonarLint. Skip loading it.");
    assertThat(engine.getLoadedAnalyzers().stream().map(LoadedAnalyzer::key)).contains("javascript");
    assertThat(engine.getLoadedAnalyzers().stream().map(LoadedAnalyzer::key)).doesNotContain("typescript");

    engine.updateProject(config(), PROJECT_KEY_JAVASCRIPT, null);
    SaveIssueListener issueListener = new SaveIssueListener();
    engine.analyze(createAnalysisConfiguration(PROJECT_KEY_JAVASCRIPT, PROJECT_KEY_JAVASCRIPT, "src/Person.js"), issueListener, null, null);
    assertThat(issueListener.getIssues()).hasSize(1);
  }

  /**
  *  SLCORE-259
  *  SonarTS has been merged into SonarJS. It means excluding the typescript plugin is not enough to prevent TS analysis.
  *  For backward compatibility, we "hacked" the core to prevent typescript analysis through SonarJS when typescript plugin is excluded.
  */
  @Test
  public void dontAnalyzeTypescriptIfExcluded() throws Exception {
    ConnectedAnalysisConfiguration tsAnalysisConfig = createAnalysisConfiguration(PROJECT_KEY_TYPESCRIPT, PROJECT_KEY_TYPESCRIPT, "src/Person.ts");

    ProcessBuilder pb = new ProcessBuilder("npm" + (SystemUtils.IS_OS_WINDOWS ? ".cmd" : ""), "install")
      .directory(tsAnalysisConfig.baseDir().toFile())
      .inheritIO();
    Process process = pb.start();
    if (process.waitFor() != 0) {
      fail("Unable to run npm install");
    }

    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("sonar.typescript.internal.typescriptLocation", tsAnalysisConfig.baseDir().resolve("node_modules").toString());
    engine = createEngine(e -> e
      .setExtraProperties(extraProperties)
      .addExcludedCodeAnalyzer("typescript"));
    engine.update(config(), null);
    assertThat(logs).doesNotContain("Code analyzer 'SonarJS' is transitively excluded in this version of SonarLint. Skip loading it.");
    assertThat(engine.getLoadedAnalyzers().stream().map(LoadedAnalyzer::key)).contains("javascript");
    assertThat(engine.getLoadedAnalyzers().stream().map(LoadedAnalyzer::key)).doesNotContain("typescript");

    engine.updateProject(config(), PROJECT_KEY_TYPESCRIPT, null);
    SaveIssueListener issueListenerTs = new SaveIssueListener();
    engine.analyze(tsAnalysisConfig, issueListenerTs, null, null);
    assertThat(issueListenerTs.getIssues()).hasSize(0);
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 0)) {
      assertThat(logs).contains("TypeScript sensor excluded");
    }
    assertThat(logs).doesNotContain("Execute Sensor: ESLint-based TypeScript analysis");
  }

  @Test
  public void dontCheckMinimalPluginVersionWhenValidatingConnection() {
    engine = createEngine(e -> e.addExcludedCodeAnalyzer("java"));
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
