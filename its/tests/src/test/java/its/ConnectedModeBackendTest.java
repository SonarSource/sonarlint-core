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
import org.sonarsource.sonarlint.core.clientapi.backend.InitializeParams;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.GetActiveRuleDetailsParams;
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.client.SuggestBindingParams;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.clientapi.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams;
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse;
import org.sonarsource.sonarlint.core.clientapi.backend.rules.ActiveRuleDescriptionTabDto;
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
    backend.initialize(new InitializeParams("integrationTests", sonarUserHome.resolve("storage"), Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(), Set.of(Language.JAVA),
      Collections.emptySet(), null, false, List.of(new SonarQubeConnectionConfigurationDto("ORCHESTRATOR", ORCHESTRATOR.getServer().getUrl())), Collections.emptyList(), sonarUserHome.toString()));

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
        .containsOnly(
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
          "<p>The following code is vulnerable to path injection as it is constructing a path using untrusted data. This path is then used to delete a file\n" +
            "without being validated first. Therefore, it can be leveraged by an attacker to delete arbitrary files.</p>\n" +
            "<h4>Non-compliant code example</h4>\n" +
            "<pre data-diff-id=\"1\" data-diff-type=\"noncompliant\">\n" +
            "@Controller\n" +
            "public class ExampleController\n" +
            "{\n" +
            "    static private String targetDirectory = \"/path/to/target/directory/\";\n" +
            "\n" +
            "    @GetMapping(value = \"/delete\")\n" +
            "    public void delete(@RequestParam(\"filename\") String filename) throws IOException {\n" +
            "\n" +
            "        File file = new File(targetDirectory + filename);\n" +
            "        file.delete();\n" +
            "    }\n" +
            "}\n" +
            "</pre>\n" +
            "<h4>Compliant solution</h4>\n" +
            "<pre data-diff-id=\"1\" data-diff-type=\"compliant\">\n" +
            "@Controller\n" +
            "public class ExampleController\n" +
            "{\n" +
            "    static private String targetDirectory = \"/path/to/target/directory/\";\n" +
            "\n" +
            "    @GetMapping(value = \"/delete\")\n" +
            "    public void delete(@RequestParam(\"filename\") String filename) throws IOException {\n" +
            "\n" +
            "        File file = new File(targetDirectory + filename);\n" +
            "        String canonicalDestinationPath = file.getCanonicalPath();\n" +
            "\n" +
            "        if (!canonicalDestinationPath.startsWith(targetDirectory)) {\n" +
            "            throw new IOException(\"Entry is outside of the target directory\");\n" +
            "        }\n" +
            "\n" +
            "        file.delete();\n" +
            "    }\n" +
            "}\n" +
            "</pre>\n" +
            "<h3>How does this work?</h3>\n" +
            "<p>The universal way to prevent path injection is to validate paths constructed from untrusted data.</p>\n" +
            "<p>The validation should be done as follow:</p>\n" +
            "<ol>\n" +
            "  <li> Resolve the canonical path of the file by using methods like <code>java.io.File.getCanonicalPath</code>. This will resolve relative path or\n" +
            "  path components like <code>../</code> and removes any ambiguity regarding the file’s location. </li>\n" +
            "  <li> Check that the canonical path is within the directory where the file should be located. </li>\n" +
            "  <li> Ensure the target directory path ends with a forward slash to prevent partial path traversal, for example, <strong>/base/dirmalicious</strong>\n" +
            "  starts with <strong>/base/dir</strong> but does not start with <strong>/base/dir/</strong>. </li>\n" +
            "</ol>\n" +
            "<h3>Pitfalls</h3>\n" +
            "<h4>Partial Path Traversal</h4>\n" +
            "<p>When validating untrusted paths by checking if they start with a trusted folder name, <strong>ensure the validation string contains a path\n" +
            "separator as the last character</strong>.<br> A partial path traversal vulnerability can be unintentionally introduced into the application without a\n" +
            "path separator as the last character of the validation strings.</p>\n" +
            "<p>For example, the following code is vulnerable to partial path injection. Note that the string <code>targetDirectory</code> does not end with a path\n" +
            "separator:</p>\n" +
            "<pre>\n" +
            "static private String targetDirectory = \"/Users/John\";\n" +
            "\n" +
            "@GetMapping(value = \"/endpoint\")\n" +
            "public void endpoint(@RequestParam(\"folder\") fileName) throws IOException {\n" +
            "\n" +
            "    String canonicalizedFileName = fileName.getCanonicalPath();\n" +
            "\n" +
            "    if (!canonicalizedFileName .startsWith(targetDirectory)) {\n" +
            "        throw new IOException(\"Entry is outside of the target directory\");\n" +
            "    }\n" +
            "}\n" +
            "</pre>\n" +
            "<p>This check can be bypassed because <code>\"/Users/Johnny\".startsWith(\"/Users/John\")</code> returns <code>true</code>. Thus, for validation,\n" +
            "<code>\"/Users/John\"</code> should actually be <code>\"/Users/John/\"</code>.</p>\n" +
            "<p><strong>Warning</strong>: Some functions, such as <code>.getCanonicalPath</code>, remove the terminating path separator in their return value.<br>\n" +
            "The validation code should be tested to ensure that it cannot be impacted by this issue.</p>\n" +
            "<p><a href=\"https://github.com/aws/aws-sdk-java/security/advisories/GHSA-c28r-hw5m-5gv3\">Here is a real-life example of this vulnerability.</a></p>",
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
