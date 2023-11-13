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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonar.scanner.protocol.Constants;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.TextRange;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverapi.issue.IssueApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Measures;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.ProjectBranches;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Qualityprofiles;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Settings;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;
import static org.sonarsource.sonarlint.core.serverapi.rules.RulesApi.TAINT_REPOS_BY_LANGUAGE;
import static testutils.TestUtils.protobufBody;
import static testutils.TestUtils.protobufBodyDelimited;

public class ServerFixture {
  public static ServerBuilder newSonarQubeServer() {
    return newSonarQubeServer("99.9");
  }

  public static ServerBuilder newSonarQubeServer(String version) {
    return new ServerBuilder(ServerKind.SONARQUBE, null, version);
  }

  public static ServerBuilder newSonarCloudServer() {
    return newSonarCloudServer("myOrganization");
  }

  public static ServerBuilder newSonarCloudServer(String organization) {
    return new ServerBuilder(ServerKind.SONARCLOUD, organization, null);
  }

  private enum ServerKind {
    SONARQUBE, SONARCLOUD
  }

  public enum ServerStatus {
    UP, DOWN
  }

  public static class ServerBuilder {
    private final ServerKind serverKind;
    @Nullable
    private final String organizationKey;
    private final String version;
    private final Map<String, ServerProjectBuilder> projectByProjectKey = new HashMap<>();
    private final Map<String, ServerQualityProfileBuilder> qualityProfilesByKey = new HashMap<>();
    private final Map<String, ServerPluginBuilder> pluginsByKey = new HashMap<>();
    private ServerStatus serverStatus = ServerStatus.UP;
    private boolean smartNotificationsSupported;

