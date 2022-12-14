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
import com.sonar.orchestrator.container.Edition;
import its.tools.OrchestratorUtils;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.SonarLintBackendImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleDescriptionTabDto;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetActiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.client.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;

public class ConnectedModeBackendTest extends AbstractConnectedTest {

  private static final String PROJECT_KEY_JAVA_TAINT = "sample-java-taint";

  @ClassRule
  public static Orchestrator ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .setEdition(Edition.DEVELOPER)
    .activateLicense()
    .keepBundledPlugins()
    .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
    .build();

  @ClassRule
  public static TemporaryFolder temp = new TemporaryFolder();

  private static Path sonarUserHome;

  private SonarLintBackend backend;
  // still needed for the sync
  private ConnectedSonarLintEngine engine;

  @BeforeClass
  public static void prepare() throws Exception {
    var adminWsClient = newAdminWsClient(ORCHESTRATOR);
    sonarUserHome = temp.newFolder().toPath();

    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));
    provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_TAINT, "Java With Taint Vulnerabilities");

    // Build project to have bytecode and analyze
    analyzeMavenProject(ORCHESTRATOR, PROJECT_KEY_JAVA_TAINT, Map.of("sonar.projectKey", PROJECT_KEY_JAVA_TAINT));
  }

  @Before
  public void start() {
    FileUtils.deleteQuietly(sonarUserHome.toFile());

    backend = new SonarLintBackendImpl(newDummySonarLintClient());
    backend.initialize(
      new InitializeParams("integrationTests", sonarUserHome.resolve("storage"), Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(), Set.of(Language.JAVA),
        Collections.emptySet(), false, List.of(new SonarQubeConnectionConfigurationDto("ORCHESTRATOR", ORCHESTRATOR.getServer().getUrl())), Collections.emptyList(),
        sonarUserHome.toString()));

    var globalConfig = ConnectedGlobalConfiguration.sonarQubeBuilder()
      .setConnectionId("ORCHESTRATOR")
      .setSonarLintUserHome(sonarUserHome)
      .addEnabledLanguage(Language.JAVA)
      .setLogOutput((msg, level) -> {
        System.out.println(msg);
      })
      .build();
    engine = new ConnectedSonarLintEngineImpl(globalConfig);

    // sync is still done by the engine for now
    updateProject(PROJECT_KEY_JAVA_TAINT);
  }

  @After
  public void stop() {
    backend.shutdown();
    engine.stop(true);
  }

  @Test
  public void returnDescriptionSectionsForTaintRules() throws ExecutionException, InterruptedException {
    // sync is still done by the engine for now
    updateProject(PROJECT_KEY_JAVA_TAINT);
    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto("project", null, true, "Project", new BindingConfigurationDto("ORCHESTRATOR", PROJECT_KEY_JAVA_TAINT, false)))));

    var activeRuleDetailsResponse = backend.getActiveRulesService().getActiveRuleDetails(new GetActiveRuleDetailsParams("project", "javasecurity:S2083")).get();

    var description = activeRuleDetailsResponse.details().getDescription();

    if (!ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 5)) {
      // no description sections at that time
      assertThat(description.isRight()).isFalse();
    } else {
      assertThat(description)
        .extracting("right.introductionHtmlContent")
        .isNull();
      assertThat(description)
        .extracting("right.tabs", as(list(ActiveRuleDescriptionTabDto.class)))
        .flatExtracting(ConnectedModeBackendTest::extractTabContent)
        .contains(
          "Why is this an issue?",
          "<p>Path injections occur when an application uses untrusted data to construct a file path and access this file without validating its path first.</p>\n" +
            "<p>A user with malicious intent would inject specially crafted values, such as <code>../</code>, to change the initial intended path. The resulting\n" +
            "path would resolve somewhere in the filesystem where the user should not normally have access to.</p>\n" +
            "<h3>What is the potential impact?</h3>\n" +
            "<p>A web application is vulnerable to path injection and an attacker is able to exploit it.</p>\n" +
            "<p>The files that can be affected are limited by the permission of the process that runs the application. Worst case scenario: the process runs with\n" +
            "root privileges on Linux, and therefore any file can be affected.</p>\n" +
            "<p>Below are some real-world scenarios that illustrate some impacts of an attacker exploiting the vulnerability.</p>\n" +
            "<h4>Override or delete arbitrary files</h4>\n" +
            "<p>The injected path component tampers with the location of a file the application is supposed to delete or write into. The vulnerability is exploited\n" +
            "to remove or corrupt files that are critical for the application or for the system to work properly.</p>\n" +
            "<p>It could result in data being lost or the application being unavailable.</p>\n" +
            "<h4>Read arbitrary files</h4>\n" +
            "<p>The injected path component tampers with the location of a file the application is supposed to read and output. The vulnerability is exploited to\n" +
            "leak the content of arbitrary files from the file system, including sensitive files like SSH private keys.</p>",
          "How can I fix it?",
          // actual description not checked because it changes frequently between versions
          "java_se", "Java SE",
          "More Info",
          "<h3>Standards</h3>\n" +
            "<ul>\n" +
            "  <li> <a href=\"https://owasp.org/Top10/A01_2021-Broken_Access_Control/\">OWASP Top 10 2021 Category A1</a> - Broken Access Control </li>\n" +
            "  <li> <a href=\"https://owasp.org/Top10/A03_2021-Injection/\">OWASP Top 10 2021 Category A3</a> - Injection </li>\n" +
            "  <li> <a href=\"https://www.owasp.org/index.php/Top_10-2017_A1-Injection\">OWASP Top 10 2017 Category A1</a> - Injection </li>\n" +
            "  <li> <a href=\"https://www.owasp.org/index.php/Top_10-2017_A5-Broken_Access_Control\">OWASP Top 10 2017 Category A5</a> - Broken Access Control </li>\n" +
            "  <li> <a href=\"https://cwe.mitre.org/data/definitions/20\">MITRE, CWE-20</a> - Improper Input Validation </li>\n" +
            "  <li> <a href=\"https://cwe.mitre.org/data/definitions/22\">MITRE, CWE-22</a> - Improper Limitation of a Pathname to a Restricted Directory ('Path\n" +
            "  Traversal') </li>\n" +
            "</ul>");
    }
  }

  private static List<Object> extractTabContent(ActiveRuleDescriptionTabDto tab) {
    if (tab.getContent().isLeft()) {
      return List.of(tab.getTitle(), tab.getContent().getLeft().getHtmlContent());
    }
    return tab.getContent().getRight().stream().flatMap(s -> Stream.of(tab.getTitle(), s.getHtmlContent(), s.getContextKey(), s.getDisplayName())).collect(Collectors.toList());
  }

  private void updateProject(String projectKey) {
    engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), projectKey, null);
    engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(projectKey), null);
    engine.syncServerIssues(endpointParams(ORCHESTRATOR), sqHttpClient(), projectKey, MAIN_BRANCH_NAME, null);
  }

  private static SonarLintClient newDummySonarLintClient() {
    return new SonarLintClient() {
      @Override
      public void suggestBinding(SuggestBindingParams params) {

      }

      @Override
      public CompletableFuture<FindFileByNamesInScopeResponse> findFileByNamesInScope(FindFileByNamesInScopeParams params) {
        return CompletableFuture.completedFuture(new FindFileByNamesInScopeResponse(Collections.emptyList()));
      }

      @Override
      public void openUrlInBrowser(OpenUrlInBrowserParams params) {

      }

      @Override
      public HttpClient getHttpClient(String connectionId) {
        return sqHttpClient();
      }
    };
  }

}
