/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2023 SonarSource SA
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
package mediumtest.fixtures;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.protobuf.Message;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import org.sonar.scanner.protocol.Constants;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Measures;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;
import static testutils.TestUtils.protobufBody;
import static testutils.TestUtils.protobufBodyDelimited;

public class ServerFixture {
  public static ServerBuilder newSonarQubeServer() {
    return newSonarQubeServer("99.9");
  }

  public static ServerBuilder newSonarQubeServer(String version) {
    return new ServerBuilder(ServerKind.SONARQUBE, version);
  }

  public static ServerBuilder newSonarCloudServer() {
    return new ServerBuilder(ServerKind.SONARQUBE, null);
  }

  private enum ServerKind {
    SONARQUBE, SONARCLOUD
  }

  public enum ServerStatus {
    UP, DOWN
  }

  public static class ServerBuilder {
    private final ServerKind serverKind;
    private final String version;
    private final Map<String, ServerProjectBuilder> projectByProjectKey = new HashMap<>();
    private final Map<String, ServerSourceFileBuilder> sourceFileByComponentKey = new HashMap<>();
    private ServerStatus serverStatus = ServerStatus.UP;
    private boolean smartNotificationsSupported;

    public ServerBuilder(ServerKind serverKind, @Nullable String version) {
      this.serverKind = serverKind;
      this.version = version;
    }

    public ServerBuilder withStatus(ServerStatus status) {
      serverStatus = status;
      return this;
    }

    public ServerBuilder withProject(String projectKey, UnaryOperator<ServerProjectBuilder> projectBuilder) {
      var builder = new ServerProjectBuilder();
      this.projectByProjectKey.put(projectKey, projectBuilder.apply(builder));
      return this;
    }

    public ServerBuilder withSourceFile(String componentKey, UnaryOperator<ServerSourceFileBuilder> sourceFileBuilder) {
      var builder = new ServerSourceFileBuilder();
      this.sourceFileByComponentKey.put(componentKey, sourceFileBuilder.apply(builder));
      return this;
    }

    public ServerBuilder withSmartNotificationsSupported(boolean smartNotificationsSupported) {
      this.smartNotificationsSupported = smartNotificationsSupported;
      return this;
    }

    public Server start() {
      var server = new Server(serverKind, serverStatus, version, projectByProjectKey, sourceFileByComponentKey, smartNotificationsSupported);
      server.start();
      return server;
    }

    public static class ServerProjectBuilder {
      private final Map<String, ServerProjectBranchBuilder> branchesByName = new HashMap<>();

      public ServerProjectBuilder withEmptyBranch(String branchName) {
        var builder = new ServerProjectBranchBuilder();
        this.branchesByName.put(branchName, builder);
        return this;
      }

      public ServerProjectBuilder withBranch(String branchName, UnaryOperator<ServerProjectBranchBuilder> branchBuilder) {
        var builder = new ServerProjectBranchBuilder();
        this.branchesByName.put(branchName, branchBuilder.apply(builder));
        return this;
      }

      public ServerProjectBuilder withDefaultBranch(UnaryOperator<ServerProjectBranchBuilder> branchBuilder) {
        return withBranch(null, branchBuilder);
      }
    }

    public static class ServerProjectBranchBuilder {
      private final Collection<ServerHotspot> hotspots = new ArrayList<>();
      private final Collection<ServerIssue> issues = new ArrayList<>();

      public ServerProjectBranchBuilder withHotspot(String hotspotKey) {
        return withHotspot(hotspotKey, UnaryOperator.identity());
      }

      public ServerProjectBranchBuilder withHotspot(String hotspotKey, UnaryOperator<HotspotBuilder> hotspotBuilder) {
        var builder = new HotspotBuilder();
        hotspotBuilder.apply(builder);
        this.hotspots.add(builder.build(hotspotKey));
        return this;
      }