    public ServerBuilder(ServerKind serverKind, @Nullable String organizationKey, @Nullable String version) {
      this.serverKind = serverKind;
      this.organizationKey = organizationKey;
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

    public ServerBuilder withQualityProfile(String qualityProfileKey, UnaryOperator<ServerQualityProfileBuilder> qualityProfileBuilder) {
      var builder = new ServerQualityProfileBuilder();
      this.qualityProfilesByKey.put(qualityProfileKey, qualityProfileBuilder.apply(builder));
      return this;
    }

    public ServerBuilder withSmartNotificationsSupported(boolean smartNotificationsSupported) {
      this.smartNotificationsSupported = smartNotificationsSupported;
      return this;
    }

    public ServerBuilder withPlugin(TestPlugin testPlugin) {
      return withPlugin(testPlugin.getPluginKey(), plugin -> plugin.withJarPath(testPlugin.getPath()).withHash(testPlugin.getHash()));
    }

    public ServerBuilder withPlugin(String pluginKey, UnaryOperator<ServerPluginBuilder> pluginBuilder) {
      var builder = new ServerPluginBuilder();
      this.pluginsByKey.put(pluginKey, pluginBuilder.apply(builder));
      return this;
    }

    public Server start() {
      var server = new Server(serverKind, serverStatus, organizationKey, version, projectByProjectKey, smartNotificationsSupported, pluginsByKey, qualityProfilesByKey);
      server.start();
      return server;
    }

    public static class ServerPluginBuilder {
      private String hash = "hash";
      private Path jarPath;
      private boolean sonarLintSupported = true;

      public ServerPluginBuilder withHash(String hash) {
        this.hash = hash;
        return this;
      }

      public ServerPluginBuilder withJarPath(Path jarPath) {
        this.jarPath = jarPath;
        return this;
      }

      public ServerPluginBuilder withSonarLintSupported(boolean sonarLintSupported) {
        this.sonarLintSupported = sonarLintSupported;
        return this;
      }
    }

    public static class ServerProjectBuilder {
      private final Map<String, ServerProjectBranchBuilder> branchesByName = new HashMap<>();
      private final Map<String, ServerProjectPullRequestBuilder> pullRequestsByName = new HashMap<>();
      private final List<String> qualityProfileKeys = new ArrayList<>();
      private final List<String> relativeFilePaths = new ArrayList<>();
      private String name = "MyProject";

      public ServerProjectBuilder withEmptyBranch(String branchName) {
        var builder = new ServerProjectBranchBuilder();
        this.branchesByName.put(branchName, builder);
        return this;
      }

      public ServerProjectBuilder withBranch(String branchName) {
        return withBranch(branchName, builder -> builder);
      }

      public ServerProjectBuilder withBranch(String branchName, UnaryOperator<ServerProjectBranchBuilder> branchBuilder) {
        var builder = new ServerProjectBranchBuilder();
        this.branchesByName.put(branchName, branchBuilder.apply(builder));
        return this;
      }

      public ServerProjectBuilder withPullRequest(String pullRequestNumber, UnaryOperator<ServerProjectPullRequestBuilder> pullRequestBuilder) {
        var builder = new ServerProjectPullRequestBuilder();
        this.pullRequestsByName.put(pullRequestNumber, pullRequestBuilder.apply(builder));
        return this;
      }

      public ServerProjectBuilder withDefaultBranch(UnaryOperator<ServerProjectBranchBuilder> branchBuilder) {
        return withBranch(null, branchBuilder);
      }

      public ServerProjectBuilder withName(String name) {
        this.name = name;
        return this;
      }

      public ServerProjectBuilder withQualityProfile(String qualityProfileKey) {
        this.qualityProfileKeys.add(qualityProfileKey);
        return this;
      }

      public ServerProjectBuilder withFile(String relativeFilePath) {
        this.relativeFilePaths.add(relativeFilePath);
        return this;
      }

      public static class ServerProjectBranchBuilder {
        protected final Collection<ServerHotspot> hotspots = new ArrayList<>();
        protected final Collection<ServerIssue> issues = new ArrayList<>();
        private final Collection<ServerIssue> taintIssues = new ArrayList<>();
        protected final Map<String, ServerSourceFileBuilder> sourceFileByComponentKey = new HashMap<>();

        public ServerProjectBranchBuilder withHotspot(String hotspotKey) {
          return withHotspot(hotspotKey, UnaryOperator.identity());
        }

        public ServerProjectBranchBuilder withHotspot(String hotspotKey, UnaryOperator<HotspotBuilder> hotspotBuilder) {
          var builder = new HotspotBuilder();
          hotspotBuilder.apply(builder);
          this.hotspots.add(builder.build(hotspotKey));
          return this;
        }

        public ServerProjectBranchBuilder withIssue(String issueKey) {
          return withIssue(issueKey, "ruleKey", "message", "author", "filePath", "OPEN", null, Instant.now(),
            new TextRange(1, 2, 3, 4));
        }

        public ServerProjectBranchBuilder withIssue(String issueKey, String ruleKey, String message, String author, String filePath,
          String status, String resolution, Instant creationDate, TextRange textRange) {
          this.issues.add(new ServerIssue(issueKey, ruleKey, message, author, filePath, status, resolution, creationDate, textRange, RuleType.BUG));
          return this;
        }

        public ServerProjectBranchBuilder withSourceFile(String componentKey, UnaryOperator<ServerSourceFileBuilder> sourceFileBuilder) {
          var builder = new ServerSourceFileBuilder();
          this.sourceFileByComponentKey.put(componentKey, sourceFileBuilder.apply(builder));
          return this;
        }

        public ServerProjectBranchBuilder withTaintIssue(String issueKey, String ruleKey, String message, String author, String filePath,
          String status, String resolution, Instant introductionDate, TextRange textRange, RuleType ruleType) {
          this.taintIssues.add(new ServerIssue(issueKey, ruleKey, message, author, filePath, status, resolution, introductionDate, textRange, ruleType));
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

        protected static class ServerIssue {
          private final String issueKey;
          private final String ruleKey;
          private final String message;
          private final String author;
          private final String filePath;
          private final String status;
          private final String resolution;
          private final Instant introductionDate;
          private final TextRange textRange;
          private final RuleType ruleType;

          private ServerIssue(String issueKey, String ruleKey, String message, String author, String filePath, String status,
            String resolution, Instant introductionDate, TextRange textRange, RuleType ruleType) {
            this.issueKey = issueKey;
            this.ruleKey = ruleKey;
            this.message = message;
            this.author = author;
            this.filePath = filePath;
            this.status = status;
            this.resolution = resolution;
            this.introductionDate = introductionDate;
            this.textRange = textRange;
            this.ruleType = ruleType;
          }

          public String getFilePath() {
            return filePath;
          }
        }
      }

      public static class ServerProjectPullRequestBuilder extends ServerProjectBranchBuilder {
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

    public static class ServerQualityProfileBuilder {
      private String languageKey = "lang";
      private final Map<String, ServerActiveRuleBuilder> activeRulesByKey = new HashMap<>();

      public ServerQualityProfileBuilder withLanguage(String languageKey) {
        this.languageKey = languageKey;
        return this;
      }

      public ServerQualityProfileBuilder withActiveRule(String ruleKey, UnaryOperator<ServerActiveRuleBuilder> activeRuleBuilder) {
        var builder = new ServerActiveRuleBuilder();
        this.activeRulesByKey.put(ruleKey, activeRuleBuilder.apply(builder));
        return this;
      }

      public static class ServerActiveRuleBuilder {
        private IssueSeverity issueSeverity = IssueSeverity.CRITICAL;

        public ServerActiveRuleBuilder withSeverity(IssueSeverity issueSeverity) {
          this.issueSeverity = issueSeverity;
          return this;
        }
      }
    }

  }

  public static class Server {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ").withZone(ZoneId.from(ZoneOffset.UTC));

    private final WireMockServer mockServer = new WireMockServer(options().dynamicPort());

    private final ServerKind serverKind;
    private final ServerStatus serverStatus;
    @Nullable
    private final String organizationKey;
    @Nullable
    private final Version version;
    private final Map<String, ServerBuilder.ServerProjectBuilder> projectsByProjectKey;
    private final boolean smartNotificationsSupported;
    private final Map<String, ServerBuilder.ServerPluginBuilder> pluginsByKey;
    private final Map<String, ServerBuilder.ServerQualityProfileBuilder> qualityProfilesByKey;

    public Server(ServerKind serverKind, ServerStatus serverStatus, @Nullable String organizationKey, @Nullable String version,
      Map<String, ServerBuilder.ServerProjectBuilder> projectsByProjectKey,
      boolean smartNotificationsSupported, Map<String, ServerBuilder.ServerPluginBuilder> pluginsByKey,
      Map<String, ServerBuilder.ServerQualityProfileBuilder> qualityProfilesByKey) {
      this.serverKind = serverKind;
      this.serverStatus = serverStatus;
      this.organizationKey = organizationKey;
      this.version = version != null ? Version.create(version) : null;
      this.projectsByProjectKey = projectsByProjectKey;
      this.smartNotificationsSupported = smartNotificationsSupported;
      this.pluginsByKey = pluginsByKey;
      this.qualityProfilesByKey = qualityProfilesByKey;
    }

    public void start() {
      mockServer.start();
      registerWebApiResponses();
    }

    private void registerWebApiResponses() {
      registerSystemApiResponses();
      if (serverStatus == ServerStatus.UP) {
        registerPluginsApiResponses();
        registerQualityProfilesApiResponses();
        registerRulesApiResponses();
        registerProjectBranchesApiResponses();
        registerHotspotsApiResponses();
        registerIssuesApiResponses();
        registerSourceApiResponses();
        registerDevelopersApiResponses();
        registerMeasuresApiResponses();
        registerComponentsApiResponses();
        registerSettingsApiResponses();
      }
    }

    private void registerSystemApiResponses() {
      mockServer.stubFor(get("/api/system/status")
        .willReturn(aResponse().withStatus(200).withBody("{\"id\": \"20160308094653\",\"version\": \"" + version + "\",\"status\": " +
          "\"" + serverStatus + "\"}")));
    }

    private void registerPluginsApiResponses() {
      registerPluginsInstalledResponses();
      registerPluginsDownloadResponses();
    }

    private void registerPluginsInstalledResponses() {
      mockServer.stubFor(get("/api/plugins/installed")
        .willReturn(aResponse().withStatus(200).withBody("{\"plugins\": [" +
          pluginsByKey.entrySet().stream().map(
            entry -> {
              var pluginKey = entry.getKey();
              return "{\"key\": \"" + pluginKey + "\", " +
                "\"hash\": \"" + entry.getValue().hash + "\", " +
                "\"filename\": \"" + entry.getValue().jarPath.getFileName() + "\", " +
                "\"sonarLintSupported\": " + entry.getValue().sonarLintSupported + "}";
            })
            .collect(Collectors.joining(", "))
          + "]}")));
    }

    private void registerPluginsDownloadResponses() {
      pluginsByKey.forEach((pluginKey, plugin) -> {
        try {
          var pluginContent = Files.exists(plugin.jarPath) ? Files.readAllBytes(plugin.jarPath) : new byte[0];
          mockServer.stubFor(get("/api/plugins/download?plugin=" + pluginKey)
            .willReturn(aResponse().withStatus(200).withBody(pluginContent)));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }

    private void registerQualityProfilesApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> mockServer.stubFor(get("/api/qualityprofiles/search.protobuf?project=" + projectKey)
        .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Qualityprofiles.SearchWsResponse.newBuilder().addAllProfiles(
          project.qualityProfileKeys.stream().map(qualityProfileKey -> {
            var qualityProfile = qualityProfilesByKey.get(qualityProfileKey);
            return Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder()
              .setKey(qualityProfileKey)
              .setLanguage(qualityProfile.languageKey)
              .setLanguageName(qualityProfile.languageKey)
              .setName("Quality Profile")
              .setRulesUpdatedAt(Instant.now().toString())
              .setUserUpdatedAt(Instant.now().toString())
              .setIsDefault(true)
              .setActiveRuleCount(qualityProfile.activeRulesByKey.size())
              .build();
          }).collect(toList())).build())))));
    }

    private void registerRulesApiResponses() {
      qualityProfilesByKey.forEach((qualityProfileKey, qualityProfile) -> {
        var url = "/api/rules/search.protobuf?qprofile=";
        if (serverKind == ServerKind.SONARCLOUD) {
          url += "&organization=" + organizationKey;
        }
        url += qualityProfileKey + "&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY,SECURITY_HOTSPOT&s=key&ps=500&p=1";
        mockServer.stubFor(get(url)
          .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Rules.SearchResponse.newBuilder()
            .addAllRules(qualityProfile.activeRulesByKey.entrySet().stream().map(entry -> Rules.Rule.newBuilder()
              .setKey(entry.getKey())
              .setSeverity(entry.getValue().issueSeverity.name())
              .build()).collect(toList()))
            .setActives(Rules.Actives.newBuilder()
              .putAllActives(qualityProfile.activeRulesByKey.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> Rules.ActiveList.newBuilder()
                .addActiveList(Rules.Active.newBuilder().setSeverity(entry.getValue().issueSeverity.name()).build()).build())))
              .build())
            .setPaging(Common.Paging.newBuilder().setTotal(qualityProfile.activeRulesByKey.size()).build())
            .build()))));
      });
      var taintActiveRulesByKey = qualityProfilesByKey.values().stream().flatMap(qp -> qp.activeRulesByKey.entrySet().stream())
        .filter(entry -> TAINT_REPOS_BY_LANGUAGE.containsValue(entry.getKey())).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
      var url = "/api/rules/search.protobuf?repositories=roslyn.sonaranalyzer.security.cs,javasecurity,jssecurity,phpsecurity,pythonsecurity,tssecurity";
      if (serverKind == ServerKind.SONARCLOUD) {
        url += "&organization=" + organizationKey;
      }
      url += "&f=repo&s=key&ps=500&p=1";
      mockServer.stubFor(get(url)
        .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(Rules.SearchResponse.newBuilder()
          .addAllRules(taintActiveRulesByKey.entrySet().stream().map(entry -> Rules.Rule.newBuilder()
            .setKey(entry.getKey())
            .setSeverity(entry.getValue().issueSeverity.name())
            .build()).collect(toList()))
          .setActives(Rules.Actives.newBuilder()
            .putAllActives(taintActiveRulesByKey.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> Rules.ActiveList.newBuilder()
              .addActiveList(Rules.Active.newBuilder().setSeverity(entry.getValue().issueSeverity.name()).build()).build())))
            .build())
          .setPaging(Common.Paging.newBuilder().setTotal(taintActiveRulesByKey.size()).build())
          .build()))));
    }

    private void registerProjectBranchesApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> mockServer.stubFor(get("/api/project_branches/list.protobuf?project=" + projectKey)
        .willReturn(aResponse().withStatus(200).withResponseBody(protobufBody(ProjectBranches.ListWsResponse.newBuilder()
          .addBranches(ProjectBranches.Branch.newBuilder().setName("main").setIsMain(true).setType(Common.BranchType.LONG).build())
          .addAllBranches(project.branchesByName.keySet().stream()
            .filter(Objects::nonNull)
            .map(branchName -> ProjectBranches.Branch.newBuilder().setName(branchName).setIsMain(false).setType(Common.BranchType.LONG).build()).collect(Collectors.toList()))
          .build())))));
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
          .collect(groupingBy(ServerBuilder.ServerProjectBuilder.ServerProjectBranchBuilder.ServerHotspot::getFilePath,
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
      projectsByProjectKey.forEach((projectKey, project) -> {
        project.pullRequestsByName.forEach((pullRequestName, pullRequest) -> {
          var issuesPerFilePath = getIssuesPerFilePath(projectKey, pullRequest);

          var allIssues = issuesPerFilePath.values().stream().flatMap(Collection::stream).collect(toList());

          allIssues.forEach(issue -> {
            var searchUrl = "/api/issues/search.protobuf?issues=".concat(urlEncode(issue.getKey()))
              .concat("&componentKeys=").concat(projectKey)
              .concat("&ps=1&p=1")
              .concat("&pullRequest=").concat(pullRequestName);
            mockServer.stubFor(get(searchUrl)
              .willReturn(aResponse().withResponseBody(protobufBody(Issues.SearchWsResponse.newBuilder()
                .addIssues(
                  Issues.Issue.newBuilder()
                    .setKey(issue.getKey()).setRule(issue.getRule()).setCreationDate(issue.getCreationDate()).setMessage(issue.getMessage())
                    .setTextRange(issue.getTextRange()).setComponent(issue.getComponent()).build())
                .addAllComponents(
                  issuesPerFilePath.keySet().stream().map(issues -> Issues.Component.newBuilder().setPath(issues).setKey(projectKey + ":" + issues).build()).collect(toList()))
                .setRules(Issues.SearchWsResponse.newBuilder().getRulesBuilder().addRules(Common.Rule.newBuilder().setKey(issue.getRule()).build()))
                .setPaging(Common.Paging.newBuilder().setTotal(1).build())
                .build()))));
          });
        });
        project.branchesByName.forEach((branchName, branch) -> {
          var issuesPerFilePath = getIssuesPerFilePath(projectKey, branch);

          var allIssues = issuesPerFilePath.values().stream().flatMap(Collection::stream).collect(toList());

          allIssues.forEach(issue -> {
            var searchUrl = "/api/issues/search.protobuf?issues=".concat(urlEncode(issue.getKey()))
              .concat("&componentKeys=").concat(projectKey)
              .concat("&ps=1&p=1")
              .concat("&branch=").concat(branchName);
            mockServer.stubFor(get(searchUrl)
              .willReturn(aResponse().withResponseBody(protobufBody(Issues.SearchWsResponse.newBuilder()
                .addIssues(
                  Issues.Issue.newBuilder()
                    .setKey(issue.getKey()).setRule(issue.getRule()).setCreationDate(issue.getCreationDate()).setMessage(issue.getMessage())
                    .setTextRange(issue.getTextRange()).setComponent(issue.getComponent()).setType(issue.getType()).build())
                .addAllComponents(
                  issuesPerFilePath.keySet().stream().map(issues -> Issues.Component.newBuilder().setPath(issues).setKey(projectKey + ":" + issues).build()).collect(toList()))
                .setRules(Issues.SearchWsResponse.newBuilder().getRulesBuilder().addRules(Common.Rule.newBuilder().setKey(issue.getRule()).build()))
                .setPaging(Common.Paging.newBuilder().setTotal(1).build())
                .build()))));
          });

          var vulnerabilities = allIssues.stream().filter(issue -> issue.getType() == Common.RuleType.VULNERABILITY).collect(toList());
          var searchUrl = "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED,RESOLVED&types=VULNERABILITY&componentKeys=" + projectKey + "&rules=&branch=" + branchName
            + "&ps=500&p=1";
          mockServer.stubFor(get(searchUrl)
            .willReturn(aResponse().withResponseBody(protobufBody(Issues.SearchWsResponse.newBuilder()
              .addAllIssues(
                vulnerabilities.stream().map(vulnerability -> Issues.Issue.newBuilder()
                  .setKey(vulnerability.getKey()).setRule(vulnerability.getRule()).setCreationDate(vulnerability.getCreationDate()).setMessage(vulnerability.getMessage())
                  .setTextRange(vulnerability.getTextRange()).setComponent(vulnerability.getComponent()).setType(vulnerability.getType()).build()).collect(toList()))
              .addAllComponents(
                issuesPerFilePath.keySet().stream().map(issues -> Issues.Component.newBuilder().setPath(issues).setKey(projectKey + ":" + issues).build()).collect(toList()))
              .setPaging(Common.Paging.newBuilder().setTotal(vulnerabilities.size()).build())
              .build()))));
        });
      });
    }

    @NotNull
    private static Map<String, List<Issues.Issue>> getIssuesPerFilePath(String projectKey, ServerBuilder.ServerProjectBuilder.ServerProjectBranchBuilder pullRequestOrBranch) {
      return Stream.concat(pullRequestOrBranch.issues.stream(), pullRequestOrBranch.taintIssues.stream())
        .collect(groupingBy(ServerBuilder.ServerProjectBuilder.ServerProjectBranchBuilder.ServerIssue::getFilePath,
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
              .setCreationDate(DATETIME_FORMATTER.format(issue.introductionDate))
              .setStatus(issue.status)
              .setAssignee(issue.author)
              .setType(Common.RuleType.valueOf(issue.ruleType.name()));
            return builder.build();
          }, toList())));
    }

    private void registerApiHotspotsPullResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        var branchParameter = branchName == null ? "" : "&branchName=" + branchName;
        var timestamp = Hotspots.HotspotPullQueryTimestamp.newBuilder().setQueryTimestamp(123L).build();
        var hotspotsArray = branch.hotspots.stream().map(hotspot -> Hotspots.HotspotLite.newBuilder()
          .setKey(hotspot.hotspotKey)
          .setRuleKey(hotspot.ruleKey)
          .setVulnerabilityProbability(hotspot.vulnerabilityProbability.name())
          .setMessage(hotspot.message)
          .setFilePath(hotspot.filePath)
          .setTextRange(Hotspots.TextRange.newBuilder()
            .setStartLine(hotspot.textRange.getStartLine())
            .setStartLineOffset(hotspot.textRange.getStartLineOffset())
            .setEndLine(hotspot.textRange.getEndLine())
            .setEndLineOffset(hotspot.textRange.getEndLineOffset())
            .setHash("hash"))
          .setCreationDate(123456789L)
          .build()).toArray(Hotspots.HotspotLite[]::new);
        var messages = new Message[hotspotsArray.length + 1];
        messages[0] = timestamp;
        System.arraycopy(hotspotsArray, 0, messages, 1, hotspotsArray.length);
        var response = aResponse().withResponseBody(protobufBodyDelimited(messages));
        mockServer.stubFor(
          get(urlMatching("\\Q/api/hotspots/pull?projectKey=" + projectKey + branchParameter + "\\E(&languages=.*)?(\\Q&changedSince=" + timestamp.getQueryTimestamp() + "\\E)?"))
            .willReturn(response));
      }));
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
        mockServer.stubFor(
          get(urlMatching("\\Q/api/issues/pull?projectKey=" + projectKey + branchParameter + "\\E(&languages=.*)?(\\Q&changedSince=" + timestamp.getQueryTimestamp() + "\\E)?"))
            .willReturn(response));
      }));
    }

    private void registerApiIssuesPullTaintResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        var branchParameter = branchName == null ? "" : "&branchName=" + branchName;
        var timestamp = Issues.TaintVulnerabilityPullQueryTimestamp.newBuilder().setQueryTimestamp(123L).build();
        var issuesArray = branch.taintIssues.stream().map(issue -> Issues.TaintVulnerabilityLite.newBuilder()
          .setKey(issue.issueKey)
          .setRuleKey(issue.ruleKey)
          .setType(Common.RuleType.BUG)
          .setSeverity(Common.Severity.MAJOR)
          .setMainLocation(Issues.Location.newBuilder().setFilePath(issue.filePath).setMessage(issue.message)
            .setTextRange(Issues.TextRange.newBuilder()
              .setStartLine(issue.textRange.getStartLine())
              .setStartLineOffset(issue.textRange.getStartLineOffset())
              .setEndLine(issue.textRange.getEndLine())
              .setEndLineOffset(issue.textRange.getEndLineOffset())
              .setHash("hash")))
          .setCreationDate(issue.introductionDate.toEpochMilli())
          .build()).toArray(Issues.TaintVulnerabilityLite[]::new);
        var messages = new Message[issuesArray.length + 1];
        messages[0] = timestamp;
        System.arraycopy(issuesArray, 0, messages, 1, issuesArray.length);
        var response = aResponse().withResponseBody(protobufBodyDelimited(messages));
        mockServer.stubFor(get(
          urlMatching("\\Q/api/issues/pull_taint?projectKey=" + projectKey + branchParameter + "\\E(&languages=.*)?(\\Q&changedSince=" + timestamp.getQueryTimestamp() + "\\E)?"))
            .willReturn(response));
      }));
    }

    private void registerIssueAnticipateTransitionResponses() {
      mockServer.stubFor(post("/api/issues/anticipated_transitions?projectKey=projectKey").willReturn(aResponse().withStatus(200)));
    }

    private void registerSourceApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.pullRequestsByName.forEach((pullRequestName, pullRequest) -> pullRequest.sourceFileByComponentKey
        .forEach((componentKey, sourceFile) -> mockServer
          .stubFor(get("/api/sources/raw?key=" + urlEncode(componentKey) + "&pullRequest=" + urlEncode(pullRequestName)).willReturn(aResponse().withBody(sourceFile.code))))));

      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        if (branchName != null) {
          branch.sourceFileByComponentKey
            .forEach((componentKey, sourceFile) -> mockServer
              .stubFor(get("/api/sources/raw?key=" + urlEncode(componentKey) + "&branch=" + urlEncode(branchName)).willReturn(aResponse().withBody(sourceFile.code))));
        }
      }));

      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> branch.sourceFileByComponentKey
        .forEach((componentKey, sourceFile) -> mockServer.stubFor(get("/api/sources/raw?key=" + urlEncode(componentKey)).willReturn(aResponse().withBody(sourceFile.code))))));
    }

    private void registerDevelopersApiResponses() {
      if (smartNotificationsSupported) {
        mockServer.stubFor(get("/api/developers/search_events?projects=&from=").willReturn(aResponse().withStatus(200)));
      }
    }

    private void registerMeasuresApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> {
        var uriPath = "/api/measures/component.protobuf?additionalFields=period&metricKeys=projects&component=" + projectKey;
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
      });
    }

    private void registerComponentsApiResponses() {
      registerComponentsSearchApiResponses();
      registerComponentsTreeApiResponses();
    }

    private void registerComponentsSearchApiResponses() {
      var url = "/api/components/search.protobuf?qualifiers=TRK";
      if (serverKind == ServerKind.SONARCLOUD) {
        url += "&organization=" + organizationKey;
      }
      url += "&ps=500&p=1";
      mockServer.stubFor(get(url)
        .willReturn(aResponse().withResponseBody(protobufBody(Components.SearchWsResponse.newBuilder()
          .addAllComponents(projectsByProjectKey.entrySet().stream().map(entry -> Components.Component.newBuilder().setKey(entry.getKey()).setName(entry.getValue().name).build())
            .collect(toList()))
          .setPaging(Common.Paging.newBuilder().setTotal(projectsByProjectKey.size()).build())
          .build()))));
    }

    private void registerComponentsTreeApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> {
        var url = "/api/components/tree.protobuf?qualifiers=FIL,UTS&component=" + projectKey;
        if (serverKind == ServerKind.SONARCLOUD) {
          url += "&organization=" + organizationKey;
        }
        url += "&ps=500&p=1";
        mockServer.stubFor(get(url)
          .willReturn(aResponse().withResponseBody(protobufBody(Components.TreeWsResponse.newBuilder()
            .addAllComponents(
              project.relativeFilePaths.stream().map(relativePath -> Components.Component.newBuilder().setKey(projectKey + ":" + relativePath).build()).collect(toList()))
            .setPaging(Common.Paging.newBuilder().setTotal(project.relativeFilePaths.size()).build())
            .build()))));
      });
    }

    private void registerSettingsApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> mockServer.stubFor(get("/api/settings/values.protobuf?component=" + projectKey)
        .willReturn(aResponse().withResponseBody(protobufBody(Settings.ValuesWsResponse.newBuilder().build())))));
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
