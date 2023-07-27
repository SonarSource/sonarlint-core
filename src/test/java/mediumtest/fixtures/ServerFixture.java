/*
 * SonarLint Core - Implementation
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

import com.google.protobuf.Message;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.exception.UnsupportedServerException;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import testutils.MockWebServerExtensionWithProtobuf;

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
      private final Map<String, ServerHotspot> hotspotsByKey = new HashMap<>();
      private final Collection<ServerIssue> issues = new ArrayList<>();

      public ServerProjectBranchBuilder withHotspot(String hotspotKey) {
        return withHotspot(hotspotKey, UnaryOperator.identity());
      }

      public ServerProjectBranchBuilder withHotspot(String hotspotKey, UnaryOperator<HotspotBuilder> hotspotBuilder) {
        var builder = new HotspotBuilder();
        hotspotBuilder.apply(builder);
        this.hotspotsByKey.put(hotspotKey, builder.build(hotspotKey));
        return this;
      }

      public ServerProjectBranchBuilder withIssue(String issueKey, String ruleKey, String message, String author, String filePath,
        String status, String resolution, TextRange textRange) {
        this.issues.add(new ServerIssue(issueKey, ruleKey, message, author, filePath, status, resolution, textRange));
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

        private ServerHotspot(String hotspotKey, String ruleKey, String message, String author, String filePath, HotspotReviewStatus status, TextRange textRange,
          boolean canChangeStatus) {
          this.hotspotKey = hotspotKey;
          this.ruleKey = ruleKey;
          this.message = message;
          this.author = author;
          this.filePath = filePath;
          this.status = status;
          this.textRange = textRange;
          this.canChangeStatus = canChangeStatus;
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
        private final TextRange textRange;

        private ServerIssue(String issueKey, String ruleKey, String message, String author, String filePath, String status,
          String resolution, TextRange textRange) {
          this.issueKey = issueKey;
          this.ruleKey = ruleKey;
          this.message = message;
          this.author = author;
          this.filePath = filePath;
          this.status = status;
          this.resolution = resolution;
          this.textRange = textRange;
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

      public ServerProjectBranchBuilder.ServerHotspot build(String hotspotKey) {
        return new ServerProjectBranchBuilder.ServerHotspot(hotspotKey, ruleKey, message, author, filePath, reviewStatus, textRange, canChangeStatus);
      }
    }
  }

  public static class Server {

    private final MockWebServerExtensionWithProtobuf mockWebServer = new MockWebServerExtensionWithProtobuf();
    private final ServerKind serverKind;
    private final ServerStatus serverStatus;
    @Nullable
    private final String version;
    private final Map<String, ServerBuilder.ServerProjectBuilder> projectsByProjectKey;
    private final Map<String, ServerBuilder.ServerSourceFileBuilder> sourceFileByComponentKey;
    private final boolean smartNotificationsSupported;

    public Server(ServerKind serverKind, ServerStatus serverStatus, @Nullable String version,
      Map<String, ServerBuilder.ServerProjectBuilder> projectsByProjectKey,
      Map<String, ServerBuilder.ServerSourceFileBuilder> sourceFileByComponentKey, boolean smartNotificationsSupported) {
      this.serverKind = serverKind;
      this.serverStatus = serverStatus;
      this.version = version;
      this.projectsByProjectKey = projectsByProjectKey;
      this.sourceFileByComponentKey = sourceFileByComponentKey;
      this.smartNotificationsSupported = smartNotificationsSupported;
    }

    public void start() {
      mockWebServer.start();
      registerWebApiResponses();
    }

    private void registerWebApiResponses() {
      if (serverStatus == ServerStatus.UP) {
        registerSystemApiResponses();
        registerHotspotsApiResponses();
        registerIssuesApiResponses();
        registerSourceApiResponses();
        registerDevelopersApiResponses();
      }
    }

    private void registerSystemApiResponses() {
      mockWebServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"" + version + "\",\"status\": " +
        "\"UP\"}");
    }

    private void registerHotspotsApiResponses() {
      registerHotspotsShowApiResponses();
      registerHotspotsStatusChangeApiResponses();
    }

    private void registerHotspotsShowApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        branch.hotspotsByKey.forEach((key, hotspot) -> {
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
          mockWebServer.addProtobufResponse("/api/hotspots/show.protobuf?hotspot=" + hotspot.hotspotKey,
            builder.build());
        });
      }));
    }

    private void registerHotspotsStatusChangeApiResponses() {
      mockWebServer.addResponse("/api/hotspots/change_status", new MockResponse().setResponseCode(200));
    }

    private void registerIssuesApiResponses() {
      registerApiIssuesPullResponses();
      registerApiIssuesPullTaintResponses();
      registerIssuesStatusChangeApiResponses();
      registerAddIssueCommentApiResponses();
    }

    private void registerIssuesStatusChangeApiResponses() {
      mockWebServer.addResponse("/api/issues/do_transition", new MockResponse().setResponseCode(200));
    }

    private void registerAddIssueCommentApiResponses() {
      mockWebServer.addResponse("/api/issues/add_comment", new MockResponse().setResponseCode(200));
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
        mockWebServer.addProtobufResponseDelimited("/api/issues/pull?projectKey=" + projectKey + branchParameter, messages);
        mockWebServer.addProtobufResponseDelimited("/api/issues/pull?projectKey=" + projectKey + branchParameter + "&changedSince=" + timestamp.getQueryTimestamp(), messages);
      }));
    }

    private void registerApiIssuesPullTaintResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        var timestamp = Issues.IssuesPullQueryTimestamp.newBuilder().setQueryTimestamp(123L).build();
        mockWebServer.addProtobufResponseDelimited("/api/issues/pull_taint?projectKey=" + projectKey + "&branchName=" + branchName, timestamp);
        mockWebServer.addProtobufResponseDelimited(
          "/api/issues/pull_taint?projectKey=" + projectKey + "&branchName=" + branchName + "&changedSince=" + timestamp.getQueryTimestamp(), timestamp);
      }));
    }

    private void registerSourceApiResponses() {
      sourceFileByComponentKey.forEach((componentKey, sourceFile) -> mockWebServer.addStringResponse("/api/sources/raw?key=" + UrlUtils.urlEncode(componentKey), sourceFile.code));
    }

    private void registerDevelopersApiResponses() {
      if (smartNotificationsSupported) {
        mockWebServer.addResponse("/api/developers/search_events?projects=&from=", new MockResponse().setResponseCode(200));
      }
    }

    public void shutdown() {
      mockWebServer.shutdown();
    }

    public String baseUrl() {
      return mockWebServer.url("");
    }

    public String url(String path) {
      return mockWebServer.url(path);
    }

    public RecordedRequest lastRequest() {
      return mockWebServer.takeRequest();
    }
  }
}