      public ServerProjectBranchBuilder withIssue(String issueKey, String ruleKey, String message, String author, String filePath,
        String status, String resolution, String creationDate, TextRange textRange) {
        this.issues.add(new ServerIssue(issueKey, ruleKey, message, author, filePath, status, resolution, creationDate, textRange));
        return this;
      }

      private static class ServerHotspot {
        private final String hotspotKey;
        private final String ruleKey;
        private final String message;
        private final String author;
        private final String filePath;
        private final HotspotReviewStatus status;
        private final TextRange textRange;
        private final boolean canChangeStatus;
        private final Instant creationDate;
        private final VulnerabilityProbability vulnerabilityProbability;

        private ServerHotspot(String hotspotKey, String ruleKey, String message, String author, String filePath, HotspotReviewStatus status, TextRange textRange,
          boolean canChangeStatus, Instant creationDate, VulnerabilityProbability vulnerabilityProbability) {
          this.hotspotKey = hotspotKey;
          this.ruleKey = ruleKey;
          this.message = message;
          this.author = author;
          this.filePath = filePath;
          this.status = status;
          this.textRange = textRange;
          this.canChangeStatus = canChangeStatus;
          this.creationDate = creationDate;
          this.vulnerabilityProbability = vulnerabilityProbability;
        }

        public String getFilePath() {
          return filePath;
        }
      }

      private static class ServerIssue {
        private final String issueKey;
        private final String ruleKey;
        private final String message;
        private final String author;
        private final String filePath;
        private final String status;
        private final String resolution;
        private final String creationDate;
        private final TextRange textRange;

        private ServerIssue(String issueKey, String ruleKey, String message, String author, String filePath, String status,
          String resolution, String creationDate, TextRange textRange) {
          this.issueKey = issueKey;
          this.ruleKey = ruleKey;
          this.message = message;
          this.author = author;
          this.filePath = filePath;
          this.status = status;
          this.resolution = resolution;
          this.creationDate = creationDate;
          this.textRange = textRange;
        }

        public String getFilePath() {
          return filePath;
        }
      }
    }

    public static class ServerSourceFileBuilder {
      private String code;

      public ServerSourceFileBuilder withCode(String sourceCode) {
        this.code = sourceCode;
        return this;
      }
    }

    public static class HotspotBuilder {
      private String ruleKey = "ruleKey";
      private String message = "message";
      private String author = "author";
      private String filePath = "filePath";
      private HotspotReviewStatus reviewStatus = HotspotReviewStatus.TO_REVIEW;
      private TextRange textRange = new TextRange(1, 2, 3, 4);
      private boolean canChangeStatus = true;
      private Instant creationDate = Instant.now();
      private VulnerabilityProbability vulnerabilityProbability = VulnerabilityProbability.MEDIUM;

      public HotspotBuilder withRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
        return this;
      }

      public HotspotBuilder withMessage(String message) {
        this.message = message;
        return this;
      }

      public HotspotBuilder withAuthor(String author) {
        this.author = author;
        return this;
      }

      public HotspotBuilder withFilePath(String filePath) {
        this.filePath = filePath;
        return this;
      }

      public HotspotBuilder withStatus(HotspotReviewStatus status) {
        this.reviewStatus = status;
        return this;
      }

      public HotspotBuilder withTextRange(TextRange textRange) {
        this.textRange = textRange;
        return this;
      }

      public HotspotBuilder withoutStatusChangePermission() {
        this.canChangeStatus = false;
        return this;
      }

      public HotspotBuilder withCreationDate(Instant creationDate) {
        this.creationDate = creationDate;
        return this;
      }

      public HotspotBuilder withVulnerabilityProbability(VulnerabilityProbability vulnerabilityProbability) {
        this.vulnerabilityProbability = vulnerabilityProbability;
        return this;
      }

