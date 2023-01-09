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
import com.sonar.orchestrator.version.Version;
import its.utils.OrchestratorUtils;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

class ConnectedModeBackendTest extends AbstractConnectedTest {

  private static final String PROJECT_KEY_JAVA_TAINT = "sample-java-taint";
  private static final String PROJECT_KEY_JAVA_HOTSPOT = "sample-java-hotspot";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .setEdition(Edition.DEVELOPER)
    .activateLicense()
    .keepBundledPlugins()
    .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
    .build();

  @TempDir
  private static Path sonarUserHome;

  private SonarLintBackend backend;
  // still needed for the sync
  private ConnectedSonarLintEngine engine;

  @BeforeAll
  static void prepare() throws Exception {
    var adminWsClient = newAdminWsClient(ORCHESTRATOR);

    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));
    provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_TAINT, "Java With Taint Vulnerabilities");
    provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_HOTSPOT, "Java With Security Hotspots");

    // Build project to have bytecode and analyze
    analyzeMavenProject(ORCHESTRATOR, PROJECT_KEY_JAVA_TAINT, Map.of("sonar.projectKey", PROJECT_KEY_JAVA_TAINT));
    analyzeMavenProject(ORCHESTRATOR, PROJECT_KEY_JAVA_HOTSPOT, Map.of("sonar.projectKey", PROJECT_KEY_JAVA_HOTSPOT));
  }

  @BeforeEach
  void start() {
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
    updateProject(PROJECT_KEY_JAVA_HOTSPOT);
  }

  @AfterEach
  void stop() {
    backend.shutdown();
    engine.stop(true);
  }

  @Test
  void returnDescriptionSectionsForTaintRules() throws ExecutionException, InterruptedException {
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
      var extendedDescription = description.getRight();
      assertThat(extendedDescription.getIntroductionHtmlContent()).isNull();
      assertThat(extendedDescription.getTabs())
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

  @Test
  public void returnConvertedDescriptionSectionsForHotspotRules() throws ExecutionException, InterruptedException {
    assumeThat(ORCHESTRATOR.getServer().version()).isGreaterThanOrEqualTo(Version.create("9.7"));

    backend.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
      List.of(new ConfigurationScopeDto("project", null, true, "Project", new BindingConfigurationDto("ORCHESTRATOR", PROJECT_KEY_JAVA_HOTSPOT, false)))));

    var activeRuleDetailsResponse = backend.getActiveRulesService().getActiveRuleDetails(new GetActiveRuleDetailsParams("project", javaRuleKey(ORCHESTRATOR, "S4792"))).get();

    var extendedDescription = activeRuleDetailsResponse.details().getDescription().getRight();
    assertThat(extendedDescription.getIntroductionHtmlContent()).isNull();
    assertThat(extendedDescription.getTabs())
      .flatExtracting(ConnectedModeBackendTest::extractTabContent)
      .containsOnly(
        "Why is this an issue?",
        "<p>Configuring loggers is security-sensitive. It has led in the past to the following vulnerabilities:</p>\n" +
          "<ul>\n" +
          "  <li> <a href=\"http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2018-0285\">CVE-2018-0285</a> </li>\n" +
          "  <li> <a href=\"http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2000-1127\">CVE-2000-1127</a> </li>\n" +
          "  <li> <a href=\"http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2017-15113\">CVE-2017-15113</a> </li>\n" +
          "  <li> <a href=\"http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2015-5742\">CVE-2015-5742</a> </li>\n" +
          "</ul>\n" +
          "<p>Logs are useful before, during and after a security incident.</p>\n" +
          "<ul>\n" +
          "  <li> Attackers will most of the time start their nefarious work by probing the system for vulnerabilities. Monitoring this activity and stopping it\n" +
          "  is the first step to prevent an attack from ever happening. </li>\n" +
          "  <li> In case of a successful attack, logs should contain enough information to understand what damage an attacker may have inflicted. </li>\n" +
          "</ul>\n" +
          "<p>Logs are also a target for attackers because they might contain sensitive information. Configuring loggers has an impact on the type of information\n" +
          "logged and how they are logged.</p>\n" +
          "<p>This rule flags for review code that initiates loggers configuration. The goal is to guide security code reviews.</p>\n" +
          "<h2>Exceptions</h2>\n" +
          "<p>Log4J 1.x is not covered as it has reached <a href=\"https://blogs.apache.org/foundation/entry/apache_logging_services_project_announces\">end of\n" +
          "life</a>.</p>\n",
        "Assess the risk",
        "<h2>Ask Yourself Whether</h2>\n" +
          "<ul>\n" +
          "  <li> unauthorized users might have access to the logs, either because they are stored in an insecure location or because the application gives\n" +
          "  access to them. </li>\n" +
          "  <li> the logs contain sensitive information on a production server. This can happen when the logger is in debug mode. </li>\n" +
          "  <li> the log can grow without limit. This can happen when additional information is written into logs every time a user performs an action and the\n" +
          "  user can perform the action as many times as he/she wants. </li>\n" +
          "  <li> the logs do not contain enough information to understand the damage an attacker might have inflicted. The loggers mode (info, warn, error)\n" +
          "  might filter out important information. They might not print contextual information like the precise time of events or the server hostname. </li>\n" +
          "  <li> the logs are only stored locally instead of being backuped or replicated. </li>\n" +
          "</ul>\n" +
          "<p>There is a risk if you answered yes to any of those questions.</p>\n" +
          "<h2>Sensitive Code Example</h2>\n" +
          "<p>This rule supports the following libraries: Log4J, <code>java.util.logging</code> and Logback</p>\n" +
          "<pre>\n" +
          "// === Log4J 2 ===\n" +
          "import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;\n" +
          "import org.apache.logging.log4j.Level;\n" +
          "import org.apache.logging.log4j.core.*;\n" +
          "import org.apache.logging.log4j.core.config.*;\n" +
          "\n" +
          "// Sensitive: creating a new custom configuration\n" +
          "abstract class CustomConfigFactory extends ConfigurationFactory {\n" +
          "    // ...\n" +
          "}\n" +
          "\n" +
          "class A {\n" +
          "    void foo(Configuration config, LoggerContext context, java.util.Map&lt;String, Level&gt; levelMap,\n" +
          "            Appender appender, java.io.InputStream stream, java.net.URI uri,\n" +
          "            java.io.File file, java.net.URL url, String source, ClassLoader loader, Level level, Filter filter)\n" +
          "            throws java.io.IOException {\n" +
          "        // Creating a new custom configuration\n" +
          "        ConfigurationBuilderFactory.newConfigurationBuilder();  // Sensitive\n" +
          "\n" +
          "        // Setting loggers level can result in writing sensitive information in production\n" +
          "        Configurator.setAllLevels(\"com.example\", Level.DEBUG);  // Sensitive\n" +
          "        Configurator.setLevel(\"com.example\", Level.DEBUG);  // Sensitive\n" +
          "        Configurator.setLevel(levelMap);  // Sensitive\n" +
          "        Configurator.setRootLevel(Level.DEBUG);  // Sensitive\n" +
          "\n" +
          "        config.addAppender(appender); // Sensitive: this modifies the configuration\n" +
          "\n" +
          "        LoggerConfig loggerConfig = config.getRootLogger();\n" +
          "        loggerConfig.addAppender(appender, level, filter); // Sensitive\n" +
          "        loggerConfig.setLevel(level); // Sensitive\n" +
          "\n" +
          "        context.setConfigLocation(uri); // Sensitive\n" +
          "\n" +
          "        // Load the configuration from a stream or file\n" +
          "        new ConfigurationSource(stream);  // Sensitive\n" +
          "        new ConfigurationSource(stream, file);  // Sensitive\n" +
          "        new ConfigurationSource(stream, url);  // Sensitive\n" +
          "        ConfigurationSource.fromResource(source, loader);  // Sensitive\n" +
          "        ConfigurationSource.fromUri(uri);  // Sensitive\n" +
          "    }\n" +
          "}\n" +
          "</pre>\n" +
          "<pre>\n" +
          "// === java.util.logging ===\n" +
          "import java.util.logging.*;\n" +
          "\n" +
          "class M {\n" +
          "    void foo(LogManager logManager, Logger logger, java.io.InputStream is, Handler handler)\n" +
          "            throws SecurityException, java.io.IOException {\n" +
          "        logManager.readConfiguration(is); // Sensitive\n" +
          "\n" +
          "        logger.setLevel(Level.FINEST); // Sensitive\n" +
          "        logger.addHandler(handler); // Sensitive\n" +
          "    }\n" +
          "}\n" +
          "</pre>\n" +
          "<pre>\n" +
          "// === Logback ===\n" +
          "import ch.qos.logback.classic.util.ContextInitializer;\n" +
          "import ch.qos.logback.core.Appender;\n" +
          "import ch.qos.logback.classic.joran.JoranConfigurator;\n" +
          "import ch.qos.logback.classic.spi.ILoggingEvent;\n" +
          "import ch.qos.logback.classic.*;\n" +
          "\n" +
          "class M {\n" +
          "    void foo(Logger logger, Appender&lt;ILoggingEvent&gt; fileAppender) {\n" +
          "        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, \"config.xml\"); // Sensitive\n" +
          "        JoranConfigurator configurator = new JoranConfigurator(); // Sensitive\n" +
          "\n" +
          "        logger.addAppender(fileAppender); // Sensitive\n" +
          "        logger.setLevel(Level.DEBUG); // Sensitive\n" +
          "    }\n" +
          "}\n" +
          "</pre>\n",
        "How can I fix it?",
        "<h2>Recommended Secure Coding Practices</h2>\n" +
          "<ul>\n" +
          "  <li> Check that your production deployment doesn’t have its loggers in \"debug\" mode as it might write sensitive information in logs. </li>\n" +
          "  <li> Production logs should be stored in a secure location which is only accessible to system administrators. </li>\n" +
          "  <li> Configure the loggers to display all warnings, info and error messages. Write relevant information such as the precise time of events and the\n" +
          "  hostname. </li>\n" +
          "  <li> Choose log format which is easy to parse and process automatically. It is important to process logs rapidly in case of an attack so that the\n" +
          "  impact is known and limited. </li>\n" +
          "  <li> Check that the permissions of the log files are correct. If you index the logs in some other service, make sure that the transfer and the\n" +
          "  service are secure too. </li>\n" +
          "  <li> Add limits to the size of the logs and make sure that no user can fill the disk with logs. This can happen even when the user does not control\n" +
          "  the logged information. An attacker could just repeat a logged action many times. </li>\n" +
          "</ul>\n" +
          "<p>Remember that configuring loggers properly doesn’t make them bullet-proof. Here is a list of recommendations explaining on how to use your\n" +
          "logs:</p>\n" +
          "<ul>\n" +
          "  <li> Don’t log any sensitive information. This obviously includes passwords and credit card numbers but also any personal information such as user\n" +
          "  names, locations, etc…\u200B Usually any information which is protected by law is good candidate for removal. </li>\n" +
          "  <li> Sanitize all user inputs before writing them in the logs. This includes checking its size, content, encoding, syntax, etc…\u200B As for any user\n" +
          "  input, validate using whitelists whenever possible. Enabling users to write what they want in your logs can have many impacts. It could for example\n" +
          "  use all your storage space or compromise your log indexing service. </li>\n" +
          "  <li> Log enough information to monitor suspicious activities and evaluate the impact an attacker might have on your systems. Register events such as\n" +
          "  failed logins, successful logins, server side input validation failures, access denials and any important transaction. </li>\n" +
          "  <li> Monitor the logs for any suspicious activity. </li>\n" +
          "</ul>\n" +
          "<h2>See</h2>\n" +
          "<ul>\n" +
          "  <li> <a href=\"https://owasp.org/Top10/A09_2021-Security_Logging_and_Monitoring_Failures/\">OWASP Top 10 2021 Category A9</a> - Security Logging and\n" +
          "  Monitoring Failures </li>\n" +
          "  <li> <a href=\"https://www.owasp.org/www-project-top-ten/2017/A3_2017-Sensitive_Data_Exposure\">OWASP Top 10 2017 Category A3</a> - Sensitive Data\n" +
          "  Exposure </li>\n" +
          "  <li> <a href=\"https://owasp.org/www-project-top-ten/2017/A10_2017-Insufficient_Logging%2526Monitoring\">OWASP Top 10 2017 Category A10</a> -\n" +
          "  Insufficient Logging &amp; Monitoring </li>\n" +
          "  <li> <a href=\"https://cwe.mitre.org/data/definitions/117\">MITRE, CWE-117</a> - Improper Output Neutralization for Logs </li>\n" +
          "  <li> <a href=\"https://cwe.mitre.org/data/definitions/532\">MITRE, CWE-532</a> - Information Exposure Through Log Files </li>\n" +
          "  <li> <a href=\"https://www.sans.org/top25-software-errors/#cat3\">SANS Top 25</a> - Porous Defenses </li>\n" +
          "</ul>"
    );
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
