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
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.container.Edition;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.MavenLocation;
import its.utils.OrchestratorUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.Issues.Issue;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.issues.DoTransitionRequest;
import org.sonarqube.ws.client.issues.SearchRequest;
import org.sonarqube.ws.client.issues.SetSeverityRequest;
import org.sonarqube.ws.client.issues.SetTypeRequest;
import org.sonarqube.ws.client.users.CreateRequest;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverapi.push.IssueChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.ServerEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityClosedEvent;
import org.sonarsource.sonarlint.core.serverapi.push.TaintVulnerabilityRaisedEvent;
import org.sonarsource.sonarlint.core.serverconnection.ProjectBinding;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

import static its.utils.ItUtils.SONAR_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SonarQubeDeveloperEditionTests extends AbstractConnectedTests {
  // Use the pattern of long living branches in SQ 7.9, else we only have issues on changed files
  private static final String SHORT_BRANCH = "feature/short_living";
  private static final String LONG_BRANCH = "branch-1.x";

  private static final String PROJECT_KEY = "sample-xoo";

  private static final String PROJECT_KEY_JAVA = "sample-java";
  private static final String PROJECT_KEY_JAVA_TAINT = "sample-java-taint";


  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .setEdition(Edition.DEVELOPER)
    .activateLicense()
    .keepBundledPlugins()
    .addPlugin(MavenLocation.of("org.sonarsource.sonarqube", "sonar-xoo-plugin", SONAR_VERSION))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/xoo-sonarlint.xml"))
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-with-taint.xml"))
    // Ensure SSE are processed correctly just after SQ startup
    .setServerProperty("sonar.pushevents.polling.initial.delay", "2")
    .setServerProperty("sonar.pushevents.polling.period", "1")
    .setServerProperty("sonar.pushevents.polling.last.timestamp", "1")
    .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
    .build();

  private static ConnectedSonarLintEngine engine;

  private static Issue wfIssue;
  private static Issue fpIssue;
  private static Issue overridenSeverityIssue;
  private static Issue overridenTypeIssue;

  @TempDir
  private static Path sonarUserHome;

  private static WsClient adminWsClient;

  @BeforeAll
  static void prepareUser() throws Exception {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));
  }


  @Nested
  // TODO Can be removed when switching to Java 16+ and changing prepare() to static
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class BranchTests {

    @BeforeAll
    void prepare() throws IOException {
      engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId("orchestrator")
        .setSonarLintUserHome(sonarUserHome)
        .setExtraProperties(new HashMap<>())
        .build());

      provisionProject(ORCHESTRATOR, PROJECT_KEY, "Sample Xoo");
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY, "xoo", "SonarLint IT Xoo");

      engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY, null);

      // main branch
      analyzeProject("sample-xoo-v1");

      // short living branch
      analyzeProject("sample-xoo-v1", "sonar.branch.name", SHORT_BRANCH);

      // long living branch
      analyzeProject("sample-xoo-v1", "sonar.branch.name", LONG_BRANCH);
      // Second analysis with less issues to have closed issues on the branch
      analyzeProject("sample-xoo-v2", "sonar.branch.name", LONG_BRANCH);

      // Mark a few issues as closed WF and closed FP on the branch
      var issueSearchResponse = adminWsClient.issues()
        .search(new SearchRequest().setStatuses(List.of("OPEN")).setTypes(List.of("CODE_SMELL")).setComponentKeys(List.of(PROJECT_KEY)).setBranch(LONG_BRANCH));
      wfIssue = issueSearchResponse.getIssues(0);
      fpIssue = issueSearchResponse.getIssues(1);
      // Change severity and type
      overridenSeverityIssue = issueSearchResponse.getIssues(2);
      overridenTypeIssue = issueSearchResponse.getIssues(3);

      adminWsClient.issues().doTransition(new DoTransitionRequest().setIssue(wfIssue.getKey()).setTransition("wontfix"));
      adminWsClient.issues().doTransition(new DoTransitionRequest().setIssue(fpIssue.getKey()).setTransition("falsepositive"));

      adminWsClient.issues().setSeverity(new SetSeverityRequest().setIssue(overridenSeverityIssue.getKey()).setSeverity("BLOCKER"));
      adminWsClient.issues().setType(new SetTypeRequest().setIssue(overridenTypeIssue.getKey()).setType("BUG"));

      // Ensure an hostpot has been reported on server side
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 2)) {
        assertThat(adminWsClient.hotspots().search(new org.sonarqube.ws.client.hotspots.SearchRequest().setProjectKey(PROJECT_KEY).setBranch(LONG_BRANCH)).getHotspotsList())
          .isNotEmpty();
      } else {
        assertThat(
          adminWsClient.issues().search(new SearchRequest().setTypes(List.of("SECURITY_HOTSPOT")).setComponentKeys(List.of(PROJECT_KEY))).getIssuesList())
          .isNotEmpty();
      }
    }

    @AfterAll
    public void stop() {
      engine.stop(true);
    }

    @Test
    void sync_all_project_branches() {
      engine.sync(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(PROJECT_KEY), null);

      // Starting from SQ 8.1, concept of short vs long living branch has been removed
      if (ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(8, 1)) {
        assertThat(engine.getServerBranches(PROJECT_KEY).getBranchNames()).containsOnly(MAIN_BRANCH_NAME, LONG_BRANCH, SHORT_BRANCH);
      } else {
        assertThat(engine.getServerBranches(PROJECT_KEY).getBranchNames()).containsOnly(MAIN_BRANCH_NAME, LONG_BRANCH);
      }
      assertThat(engine.getServerBranches(PROJECT_KEY).getMainBranchName()).isEqualTo(MAIN_BRANCH_NAME);
    }

    @Test
    void download_all_issues_for_branch() {
      engine.downloadAllServerIssues(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY, LONG_BRANCH, null);

      var file1Issues = engine.getServerIssues(new ProjectBinding(PROJECT_KEY, "", ""), LONG_BRANCH, "src/500lines.xoo");
      var file2Issues = engine.getServerIssues(new ProjectBinding(PROJECT_KEY, "", ""), LONG_BRANCH, "src/10000lines.xoo");

      // Number of issues is not limited to 10k
      assertThat(file1Issues.size() + file2Issues.size()).isEqualTo(10_500);

      Map<String, ServerIssue> allIssues = new HashMap<>();
      engine.getServerIssues(new ProjectBinding(PROJECT_KEY, "", ""), LONG_BRANCH, "src/500lines.xoo").forEach(i -> allIssues.put(i.getKey(), i));
      engine.getServerIssues(new ProjectBinding(PROJECT_KEY, "", ""), LONG_BRANCH, "src/10000lines.xoo").forEach(i -> allIssues.put(i.getKey(), i));

      assertThat(allIssues).hasSize(10_500);
      assertThat(allIssues.get(wfIssue.getKey()).isResolved()).isTrue();
      assertThat(allIssues.get(fpIssue.getKey()).isResolved()).isTrue();
      assertThat(allIssues.get(overridenSeverityIssue.getKey()).getUserSeverity()).isEqualTo(IssueSeverity.BLOCKER);
      assertThat(allIssues.get(overridenTypeIssue.getKey()).getType()).isEqualTo(RuleType.BUG);

      // No hotspots
      assertThat(allIssues.values()).allSatisfy(i -> assertThat(i.getType()).isIn(RuleType.CODE_SMELL, RuleType.BUG, RuleType.VULNERABILITY));
    }
  }

  @Nested
  class TaintVulnerabilities {

    @BeforeEach
    void prepare(@TempDir Path sonarUserHome) throws Exception {
      provisionProject(ORCHESTRATOR, PROJECT_KEY_JAVA_TAINT, "Java With Taint Vulnerabilities");
      ORCHESTRATOR.getServer().associateProjectToQualityProfile(PROJECT_KEY_JAVA_TAINT, "java", "SonarLint Taint Java");

      engine = new ConnectedSonarLintEngineImpl(ConnectedGlobalConfiguration.sonarQubeBuilder()
        .setConnectionId("orchestrator")
        .addEnabledLanguage(Language.JAVA)
        .setSonarLintUserHome(sonarUserHome)
        .setLogOutput((msg, level) -> System.out.println(msg))
        .setExtraProperties(new HashMap<>())
        .build());
    }

    @AfterEach
    void stop() {
      engine.stop(true);
      var request = new PostRequest("api/projects/bulk_delete");
      request.setParam("projects", PROJECT_KEY_JAVA_TAINT);
      try (var response = adminWsClient.wsConnector().call(request)) {
      }

    }

    @Test
    void download_taint_vulnerabilities() {
      analyzeMavenProject(ORCHESTRATOR, PROJECT_KEY_JAVA_TAINT, Map.of("sonar.projectKey", PROJECT_KEY_JAVA_TAINT));

      // Ensure a vulnerability has been reported on server side
      var issuesList = adminWsClient.issues().search(new SearchRequest().setTypes(List.of("VULNERABILITY")).setComponentKeys(List.of(PROJECT_KEY_JAVA_TAINT))).getIssuesList();
      assertThat(issuesList).hasSize(1);

      ProjectBinding projectBinding = new ProjectBinding(PROJECT_KEY_JAVA_TAINT, "", "");

      engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY_JAVA_TAINT, null);

      // For SQ 9.6+
      engine.syncServerTaintIssues(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY_JAVA_TAINT, MAIN_BRANCH_NAME, null);
      // For SQ < 9.6
      engine.downloadAllServerTaintIssuesForFile(endpointParams(ORCHESTRATOR), sqHttpClient(), projectBinding, "src/main/java/foo/DbHelper.java", MAIN_BRANCH_NAME, null);

      var sinkIssues = engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java");

      assertThat(sinkIssues).hasSize(1);

      var taintIssue = sinkIssues.get(0);
      assertThat(taintIssue.getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));

      assertThat(taintIssue.getSeverity()).isEqualTo(IssueSeverity.MAJOR);

      assertThat(taintIssue.getType()).isEqualTo(RuleType.VULNERABILITY);
      assertThat(taintIssue.getFlows()).isNotEmpty();
      var flow = taintIssue.getFlows().get(0);
      assertThat(flow.locations()).isNotEmpty();
      assertThat(flow.locations().get(0).getTextRange().getHash()).isEqualTo(hash("statement.executeQuery(query)"));
      assertThat(flow.locations().get(flow.locations().size() - 1).getTextRange().getHash()).isIn(hash("request.getParameter(\"user\")"), hash("request.getParameter(\"pass\")"));

      var allTaintIssues = engine.getAllServerTaintIssues(projectBinding, "master");
      assertThat(allTaintIssues)
        .hasSize(1)
        .extracting(ServerTaintIssue::getFilePath)
        .containsExactly("src/main/java/foo/DbHelper.java");
    }

    @Test
    void updatesStorageTaintVulnerabilityEvents() {
      assumeTrue(ORCHESTRATOR.getServer().version().isGreaterThanOrEquals(9, 6));

      engine.updateProject(endpointParams(ORCHESTRATOR), sqHttpClient(), PROJECT_KEY_JAVA_TAINT, null);
      Deque<ServerEvent> events = new ConcurrentLinkedDeque<>();
      engine.subscribeForEvents(endpointParams(ORCHESTRATOR), sqHttpClient(), Set.of(PROJECT_KEY_JAVA_TAINT), events::add, null);
      var projectBinding = new ProjectBinding(PROJECT_KEY_JAVA_TAINT, "", "");
      assertThat(engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java")).isEmpty();

      // check TaintVulnerabilityRaised is received
      analyzeMavenProject(ORCHESTRATOR, PROJECT_KEY_JAVA_TAINT, Map.of("sonar.projectKey", PROJECT_KEY_JAVA_TAINT));

      waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
        assertThat(events).isNotEmpty();
        assertThat(events.getLast())
          .isInstanceOfSatisfying(TaintVulnerabilityRaisedEvent.class, e -> {
            assertThat(e.getRuleKey()).isEqualTo("javasecurity:S3649");
            assertThat(e.getProjectKey()).isEqualTo(PROJECT_KEY_JAVA_TAINT);
          });
      });

      var issues = getIssueKeys(adminWsClient, "javasecurity:S3649");
      assertThat(issues).isNotEmpty();
      var issueKey = issues.get(0);

      var taintIssues = engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java");
      assertThat(taintIssues)
        .extracting("key", "resolved", "ruleKey", "message", "filePath", "severity", "type")
        .containsOnly(
          tuple(issueKey, false, "javasecurity:S3649", "Change this code to not construct SQL queries directly from user-controlled data.", "src/main/java/foo/DbHelper.java",
            IssueSeverity.MAJOR, RuleType.VULNERABILITY));
      assertThat(taintIssues)
        .extracting("textRange")
        .extracting("startLine", "startLineOffset", "endLine", "endLineOffset", "hash")
        .containsOnly(tuple(11, 35, 11, 64, "d123d615e9ea7cc7e78c784c768f2941"));
      assertThat(taintIssues)
        .flatExtracting("flows")
        .flatExtracting("locations")
        .extracting("message", "filePath", "textRange.startLine", "textRange.startLineOffset", "textRange.endLine", "textRange.endLineOffset", "textRange.hash")
        .contains(
          // flow 1 (don't assert intermediate locations as they change frequently between versions)
          tuple("Sink: this invocation is not safe; a malicious value can be used as argument", "src/main/java/foo/DbHelper.java", 11, 35, 11, 64, "d123d615e9ea7cc7e78c784c768f2941"),
          tuple("Source: a user can craft an HTTP request with malicious content", "src/main/java/foo/Endpoint.java", 9, 18, 9, 46, "a2b69949119440a24e900f15c0939c30"),
          // flow 2 (don't assert intermediate locations as they change frequently between versions)
          tuple("Sink: this invocation is not safe; a malicious value can be used as argument", "src/main/java/foo/DbHelper.java", 11, 35, 11, 64, "d123d615e9ea7cc7e78c784c768f2941"),
          tuple("Source: a user can craft an HTTP request with malicious content", "src/main/java/foo/Endpoint.java", 8, 18, 8, 46, "2ef54227b849e317e7104dc550be8146"));

      // check IssueChangedEvent is received
      resolveIssueAsWontFix(adminWsClient, issueKey);
      waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
        assertThat(events).isNotEmpty();
        assertThat(events.getLast())
          .isInstanceOfSatisfying(IssueChangedEvent.class, e -> {
            assertThat(e.getImpactedIssueKeys()).containsOnly(issueKey);
            assertThat(e.getResolved()).isTrue();
            assertThat(e.getProjectKey()).isEqualTo(PROJECT_KEY_JAVA_TAINT);
          });
      });

      taintIssues = engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java");
      assertThat(taintIssues).isEmpty();

      // check IssueChangedEvent is received
      reopenIssue(adminWsClient, issueKey);
      waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
        assertThat(events).isNotEmpty();
        assertThat(events.getLast())
          .isInstanceOfSatisfying(IssueChangedEvent.class, e -> {
            assertThat(e.getImpactedIssueKeys()).containsOnly(issueKey);
            assertThat(e.getResolved()).isFalse();
            assertThat(e.getProjectKey()).isEqualTo(PROJECT_KEY_JAVA_TAINT);
          });
      });
      taintIssues = engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java");
      assertThat(taintIssues).isNotEmpty();

      // analyze another project under the same project key to close the taint issue
      analyzeMavenProject(ORCHESTRATOR, PROJECT_KEY_JAVA, Map.of("sonar.projectKey", PROJECT_KEY_JAVA_TAINT));

      // check TaintVulnerabilityClosed is received
      waitAtMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
        assertThat(events).isNotEmpty();
        assertThat(events.getLast())
          .isInstanceOfSatisfying(TaintVulnerabilityClosedEvent.class, e -> {
            assertThat(e.getTaintIssueKey()).isEqualTo(issueKey);
            assertThat(e.getProjectKey()).isEqualTo(PROJECT_KEY_JAVA_TAINT);
          });
      });
      taintIssues = engine.getServerTaintIssues(projectBinding, MAIN_BRANCH_NAME, "src/main/java/foo/DbHelper.java");
      assertThat(taintIssues).isEmpty();
    }
  }

  private static void analyzeProject(String projectDirName, String... properties) {
    var projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    ORCHESTRATOR.executeBuild(SonarScanner.create(projectDir.toFile())
      .setProjectKey(PROJECT_KEY)
      .setSourceDirs("src")
      .setProperties(properties)
      .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
      .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD));
  }
}
