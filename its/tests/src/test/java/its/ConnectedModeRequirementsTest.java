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
import com.sonar.orchestrator.locator.FileLocation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.NodeJsHelper;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.plugin.commons.SkipReason;

import static its.tools.ItUtils.SONAR_VERSION;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.Assert.fail;

public class ConnectedModeRequirementsTest extends AbstractConnectedTest {

  private static final String OLD_SONARTS_PLUGIN_KEY = "typescript";
  private static final String CUSTOM_JAVA_PLUGIN_KEY = "custom";
  private static final String PROJECT_KEY_JAVASCRIPT = "sample-javascript";
  private static final String PROJECT_KEY_TYPESCRIPT = "sample-typescript";

  @ClassRule
  public static Orchestrator ORCHESTRATOR = Orchestrator.builderEnv().setSonarVersion(SONAR_VERSION)
    .defaultForceAuthentication()
    .keepBundledPlugins()
    .addPlugin(FileLocation.of("../plugins/java-custom-rules/target/java-custom-rules-plugin.jar"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/javascript-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/typescript-sonarlint.xml"))
    .build();

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private static Path sonarUserHome;

  private ConnectedSonarLintEngine engine;
  private final List<String> logs = new ArrayList<>();

  @BeforeClass
  public static void prepare() throws Exception {
    sonarUserHome = temp.newFolder().toPath();

    newAdminWsClient(ORCHESTRATOR).users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));

    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_JAVASCRIPT, "Sample Javascript");
    ORCHESTRATOR.getServer().provisionProject(PROJECT_KEY_TYPESCRIPT, "Sample Typescript");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVASCRIPT, "js", "SonarLint IT Javascript");
    ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_TYPESCRIPT, "ts", "SonarLint IT Typescript");
  }

  @Before
  public void start() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());
  }

  private ConnectedSonarLintEngine createEngine(Consumer<ConnectedGlobalConfiguration.Builder> configurator) {
    var nodeJsHelper = new NodeJsHelper();
    nodeJsHelper.detect(null);

    var builder = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId("orchestrator")
      .setSonarLintUserHome(sonarUserHome)
      .setLogOutput((msg, level) -> {
        logs.add(msg);
      })
      .setNodeJs(nodeJsHelper.getNodeJsPath(), nodeJsHelper.getNodeJsVersion());
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
  public void dontDownloadPluginIfNotEnabledLanguage() {
    engine = createEngine(e -> e.addEnabledLanguages(Language.JS, Language.PHP, Language.TS));
    engine.update(endpointParams(ORCHESTRATOR), sqHttpClient(), null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), emptySet(), null);
    assertThat(logs).contains("[SYNC] Code analyzer 'java' is disabled in SonarLint (language not enabled). Skip downloading it.");
    // TypeScript plugin has been merged in SonarJS in SQ 8.5
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 5)) {
      assertThat(engine.getPluginDetails().stream().map(PluginDetails::key))
        .containsOnly(Language.JS.getPluginKey(), Language.PHP.getPluginKey(), CUSTOM_JAVA_PLUGIN_KEY);
    } else {
      assertThat(engine.getPluginDetails().stream().map(PluginDetails::key))
        .containsOnly(Language.JS.getPluginKey(), Language.PHP.getPluginKey(), OLD_SONARTS_PLUGIN_KEY, CUSTOM_JAVA_PLUGIN_KEY);
    }
  }

  @Test
  public void dontFailIfMissingDependentPlugin() {
    engine = createEngine(e -> e.addEnabledLanguages(Language.PHP));
    engine.update(endpointParams(ORCHESTRATOR), sqHttpClient(), null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), emptySet(), null);
    assertThat(logs).contains("Plugin 'Java Custom Rules Plugin' dependency on 'java' is unsatisfied. Skip loading it.");
    assertThat(engine.getPluginDetails()).extracting(PluginDetails::key, PluginDetails::skipReason)
      .contains(tuple(CUSTOM_JAVA_PLUGIN_KEY, Optional.of(new SkipReason.UnsatisfiedDependency("java"))));
  }

  @Test
  public void dontLoadExcludedPlugin() {
    engine = createEngine(e -> e.addEnabledLanguages(Language.JAVA, Language.JS, Language.PHP));
    engine.update(endpointParams(ORCHESTRATOR), sqHttpClient(), null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), emptySet(), null);
    assertThat(engine.getPluginDetails().stream().map(PluginDetails::key)).contains(Language.JAVA.getPluginKey());
    engine.stop(false);

    engine = createEngine(e -> e.addEnabledLanguages(Language.JS, Language.PHP));
    // The description of SonarJava changed in 6.3, embedded in SQ 8.3
    var javaDescription = ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 3) ? "Java Code Quality and Security" : "SonarJava";
    var expectedLog = String.format("Plugin '%s' is excluded because language 'Java' is not enabled. Skip loading it.", javaDescription);
    assertThat(logs).contains(expectedLog);
    assertThat(engine.getPluginDetails()).extracting(PluginDetails::key, PluginDetails::skipReason)
      .contains(tuple(Language.JAVA.getPluginKey(), Optional.of(new SkipReason.LanguagesNotEnabled(asList(Language.JAVA)))));
  }

  // SLCORE-259
  @Test
  public void analysisJavascriptWithoutTypescript() throws Exception {
    engine = createEngine(e -> e.addEnabledLanguages(Language.JS, Language.PHP));
    engine.update(endpointParams(ORCHESTRATOR), sqHttpClient(), null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(PROJECT_KEY_JAVASCRIPT), null);
    assertThat(engine.getPluginDetails().stream().map(PluginDetails::key)).contains("javascript");
    assertThat(engine.getPluginDetails().stream().map(PluginDetails::key)).doesNotContain(OLD_SONARTS_PLUGIN_KEY);

    engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY_JAVASCRIPT, null, null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(PROJECT_KEY_JAVASCRIPT), null);
    var issueListener = new SaveIssueListener();
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
    var tsAnalysisConfig = createAnalysisConfiguration(PROJECT_KEY_TYPESCRIPT, PROJECT_KEY_TYPESCRIPT, "src/Person.ts");

    var pb = new ProcessBuilder("npm" + (SystemUtils.IS_OS_WINDOWS ? ".cmd" : ""), "install")
      .directory(tsAnalysisConfig.baseDir().toFile())
      .inheritIO();
    var process = pb.start();
    if (process.waitFor() != 0) {
      fail("Unable to run npm install");
    }

    Map<String, String> extraProperties = new HashMap<>();
    extraProperties.put("sonar.typescript.internal.typescriptLocation", tsAnalysisConfig.baseDir().resolve("node_modules").toString());
    engine = createEngine(e -> e
      .setExtraProperties(extraProperties)
      .addEnabledLanguages(Language.JS, Language.PHP));
    engine.update(endpointParams(ORCHESTRATOR), sqHttpClient(), null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(PROJECT_KEY_JAVASCRIPT), null);
    assertThat(logs).doesNotContain("Code analyzer 'SonarJS' is transitively excluded in this version of SonarLint. Skip loading it.");
    assertThat(engine.getPluginDetails().stream().map(PluginDetails::key)).contains(Language.JS.getPluginKey());

    engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY_TYPESCRIPT, null, null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(PROJECT_KEY_TYPESCRIPT), null);
    var issueListenerTs = new SaveIssueListener();
    engine.analyze(tsAnalysisConfig, issueListenerTs, null, null);
    assertThat(issueListenerTs.getIssues()).hasSize(0);
    if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 0)) {
      assertThat(logs).contains("TypeScript sensor excluded");
    }
    assertThat(logs).doesNotContain("Execute Sensor: ESLint-based TypeScript analysis");
  }

}
