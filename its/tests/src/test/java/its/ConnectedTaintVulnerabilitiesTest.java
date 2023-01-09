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
/*


Optimisations:
  - allow to fetch taint vulnerabilities separately: new parameter repoKeys=javasecurity,pythonsecurity,...
  - only fetch secondary locations for hotspots and taint vulnerabilities: new parameter  * SonarLint Core - ITs - Tests
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
import com.sonar.orchestrator.locator.FileLocation;
import its.utils.OrchestratorUtils;
import java.nio.file.Path;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.issues.SearchRequest;
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
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ConnectedTaintVulnerabilitiesTest extends AbstractConnectedTest {
  private static final String PROJECT_KEY_JAVA = "sample-java";
  private static final String PROJECT_KEY_JAVA_TAINT = "sample-java-taint";

  @RegisterExtension
  static OrchestratorExtension ORCHESTRATOR = OrchestratorUtils.defaultEnvBuilder()
    .setEdition(Edition.DEVELOPER)
    .activateLicense()
    .keepBundledPlugins()
    .restoreProfileAtStartup(FileLocation.ofClasspath("/java-sonarlint-with-taint.xml"))
    // Ensure SSE are processed correctly just after SQ startup
    .setServerProperty("sonar.pushevents.polling.initial.delay", "2")
    .setServerProperty("sonar.pushevents.polling.period", "1")
    .setServerProperty("sonar.pushevents.polling.last.timestamp", "1")
    .setServerProperty("sonar.projectCreation.mainBranchName", MAIN_BRANCH_NAME)
    .build();

  private ConnectedSonarLintEngine engine;

  private static WsClient adminWsClient;

  @BeforeAll
  static void prepareProject() throws Exception {
    adminWsClient = newAdminWsClient(ORCHESTRATOR);
    adminWsClient.users().create(new CreateRequest().setLogin(SONARLINT_USER).setPassword(SONARLINT_PWD).setName("SonarLint"));
  }

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