      public ServerProjectBranchBuilder.ServerHotspot build(String hotspotKey) {
        return new ServerProjectBranchBuilder.ServerHotspot(hotspotKey, ruleKey, message, author, filePath, reviewStatus, textRange, canChangeStatus, creationDate,
          vulnerabilityProbability);
      }
    }
  }

  public static class Server {

    private final WireMockServer mockServer = new WireMockServer(options().dynamicPort());

    private final ServerKind serverKind;
    private final ServerStatus serverStatus;
    @Nullable
    private final Version version;
    private final Map<String, ServerBuilder.ServerProjectBuilder> projectsByProjectKey;
    private final Map<String, ServerBuilder.ServerSourceFileBuilder> sourceFileByComponentKey;
    private final boolean smartNotificationsSupported;

    public Server(ServerKind serverKind, ServerStatus serverStatus, @Nullable String version,
      Map<String, ServerBuilder.ServerProjectBuilder> projectsByProjectKey,
      Map<String, ServerBuilder.ServerSourceFileBuilder> sourceFileByComponentKey, boolean smartNotificationsSupported) {
      this.serverKind = serverKind;
      this.serverStatus = serverStatus;
      this.version = version != null ? Version.create(version) : null;
      this.projectsByProjectKey = projectsByProjectKey;
      this.sourceFileByComponentKey = sourceFileByComponentKey;
      this.smartNotificationsSupported = smartNotificationsSupported;
    }

    public void start() {
      mockServer.start();
      registerWebApiResponses();
    }

    private void registerWebApiResponses() {
      if (serverStatus == ServerStatus.UP) {
        registerSystemApiResponses();
        registerHotspotsApiResponses();
        registerIssuesApiResponses();
        registerSourceApiResponses();
        registerDevelopersApiResponses();
        registerMeasuresApiResponses();
      }
    }

    private void registerSystemApiResponses() {
      mockServer.stubFor(get("/api/system/status")
        .willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"" + version + "\",\"status\": " +
          "\"UP\"}")));
    }

    private void registerHotspotsApiResponses() {
      if (version != null && version.satisfiesMinRequirement(HotspotApi.MIN_SQ_VERSION_SUPPORTING_PULL)) {
        registerApiHotspotsPullResponses();
      } else {
        registerApiHotspotSearchResponses();
      }
      registerHotspotsShowApiResponses();
      registerHotspotsStatusChangeApiResponses();
    }

    private void registerApiHotspotSearchResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        var messagesPerFilePath = branch.hotspots.stream()
          .collect(groupingBy(ServerBuilder.ServerProjectBranchBuilder.ServerHotspot::getFilePath,
            mapping(hotspot -> {
              var builder = Hotspots.SearchWsResponse.Hotspot.newBuilder()
                .setKey(hotspot.hotspotKey)
                .setComponent(projectKey + ":" + hotspot.filePath)
                .setRuleKey(hotspot.ruleKey)
                .setMessage(hotspot.message)
                .setTextRange(Common.TextRange.newBuilder()
                  .setStartLine(hotspot.textRange.getStartLine())
                  .setStartOffset(hotspot.textRange.getStartLineOffset())
                  .setEndLine(hotspot.textRange.getEndLine())
                  .setEndOffset(hotspot.textRange.getEndLineOffset())
                  .build())
                .setCreationDate(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ").withZone(ZoneId.systemDefault()).format(hotspot.creationDate))
                .setStatus(hotspot.status == HotspotReviewStatus.TO_REVIEW ? "TO_REVIEW" : "REVIEWED")
                .setResolution("")
                .setVulnerabilityProbability(hotspot.vulnerabilityProbability.toString())
                .setAssignee(hotspot.author);
              if (hotspot.status != HotspotReviewStatus.TO_REVIEW) {
                builder = builder.setResolution(hotspot.status.toString());
              }
              return builder.build();
            }, toList())));
        var branchParameter = branchName == null ? "" : "&branch=" + urlEncode(branchName);
        messagesPerFilePath.forEach((filePath,
          messages) -> mockServer.stubFor(get("/api/hotspots/search.protobuf?projectKey=" + projectKey + "&files=" + urlEncode(filePath) + branchParameter + "&ps=500&p=1")
            .willReturn(aResponse().withResponseBody(protobufBody(Hotspots.SearchWsResponse.newBuilder()
              .addComponents(Hotspots.Component.newBuilder().setPath(filePath).setKey(projectKey + ":" + filePath).build())
              .addAllHotspots(messages)
              .setPaging(Common.Paging.newBuilder().setTotal(messages.size()).build())
              .build())))));
        var allMessages = messagesPerFilePath.values().stream().flatMap(Collection::stream).collect(toList());
        mockServer.stubFor(get("/api/hotspots/search.protobuf?projectKey=" + projectKey + branchParameter + "&ps=500&p=1")
          .willReturn(aResponse().withResponseBody(protobufBody(Hotspots.SearchWsResponse.newBuilder()
            .addAllComponents(messagesPerFilePath.keySet().stream().map(filePath -> Hotspots.Component.newBuilder().setPath(filePath).setKey(projectKey + ":" + filePath).build())
              .collect(toList()))
            .setPaging(Common.Paging.newBuilder().setTotal(allMessages.size()).build())
            .addAllHotspots(allMessages).build()))));
      }));
    }

    private void registerSearchIssueApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        var issuesPerFilePath = branch.issues.stream()
          .collect(groupingBy(ServerBuilder.ServerProjectBranchBuilder.ServerIssue::getFilePath,
            mapping(issue -> {
              var builder = Issues.Issue.newBuilder()
                .setKey(issue.issueKey)
                .setComponent(projectKey + ":" + issue.filePath)
                .setRule(issue.ruleKey)
                .setMessage(issue.message)
                .setTextRange(Common.TextRange.newBuilder()
                  .setStartLine(issue.textRange.getStartLine())
                  .setStartOffset(issue.textRange.getStartLineOffset())
                  .setEndLine(issue.textRange.getEndLine())
                  .setEndOffset(issue.textRange.getEndLineOffset())
                  .build())
                .setCreationDate(issue.creationDate)
                .setStatus(issue.status)
                .setAssignee(issue.author);
              return builder.build();
            }, toList())));

        var allIssues = issuesPerFilePath.values().stream().flatMap(Collection::stream).collect(toList());

        allIssues.forEach(issue -> mockServer.stubFor(get("/api/issues/search.protobuf?issues=".concat(urlEncode(issue.getKey())).concat("&ps=1&p=1"))
          .willReturn(aResponse().withResponseBody(protobufBody(Issues.SearchWsResponse.newBuilder()
            .addIssues(
              Issues.Issue.newBuilder()
                .setKey(issue.getKey()).setRule(issue.getRule()).setCreationDate(issue.getCreationDate()).setMessage(issue.getMessage())
                .setTextRange(issue.getTextRange()).setComponent(issue.getComponent()).build())
            .addComponents(Issues.Component.newBuilder().setPath(issue.getComponent()).setKey(issue.getComponent()).build())
            .setRules(Issues.SearchWsResponse.newBuilder().getRulesBuilder().addRules(Common.Rule.newBuilder().setKey(issue.getRule()).build()))
            .build())))));
      }));
    }

    private void registerApiHotspotsPullResponses() {

    }

    private void registerHotspotsShowApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        branch.hotspots.forEach(hotspot -> {
          var textRange = hotspot.textRange;
          var reviewStatus = hotspot.status;
          var status = reviewStatus.isReviewed() ? "REVIEWED" : "TO_REVIEW";
          var builder = Hotspots.ShowWsResponse.newBuilder()
            .setMessage(hotspot.message)
            .setComponent(Hotspots.Component.newBuilder().setPath(hotspot.filePath).setKey(projectKey + ":" + hotspot.filePath))
            .setTextRange(Common.TextRange.newBuilder().setStartLine(textRange.getStartLine()).setStartOffset(textRange.getStartLineOffset()).setEndLine(textRange.getEndLine())
              .setEndOffset(textRange.getEndLineOffset()).build())
            .setAuthor(hotspot.author)
            .setStatus(status)
            .setCanChangeStatus(hotspot.canChangeStatus)
            .setRule(Hotspots.Rule.newBuilder().setKey(hotspot.ruleKey)
              .setName("name")
              .setSecurityCategory("category")
              .setVulnerabilityProbability("HIGH")
              .setRiskDescription("risk")
              .setVulnerabilityDescription("vulnerability")
              .setFixRecommendations("fix")
              .build());
          if (reviewStatus.isReviewed()) {
            builder = builder.setResolution(reviewStatus.name());
          }
          mockServer.stubFor(get("/api/hotspots/show.protobuf?hotspot=" + hotspot.hotspotKey)
            .willReturn(aResponse().withResponseBody(protobufBody(builder.build()))));
        });
      }));
    }

    private void registerHotspotsStatusChangeApiResponses() {
      mockServer.stubFor(post("/api/hotspots/change_status").willReturn(aResponse().withStatus(200)));
    }

    private void registerIssuesApiResponses() {
      if (version != null && version.satisfiesMinRequirement(IssueApi.MIN_SQ_VERSION_SUPPORTING_PULL)) {
        registerApiIssuesPullResponses();
        registerApiIssuesPullTaintResponses();
      } else {
        registerBatchIssuesResponses();
      }
      registerIssuesStatusChangeApiResponses();
      registerAddIssueCommentApiResponses();
      registerSearchIssueApiResponses();
      if (version != null && version.satisfiesMinRequirement(Version.create("10.2"))) {
        registerIssueAnticipateTransitionResponses();
      }
    }

    private void registerBatchIssuesResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        var allBranchIssues = new ArrayList<Message>();
        branch.issues.stream().collect(groupingBy(i -> i.filePath)).forEach((filePath, issues) -> {
          var messages = issues.stream().map(issue -> {
            var ruleKey = RuleKey.parse(issue.ruleKey);
            var serverIssue = ScannerInput.ServerIssue.newBuilder()
              .setKey(issue.issueKey)
              .setRuleRepository(ruleKey.repository())
              .setRuleKey(ruleKey.rule())
              .setChecksum("hash")
              .setMsg(issue.message)
              .setLine(issue.textRange.getStartLine())
              .setCreationDate(123456789L)
              .setPath(issue.filePath)
              .setType("BUG")
              .setManualSeverity(false)
              .setSeverity(Constants.Severity.BLOCKER)
              .build();
            allBranchIssues.add(serverIssue);
            return serverIssue;
          }).toArray(Message[]::new);
          var branchParameter = branchName == null ? "" : "&branch=" + urlEncode(branchName);
          mockServer.stubFor(get("/batch/issues?key=" + urlEncode(projectKey + ':' + filePath) + branchParameter)
            .willReturn(aResponse().withResponseBody(protobufBodyDelimited(messages))));
        });
        var branchParameter = branchName == null ? "" : "&branch=" + urlEncode(branchName);
        mockServer.stubFor(get("/batch/issues?key=" + urlEncode(projectKey) + branchParameter)
          .willReturn(aResponse().withResponseBody(protobufBodyDelimited(allBranchIssues.toArray(new Message[0])))));
      }));
    }

    private void registerIssuesStatusChangeApiResponses() {
      mockServer.stubFor(post("/api/issues/do_transition").willReturn(aResponse().withStatus(200)));
    }

    private void registerAddIssueCommentApiResponses() {
      mockServer.stubFor(post("/api/issues/add_comment").willReturn(aResponse().withStatus(200)));
    }

    private void registerApiIssuesPullResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        var branchParameter = branchName == null ? "" : "&branchName=" + branchName;
        var timestamp = Issues.IssuesPullQueryTimestamp.newBuilder().setQueryTimestamp(123L).build();
        var issuesArray = branch.issues.stream().map(issue -> Issues.IssueLite.newBuilder()
          .setKey(issue.issueKey)
          .setRuleKey(issue.ruleKey)
          .setType(Common.RuleType.BUG)
          .setUserSeverity(Common.Severity.MAJOR)
          .setMainLocation(Issues.Location.newBuilder().setFilePath(issue.filePath).setMessage(issue.message)
            .setTextRange(Issues.TextRange.newBuilder()
              .setStartLine(issue.textRange.getStartLine())
              .setStartLineOffset(issue.textRange.getStartLineOffset())
              .setEndLine(issue.textRange.getEndLine())
              .setEndLineOffset(issue.textRange.getEndLineOffset())
              .setHash("hash")))
          .setCreationDate(123456789L)
          .build()).toArray(Issues.IssueLite[]::new);
        var messages = new Message[issuesArray.length + 1];
        messages[0] = timestamp;
        System.arraycopy(issuesArray, 0, messages, 1, issuesArray.length);
        var response = aResponse().withResponseBody(protobufBodyDelimited(messages));
        mockServer.stubFor(get("/api/issues/pull?projectKey=" + projectKey + branchParameter).willReturn(response));
        mockServer.stubFor(get("/api/issues/pull?projectKey=" + projectKey + branchParameter + "&changedSince=" + timestamp.getQueryTimestamp()).willReturn(response));
      }));
    }

    private void registerApiIssuesPullTaintResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        var branchParameter = branchName == null ? "" : "&branchName=" + branchName;
        var timestamp = Issues.IssuesPullQueryTimestamp.newBuilder().setQueryTimestamp(123L).build();
        var response = aResponse().withResponseBody(protobufBodyDelimited(timestamp));
        mockServer.stubFor(get("/api/issues/pull_taint?projectKey=" + projectKey + branchParameter).willReturn(response));
        mockServer.stubFor(get("/api/issues/pull_taint?projectKey=" + projectKey + branchParameter + "&changedSince=" + timestamp.getQueryTimestamp()).willReturn(response));
      }));
    }

    private void registerIssueAnticipateTransitionResponses() {
      mockServer.stubFor(post("/api/issues/anticipated_transitions?projectKey=projectKey").willReturn(aResponse().withStatus(200)));
    }

    private void registerSourceApiResponses() {
      sourceFileByComponentKey
        .forEach((componentKey, sourceFile) -> mockServer.stubFor(get("/api/sources/raw?key=" + urlEncode(componentKey)).willReturn(aResponse().withBody(sourceFile.code))));
    }

    private void registerDevelopersApiResponses() {
      if (smartNotificationsSupported) {
        mockServer.stubFor(get("/api/developers/search_events?projects=&from=").willReturn(aResponse().withStatus(200)));
      }
    }

    private void registerMeasuresApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        var branchParameter = branchName == null ? "" : "&branch=" + branchName;
        var uriPath = "/api/measures/component.protobuf?additionalFields=period&metricKeys=projects&component=" + projectKey + branchParameter;
        mockServer.stubFor(get(uriPath)
          .willReturn(aResponse().withResponseBody(protobufBody(
            // TODO Override with whatever is set on the branch fixture?
            Measures.ComponentWsResponse.newBuilder()
              .setComponent(Measures.Component.newBuilder()
                .setKey(projectKey)
                .setQualifier("TRK")
                .build())
              .setPeriod(Measures.Period.newBuilder()
                .setMode("PREVIOUS_VERSION")
                .setDate("2023-08-29T09:37:59+0000")
                .setParameter("9.2")
                .build())
              .build()))));
      }));
    }

    public void shutdown() {
      mockServer.stop();
    }

    public String baseUrl() {
      return mockServer.url("");
    }

    public String url(String path) {
      return mockServer.url(path);
    }

    public WireMockServer getMockServer() {
      return mockServer;
    }
  }
}
