/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2025 SonarSource SA
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.http.HttpMethod;
import its.utils.LogOnTestFailure;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;
import org.apache.commons.codec.digest.DigestUtils;
import org.assertj.core.internal.Failures;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarqube.ws.Issues;
import org.sonarqube.ws.Qualityprofiles.SearchWsResponse.QualityProfile;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.qualityprofiles.SearchRequest;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractConnectedTests {
  public static final TelemetryClientConstantAttributesDto IT_TELEMETRY_ATTRIBUTES = new TelemetryClientConstantAttributesDto("SonarLint ITs", "SonarLint ITs",
    "1.2.3", "4.5.6", Collections.emptyMap());
  protected static final Queue<LogParams> rpcClientLogs = new ConcurrentLinkedQueue<>();

  @RegisterExtension
  static LogOnTestFailure logOnTestFailure = new LogOnTestFailure(rpcClientLogs);

  public static final ClientConstantInfoDto IT_CLIENT_INFO = new ClientConstantInfoDto("clientName", "integrationTests");
  protected static final String SONARLINT_USER = "sonarlint";
  protected static final String SONARLINT_PWD = "sonarlintpwd";
  protected static final String MAIN_BRANCH_NAME = "master";

  protected static WsClient newAdminWsClient(Orchestrator orchestrator) {
    var server = orchestrator.getServer();
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(server.getUrl())
      .credentials(com.sonar.orchestrator.container.Server.ADMIN_LOGIN, com.sonar.orchestrator.container.Server.ADMIN_PASSWORD)
      .build());
  }

  static Map<String, String> toMap(String[] keyValues) {
    Preconditions.checkArgument(keyValues.length % 2 == 0, "Must be an even number of key/values");
    Map<String, String> map = Maps.newHashMap();
    var index = 0;
    while (index < keyValues.length) {
      var key = keyValues[index++];
      var value = keyValues[index++];
      map.put(key, value);
    }
    return map;
  }

  private static final Pattern MATCH_ALL_WHITESPACES = Pattern.compile("\\s");

  protected static String hash(String codeSnippet) {
    String codeSnippetWithoutWhitespaces = MATCH_ALL_WHITESPACES.matcher(codeSnippet).replaceAll("");
    return DigestUtils.md5Hex(codeSnippetWithoutWhitespaces);
  }

  protected static void analyzeMavenProject(Orchestrator orchestrator, String projectDirName) {
    analyzeMavenProject(orchestrator, projectDirName, Map.of());
  }

  protected static void analyzeMavenProject(Orchestrator orchestrator, String projectDirName, Map<String, String> extraProperties) {
    var projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    var pom = projectDir.resolve("pom.xml");
    var mavenBuild = MavenBuild.create(pom.toFile())
      .setCleanPackageSonarGoals()
      .setProperties(extraProperties);

    if (orchestrator.getServer().version().isGreaterThanOrEquals(10, 2)) {
      mavenBuild
        .setProperty("sonar.token", orchestrator.getDefaultAdminToken())
        .setProperties(extraProperties);
    } else {
      // sonar.token is not supported for 9.9
      mavenBuild
        .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
        .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD);
    }

    orchestrator.executeBuild(mavenBuild);
  }

  protected QualityProfile getQualityProfile(WsClient adminWsClient, String qualityProfileName) {
    var searchReq = new SearchRequest();
    searchReq.setQualityProfile(qualityProfileName);
    searchReq.setDefaults("false");
    var search = adminWsClient.qualityprofiles().search(searchReq);
    for (QualityProfile profile : search.getProfilesList()) {
      if (profile.getName().equals(qualityProfileName)) {
        return profile;
      }
    }
    throw Failures.instance().failure("Unable to get quality profile " + qualityProfileName);
  }

  protected void deactivateRule(WsClient adminWsClient, QualityProfile qualityProfile, String ruleKey) {
    var request = new PostRequest("/api/qualityprofiles/deactivate_rule")
      .setParam("key", qualityProfile.getKey())
      .setParam("rule", ruleKey);
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertTrue(response.isSuccessful(), "Unable to deactivate rule");
    }
  }

  protected static List<String> getIssueKeys(WsClient adminWsClient, String ruleKey) {
    var searchReq = new org.sonarqube.ws.client.issues.SearchRequest();
    searchReq.setRules(List.of(ruleKey));
    var response = adminWsClient.issues().search(searchReq);
    return response.getIssuesList().stream().map(Issues.Issue::getKey).toList();
  }

  protected static void resolveIssueAsWontFix(WsClient adminWsClient, String issueKey) {
    changeIssueStatus(adminWsClient, issueKey, "wontfix");
  }

  protected static void reopenIssue(WsClient adminWsClient, String issueKey) {
    changeIssueStatus(adminWsClient, issueKey, "reopen");
  }

  protected static void changeIssueStatus(WsClient adminWsClient, String issueKey, String status) {
    var request = new PostRequest("/api/issues/do_transition")
      .setParam("issue", issueKey)
      .setParam("transition", status);
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertTrue(response.isSuccessful(), "Unable to resolve issue");
    }
  }

  protected static void resolveHotspotAsSafe(WsClient adminWsClient, String hotspotKey) {
    var request = new PostRequest("/api/hotspots/change_status")
      .setParam("hotspot", hotspotKey)
      .setParam("status", "REVIEWED")
      .setParam("resolution", "SAFE");
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertTrue(response.isSuccessful(), "Unable to resolve hotspot");
    }
  }

  protected static void provisionProject(Orchestrator orchestrator, String projectKey, String projectName) {
    orchestrator.getServer()
      .newHttpCall("/api/projects/create")
      .setMethod(HttpMethod.POST)
      .setAdminCredentials()
      .setParam("project", projectKey)
      .setParam("name", projectName)
      .setParam("mainBranch", MAIN_BRANCH_NAME)
      .execute();
  }
}
