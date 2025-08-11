/*
 * SonarLint Core - Test Utils
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
package org.sonarsource.sonarlint.core.test.utils.server;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonar.scanner.protocol.Constants;
import org.sonar.scanner.protocol.input.ScannerInput;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.RuleKey;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.hotspot.HotspotApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarcloud.ws.Organizations;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Components;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Issues;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Measures;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.ProjectBranches;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Qualityprofiles;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Settings;
import org.sonarsource.sonarlint.core.serverconnection.AiCodeFixFeatureEnablement;
import org.sonarsource.sonarlint.core.test.utils.plugins.Plugin;
import org.sonarsource.sonarlint.core.test.utils.server.sse.SSEServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;
import static org.sonarsource.sonarlint.core.serverapi.rules.RulesApi.TAINT_REPOS_BY_LANGUAGE;
import static org.sonarsource.sonarlint.core.test.utils.ProtobufUtils.protobufBody;
import static org.sonarsource.sonarlint.core.test.utils.ProtobufUtils.protobufBodyDelimited;

public class ServerFixture {
  public static SonarQubeServerBuilder newSonarQubeServer() {
    return newSonarQubeServer((Consumer<Server>) null);
  }

  public static SonarQubeServerBuilder newSonarQubeServer(@Nullable Consumer<Server> onStart) {
    return newSonarQubeServer(onStart, "99.9");
  }

  public static SonarQubeServerBuilder newSonarQubeServer(String version) {
    return newSonarQubeServer(null, version);
  }

  public static SonarQubeServerBuilder newSonarQubeServer(@Nullable Consumer<Server> onStart, String version) {
    return new SonarQubeServerBuilder(onStart, ServerKind.SONARQUBE, version);
  }

  public static SonarQubeCloudBuilder newSonarCloudServer() {
    return newSonarCloudServer(null);
  }

  public static SonarQubeCloudBuilder newSonarCloudServer(@Nullable Consumer<Server> onStart) {
    return new SonarQubeCloudBuilder(onStart, ServerKind.SONARCLOUD, "0.0.0");
  }

  private enum ServerKind {
    SONARQUBE, SONARCLOUD
  }

  public enum ServerStatus {
    UP, DOWN
  }

  public abstract static class AbstractServerBuilder<T extends AbstractServerBuilder<T>> {
    private final Consumer<Server> onStart;
    private final ServerKind serverKind;
    private String version;
    protected final Map<String, SonarQubeCloudBuilder.SonarQubeCloudOrganizationBuilder> organizationsByKey = new HashMap<>();
    protected final Map<String, ServerProjectBuilder> projectByProjectKey = new HashMap<>();
    protected final Map<String, ServerQualityProfileBuilder> qualityProfilesByKey = new HashMap<>();
    private final Map<String, ServerPluginBuilder> pluginsByKey = new HashMap<>();
    private ServerStatus serverStatus = ServerStatus.UP;
    private ResponseCodesBuilder responseCodes = new ResponseCodesBuilder();
    protected boolean serverSentEventsEnabled;
    protected Set<String> aiCodeFixSupportedRules = Set.of();
    protected Set<String> features = new HashSet<>();
    private final List<SmartNotifications> smartNotifications = new ArrayList<>();
    private final Map<String, String> globalSettings = new HashMap<>();
    protected DopTranslationBuilder dopTranslation = new DopTranslationBuilder();

    protected AbstractServerBuilder(@Nullable Consumer<Server> onStart, ServerKind serverKind, @Nullable String version) {
      this.onStart = onStart;
      this.serverKind = serverKind;
      this.version = version;
    }

    public T withStatus(ServerStatus status) {
      serverStatus = status;
      return (T) this;
    }

    public T withGlobalSetting(String key, String value) {
      globalSettings.put(key, value);
      return (T) this;
    }

    public T withPlugin(Plugin testPlugin) {
      return withPlugin(testPlugin.getPluginKey(), plugin -> plugin.withJarPath(testPlugin.getPath()).withHash(testPlugin.getHash()));
    }

    public T withPlugin(String pluginKey, UnaryOperator<ServerPluginBuilder> pluginBuilder) {
      var builder = new ServerPluginBuilder();
      this.pluginsByKey.put(pluginKey, pluginBuilder.apply(builder));
      return (T) this;
    }

    public T withAiCodeFixSupportedRules(Set<String> supportedRules) {
      this.aiCodeFixSupportedRules = supportedRules;
      return (T) this;
    }

    public T withResponseCodes(UnaryOperator<ResponseCodesBuilder> responseCodes) {
      this.responseCodes = new ResponseCodesBuilder();
      responseCodes.apply(this.responseCodes);
      return (T) this;
    }

    public T withSmartNotifications(List<String> projects, String events) {
      this.smartNotifications.add(new SmartNotifications(projects, events));
      return (T) this;
    }

    public T withDopTranslation(UnaryOperator<DopTranslationBuilder> dopTranslationBuilder) {
      this.dopTranslation = dopTranslationBuilder.apply(new DopTranslationBuilder());
      return (T) this;
    }

    public static class DopTranslationBuilder {
      private final Map<String, ProjectBinding> projectBindings = new HashMap<>();

      public DopTranslationBuilder withProjectBinding(String repositoryUrl, String projectId, String projectKey) {
        this.projectBindings.put(repositoryUrl, new ProjectBinding(projectId, projectKey));
        return this;
      }

      record ProjectBinding(String projectId, String projectKey) {
      }

    }

    record SmartNotifications(List<String> projects, String events) {
    }

    public Server start() {
      var server = new Server(serverKind, serverStatus, version, organizationsByKey, projectByProjectKey, pluginsByKey, qualityProfilesByKey, responseCodes.build(),
        aiCodeFixSupportedRules, serverSentEventsEnabled, features, smartNotifications, globalSettings, dopTranslation);
      server.start();
      if (onStart != null) {
        onStart.accept(server);
      }
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

    public static class AiCodeFixFeatureBuilder {
      private boolean organizationEligible = true;
      private AiCodeFixFeatureEnablement enablement = AiCodeFixFeatureEnablement.DISABLED;
      private List<String> enabledProjectKeys;

      public AiCodeFixFeatureBuilder organizationEligible(boolean organizationEligible) {
        this.organizationEligible = organizationEligible;
        return this;
      }

      public AiCodeFixFeatureBuilder disabled() {
        this.enablement = AiCodeFixFeatureEnablement.DISABLED;
        return this;
      }

      public AiCodeFixFeatureBuilder enabledForProjects(String projectKey) {
        this.enablement = AiCodeFixFeatureEnablement.ENABLED_FOR_SOME_PROJECTS;
        this.enabledProjectKeys = List.of(projectKey);
        return this;
      }

      public AiCodeFixFeatureBuilder enabledForAllProjects() {
        this.enablement = AiCodeFixFeatureEnablement.ENABLED_FOR_ALL_PROJECTS;
        this.enabledProjectKeys = null;
        return this;
      }

      public AiCodeFixFeature build() {
        return new AiCodeFixFeature(enablement);
      }
    }

    public static class ResponseCodesBuilder {
      private int statusCode = 200;
      private int issueTransitionStatusCode = 200;
      private int addCommentStatusCode = 200;

      public ResponseCodesBuilder withStatusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
      }

      public ResponseCodesBuilder withIssueTransitionStatusCode(int issueTransitionStatusCode) {
        this.issueTransitionStatusCode = issueTransitionStatusCode;
        return this;
      }

      public ResponseCodesBuilder withAddCommentStatusCode(int addCommentStatusCode) {
        this.addCommentStatusCode = addCommentStatusCode;
        return this;
      }

      public ResponseCodes build() {
        return new ResponseCodes(this.statusCode, this.issueTransitionStatusCode, this.addCommentStatusCode);
      }
    }

    public record AiCodeFixFeature(AiCodeFixFeatureEnablement enablement) {
    }

    public record ResponseCodes(int statusCode, int issueTransitionStatusCode, int addCommentStatusCode) {
    }

    public static class ServerProjectBuilder {
      private String organizationKey;
      private final Map<String, ServerProjectBranchBuilder> branchesByName = new HashMap<>();
      private String mainBranchName = "main";
      private final Map<String, ServerProjectPullRequestBuilder> pullRequestsByName = new HashMap<>();
      private final List<String> qualityProfileKeys = new ArrayList<>();
      private final List<String> relativeFilePaths = new ArrayList<>();
      private String name = "MyProject";
      private String projectName;
      private AiCodeFixSuggestionBuilder aiCodeFixSuggestion;
      private boolean aiCodeFixEnabled;
      private final DopTranslationBuilder dopTranslation;
      private final String projectKey;
      private String projectId;

      private ServerProjectBuilder() {
        this(null, null, null);
      }

      private ServerProjectBuilder(@Nullable String organizationKey, @Nullable DopTranslationBuilder dopTranslation, @Nullable String projectKey) {
        this.organizationKey = organizationKey;
        this.dopTranslation = dopTranslation;
        this.projectKey = projectKey;
        this.branchesByName.put(mainBranchName, new ServerProjectBranchBuilder());
      }

      public ServerProjectBuilder withMainBranch(String branchName) {
        this.mainBranchName = branchName;
        return withBranch(branchName, builder -> builder);
      }

      public ServerProjectBuilder withBranch(String branchName) {
        return withBranch(branchName, builder -> builder);
      }

      public ServerProjectBuilder withProjectName(String projectName) {
        this.projectName = projectName;
        return this;
      }

      public ServerProjectBuilder withBranch(@Nullable String branchName, UnaryOperator<ServerProjectBranchBuilder> branchBuilder) {
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

      public ServerProjectBuilder withAiCodeFixSuggestion(UnaryOperator<AiCodeFixSuggestionBuilder> aiCodeFixSuggestionBuilder) {
        this.aiCodeFixSuggestion = new AiCodeFixSuggestionBuilder();
        aiCodeFixSuggestionBuilder.apply(aiCodeFixSuggestion);
        return this;
      }

      public ServerProjectBuilder withAiCodeFixEnabled(boolean enabled) {
        this.aiCodeFixEnabled = enabled;
        return this;
      }

      public ServerProjectBuilder withId(UUID id) {
        this.projectId = id.toString();
        return this;
      }

      public ServerProjectBuilder withBinding(String repositoryUrl) {
        if (this.projectId == null) {
          throw new IllegalStateException("withBinding() requires project id to be set via withId(UUID) beforehand");
        }
        this.dopTranslation.withProjectBinding(repositoryUrl, this.projectId, this.projectKey);
        return this;
      }

      public record ServerDependencyRisk(
        String id,
        String type,
        String severity,
        String quality,
        String status,
        String packageName,
        String packageVersion,
        List<String> transitions) {
      }

      public static class ServerProjectBranchBuilder {
        protected final Collection<ServerHotspot> hotspots = new ArrayList<>();
        protected final Collection<ServerIssue> issues = new ArrayList<>();
        private final Collection<ServerIssue> taintIssues = new ArrayList<>();
        private final Collection<ServerDependencyRisk> dependencyRisks = new ArrayList<>();
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
          String status, @Nullable String resolution, Instant creationDate, TextRange textRange) {
          this.issues.add(new ServerIssue(issueKey, ruleKey, message, author, filePath, status, resolution, creationDate, textRange, RuleType.BUG));
          return this;
        }

        public ServerProjectBranchBuilder withIssue(String issueKey, String ruleKey, String message, String author, String filePath,
          String hash, Constants.Severity severity, RuleType ruleType, String status, String resolution, Instant creationDate, TextRange textRange) {
          this.issues.add(new ServerIssue(issueKey, ruleKey, message, author, filePath, status, resolution, creationDate, textRange, ruleType, hash, severity));
          return this;
        }

        public ServerProjectBranchBuilder withIssue(String issueKey, String ruleKey, String message, String author, String filePath,
          String hash, Constants.Severity severity, RuleType ruleType, String status, String resolution, Instant creationDate, TextRange textRange,
          Map<SoftwareQuality, ImpactSeverity> impacts) {
          this.issues.add(new ServerIssue(issueKey, ruleKey, message, author, filePath, status, resolution, creationDate, textRange, ruleType, hash, severity, impacts));
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

        public ServerProjectBranchBuilder withDependencyRisk(ServerDependencyRisk dependencyRisk) {
          this.dependencyRisks.add(dependencyRisk);
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
          private String hash;
          private Constants.Severity severity;
          private boolean manualSeverity = false;
          private Map<SoftwareQuality, ImpactSeverity> impacts;

          private ServerIssue(String issueKey, String ruleKey, String message, String author, String filePath, String status,
            String resolution, Instant introductionDate, TextRange textRange, RuleType ruleType, String hash, Constants.Severity severity,
            Map<SoftwareQuality, ImpactSeverity> impacts) {
            this(issueKey, ruleKey, message, author, filePath, status, resolution, introductionDate, textRange, ruleType, hash, severity);
            this.impacts = impacts;
          }

          private ServerIssue(String issueKey, String ruleKey, String message, String author, String filePath, String status,
            @Nullable String resolution, Instant introductionDate, TextRange textRange, RuleType ruleType, String hash, Constants.Severity severity) {
            this(issueKey, ruleKey, message, author, filePath, status, resolution, introductionDate, textRange, ruleType);
            this.hash = hash;
            this.severity = severity;
            this.manualSeverity = true;
          }

          private ServerIssue(String issueKey, String ruleKey, String message, String author, String filePath, String status,
            @Nullable String resolution, Instant introductionDate, TextRange textRange, RuleType ruleType) {
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
            this.hash = "hash";
            this.severity = Constants.Severity.BLOCKER;
            this.impacts = Collections.emptyMap();
          }

          public String getFilePath() {
            return filePath;
          }
        }
      }

      public static class AiCodeFixSuggestionBuilder {
        private UUID id = UUID.randomUUID();
        private String explanation = "default";
        private final List<AiCodeFixChange> changes = new ArrayList<>();

        public AiCodeFixSuggestionBuilder withId(UUID id) {
          this.id = id;
          return this;
        }

        public AiCodeFixSuggestionBuilder withExplanation(String explanation) {
          this.explanation = explanation;
          return this;
        }

        public AiCodeFixSuggestionBuilder withChange(int startLine, int endLine, String newCode) {
          this.changes.add(new AiCodeFixChange(startLine, endLine, newCode));
          return this;
        }

        public AiCodeFix build() {
          return new AiCodeFix(id, explanation, changes);
        }
      }

      public record AiCodeFix(UUID id, String explanation, List<AiCodeFixChange> changes) {
      }

      public record AiCodeFixChange(int startLine, int endLine, String newCode) {

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
      private final String organizationKey;
      private String languageKey = "lang";
      private final Map<String, ServerActiveRuleBuilder> activeRulesByKey = new HashMap<>();

      public ServerQualityProfileBuilder(@Nullable String organizationKey) {
        this.organizationKey = organizationKey;
      }

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

  public static class SonarQubeServerBuilder extends AbstractServerBuilder<SonarQubeServerBuilder> {

    public SonarQubeServerBuilder(@org.jetbrains.annotations.Nullable Consumer<Server> onStart, ServerKind serverKind, @Nullable String version) {
      super(onStart, serverKind, version);
    }

    public SonarQubeServerBuilder withProject(String projectKey) {
      return withProject(projectKey, UnaryOperator.identity());
    }

    public SonarQubeServerBuilder withProject(String projectKey, UnaryOperator<ServerProjectBuilder> projectBuilder) {
      var builder = new ServerProjectBuilder(null, this.dopTranslation, projectKey);
      this.projectByProjectKey.put(projectKey, projectBuilder.apply(builder));
      return this;
    }

    public SonarQubeServerBuilder withQualityProfile(String qualityProfileKey, UnaryOperator<ServerQualityProfileBuilder> qualityProfileBuilder) {
      var builder = new ServerQualityProfileBuilder(null);
      this.qualityProfilesByKey.put(qualityProfileKey, qualityProfileBuilder.apply(builder));
      return this;
    }

    public SonarQubeServerBuilder withServerSentEventsEnabled() {
      this.serverSentEventsEnabled = true;
      return this;
    }

    public SonarQubeServerBuilder withFeature(String featureName) {
      this.features.add(featureName);
      return this;
    }
  }

  public static class SonarQubeCloudBuilder extends AbstractServerBuilder<SonarQubeCloudBuilder> {

    public SonarQubeCloudBuilder(@org.jetbrains.annotations.Nullable Consumer<Server> onStart, ServerKind serverKind, @Nullable String version) {
      super(onStart, serverKind, version);
    }

    public SonarQubeCloudBuilder withOrganization(String organizationKey) {
      return withOrganization(organizationKey, UnaryOperator.identity());
    }

    public SonarQubeCloudBuilder withOrganization(String organizationKey, UnaryOperator<SonarQubeCloudOrganizationBuilder> organizationBuilder) {
      var builder = new SonarQubeCloudOrganizationBuilder(organizationKey, qualityProfilesByKey, projectByProjectKey, this.dopTranslation);
      this.organizationsByKey.put(organizationKey, organizationBuilder.apply(builder));
      return this;
    }

    public static class SonarQubeCloudOrganizationBuilder {
      private final String organizationKey;
      private final Map<String, ServerQualityProfileBuilder> qualityProfilesByKey;
      private final Map<String, ServerProjectBuilder> projectByProjectKey;
      public String name = "OrgName";
      public String description = "OrgDescription";
      private final String id = UUID.randomUUID().toString();
      private final UUID uuidV4 = UUID.randomUUID();
      private AbstractServerBuilder.AiCodeFixFeatureBuilder aiCodeFixFeature = new AiCodeFixFeatureBuilder();
      private boolean isCurrentUserMember = true;
      private final DopTranslationBuilder dopTranslation;

      public SonarQubeCloudOrganizationBuilder(String organizationKey, Map<String, ServerQualityProfileBuilder> qualityProfilesByKey,
        Map<String, ServerProjectBuilder> projectByProjectKey, DopTranslationBuilder dopTranslation) {
        this.organizationKey = organizationKey;
        this.qualityProfilesByKey = qualityProfilesByKey;
        this.projectByProjectKey = projectByProjectKey;
        this.dopTranslation = dopTranslation;
      }

      public SonarQubeCloudOrganizationBuilder withQualityProfile(String qualityProfileKey, UnaryOperator<ServerQualityProfileBuilder> qualityProfileBuilder) {
        var builder = new ServerQualityProfileBuilder(organizationKey);
        this.qualityProfilesByKey.put(qualityProfileKey, qualityProfileBuilder.apply(builder));
        return this;
      }

      public SonarQubeCloudOrganizationBuilder withProject(String projectKey) {
        return withProject(projectKey, UnaryOperator.identity());
      }

      public SonarQubeCloudOrganizationBuilder withProject(String projectKey, UnaryOperator<ServerProjectBuilder> projectBuilder) {
        var builder = new ServerProjectBuilder(organizationKey, dopTranslation, projectKey);
        this.projectByProjectKey.put(projectKey, projectBuilder.apply(builder));
        return this;
      }

      public SonarQubeCloudOrganizationBuilder withAiCodeFixFeature(UnaryOperator<AiCodeFixFeatureBuilder> aiCodeFixBuilder) {
        this.aiCodeFixFeature = new AiCodeFixFeatureBuilder();
        aiCodeFixBuilder.apply(aiCodeFixFeature);
        return this;
      }

      public SonarQubeCloudOrganizationBuilder withCurrentUserMember(boolean isCurrentUserMember) {
        this.isCurrentUserMember = isCurrentUserMember;
        return this;
      }
    }
  }

  public static class Server {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ").withZone(ZoneId.from(ZoneOffset.UTC));
    public static final String API_COMPONENTS_SHOW_PROTOBUF_COMPONENT = "/api/components/show.protobuf?component=";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String APPLICATION_JSON = "application/json";

    private final WireMockServer mockServer = new WireMockServer(options().dynamicPort());

    private final ServerKind serverKind;
    private final ServerStatus serverStatus;
    @Nullable
    private final Version version;
    private final Map<String, SonarQubeCloudBuilder.SonarQubeCloudOrganizationBuilder> organizationsByKey;
    private final Map<String, AbstractServerBuilder.ServerProjectBuilder> projectsByProjectKey;
    private final Map<String, AbstractServerBuilder.ServerPluginBuilder> pluginsByKey;
    private final Map<String, AbstractServerBuilder.ServerQualityProfileBuilder> qualityProfilesByKey;
    private final AbstractServerBuilder.ResponseCodes responseCodes;
    private final List<AbstractServerBuilder.SmartNotifications> smartNotifications;
    private final Map<String, String> globalSettings;
    private final Set<String> aiCodeFixSupportedRules;
    private final boolean serverSentEventsEnabled;
    private final AbstractServerBuilder.DopTranslationBuilder dopTranslation;
    private final Set<String> features;
    private SSEServer sseServer;

    public Server(ServerKind serverKind, ServerStatus serverStatus, @Nullable String version,
      Map<String, SonarQubeCloudBuilder.SonarQubeCloudOrganizationBuilder> organizationsByKey, Map<String, AbstractServerBuilder.ServerProjectBuilder> projectsByProjectKey,
      Map<String, AbstractServerBuilder.ServerPluginBuilder> pluginsByKey, Map<String, AbstractServerBuilder.ServerQualityProfileBuilder> qualityProfilesByKey,
      AbstractServerBuilder.ResponseCodes responseCodes, Set<String> aiCodeFixSupportedRules, boolean serverSentEventsEnabled, Set<String> features,
      List<AbstractServerBuilder.SmartNotifications> smartNotifications, Map<String, String> globalSettings, AbstractServerBuilder.DopTranslationBuilder dopTranslation) {
      this.serverKind = serverKind;
      this.serverStatus = serverStatus;
      this.version = version != null ? Version.create(version) : null;
      this.organizationsByKey = organizationsByKey;
      this.projectsByProjectKey = projectsByProjectKey;
      this.pluginsByKey = pluginsByKey;
      this.qualityProfilesByKey = qualityProfilesByKey;
      this.responseCodes = responseCodes;
      this.aiCodeFixSupportedRules = aiCodeFixSupportedRules;
      this.serverSentEventsEnabled = serverSentEventsEnabled;
      this.features = features;
      this.smartNotifications = smartNotifications;
      this.globalSettings = globalSettings;
      this.dopTranslation = dopTranslation;
    }

    public void start() {
      mockServer.start();
      if (serverSentEventsEnabled) {
        sseServer = new SSEServer();
        sseServer.start();
      }
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
        registerComponentApiResponses();
        registerFixSuggestionsApiResponses();
        registerOrganizationApiResponses();
        registerPushApiResponses();
        registerFeaturesApiResponses();
        registerScaApiResponses();
        registerDopTranslationApiResponses();
      }
    }

    private void registerComponentApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> {
        if (project.projectName != null) {
          mockServer.stubFor(get(API_COMPONENTS_SHOW_PROTOBUF_COMPONENT + UrlUtils.urlEncode(projectKey))
            .willReturn(aResponse().withResponseBody(protobufBody(
              Components.ShowWsResponse.newBuilder()
                .setComponent(Components.Component.newBuilder().setKey(projectKey).setName(project.projectName).build()).build()))));
        }
      });
    }

    public void registerSystemApiResponses() {
      // API is public, so it can't return 401 or 403 status
      var statusesToSkip = Set.of(401, 403);
      var status = statusesToSkip.contains(responseCodes.statusCode) ? 200 : responseCodes.statusCode;
      mockServer.stubFor(get("/api/system/status")
        .willReturn(aResponse().withStatus(status).withBody("{\"id\": \"20160308094653\",\"version\": \"" + version + "\",\"status\": " +
          "\"" + serverStatus + "\"}")));
    }

    private void registerPluginsApiResponses() {
      registerPluginsInstalledResponses();
      registerPluginsDownloadResponses();
    }

    private void registerPluginsInstalledResponses() {
      mockServer.stubFor(get("/api/plugins/installed")
        .willReturn(aResponse().withStatus(responseCodes.statusCode).withBody("{\"plugins\": [" +
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
            .willReturn(aResponse().withStatus(responseCodes.statusCode).withBody(pluginContent)));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }

    private void registerQualityProfilesApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> {
        var urlBuilder = new StringBuilder("/api/qualityprofiles/search.protobuf?project=" + projectKey);
        if (project.organizationKey != null) {
          urlBuilder.append("&organization=").append(project.organizationKey);
        }
        mockServer.stubFor(get(urlBuilder.toString())
          .willReturn(aResponse().withStatus(responseCodes.statusCode).withResponseBody(protobufBody(Qualityprofiles.SearchWsResponse.newBuilder().addAllProfiles(
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
            }).toList()).build()))));
      });
    }

    private void registerRulesApiResponses() {
      qualityProfilesByKey.forEach((qualityProfileKey, qualityProfile) -> {
        var url = "/api/rules/search.protobuf?qprofile=" + qualityProfileKey;
        if (qualityProfile.organizationKey != null) {
          url += "&organization=" + qualityProfile.organizationKey;
        }
        url += "&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY,SECURITY_HOTSPOT&s=key&ps=500&p=1";
        mockServer.stubFor(get(url)
          .willReturn(aResponse().withStatus(responseCodes.statusCode).withResponseBody(protobufBody(Rules.SearchResponse.newBuilder()
            .addAllRules(qualityProfile.activeRulesByKey.entrySet().stream().map(entry -> Rules.Rule.newBuilder()
              .setKey(entry.getKey())
              .setSeverity(entry.getValue().issueSeverity.name())
              .build()).toList())
            .setActives(Rules.Actives.newBuilder()
              .putAllActives(qualityProfile.activeRulesByKey.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> Rules.ActiveList.newBuilder()
                .addActiveList(Rules.Active.newBuilder().setSeverity(entry.getValue().issueSeverity.name()).build()).build())))
              .build())
            .setPaging(Common.Paging.newBuilder().setTotal(qualityProfile.activeRulesByKey.size()).build())
            .build()))));
      });
      var taintActiveRulesByKey = qualityProfilesByKey.values().stream().flatMap(qp -> qp.activeRulesByKey.entrySet().stream())
        .filter(entry -> TAINT_REPOS_BY_LANGUAGE.containsValue(entry.getKey())).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
      qualityProfilesByKey.values().stream().map(qp -> qp.organizationKey).collect(Collectors.toSet()).forEach(organizationKey -> {
        var url = "/api/rules/search.protobuf?repositories=roslyn.sonaranalyzer.security.cs,javasecurity,jssecurity,phpsecurity,pythonsecurity,tssecurity";
        if (organizationKey != null) {
          url += "&organization=" + organizationKey;
        }
        url += "&f=repo&s=key&ps=500&p=1";
        mockServer.stubFor(get(url)
          .willReturn(aResponse().withStatus(responseCodes.statusCode).withResponseBody(protobufBody(Rules.SearchResponse.newBuilder()
            .addAllRules(taintActiveRulesByKey.entrySet().stream().map(entry -> Rules.Rule.newBuilder()
              .setKey(entry.getKey())
              .setSeverity(entry.getValue().issueSeverity.name())
              .build()).toList())
            .setActives(Rules.Actives.newBuilder()
              .putAllActives(taintActiveRulesByKey.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> Rules.ActiveList.newBuilder()
                .addActiveList(Rules.Active.newBuilder().setSeverity(entry.getValue().issueSeverity.name()).build()).build())))
              .build())
            .setPaging(Common.Paging.newBuilder().setTotal(taintActiveRulesByKey.size()).build())
            .build()))));
      });

      var activeRules = qualityProfilesByKey.values().stream().flatMap(qp -> qp.activeRulesByKey.entrySet().stream()).toList();
      activeRules.forEach(entry -> {
        var ruleKey = entry.getKey();
        var rule = entry.getValue();
        var rulesShowUrl = "/api/rules/show.protobuf?key=" + ruleKey;
        mockServer.stubFor(get(rulesShowUrl)
          .willReturn(aResponse().withStatus(responseCodes.statusCode).withResponseBody(protobufBody(Rules.ShowResponse.newBuilder()
            .setRule(Rules.Rule.newBuilder()
              .setKey(ruleKey)
              .setName("fakeName")
              .setLang("java")
              .setHtmlNote("htmlNote")
              .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder().build())
              .setCleanCodeAttribute(Common.CleanCodeAttribute.CONVENTIONAL)
              .setEducationPrinciples(Rules.Rule.EducationPrinciples.newBuilder().build())
              .setSeverity(rule.issueSeverity.name())
              .setType(Common.RuleType.BUG)
              .setHtmlDesc("htmlDesc")
              .setImpacts(Rules.Rule.Impacts.newBuilder().build())
              .build())
            .build()))));
      });
    }

    private void registerProjectBranchesApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> mockServer.stubFor(get("/api/project_branches/list.protobuf?project=" + projectKey)
        .willReturn(aResponse().withStatus(responseCodes.statusCode).withResponseBody(protobufBody(ProjectBranches.ListWsResponse.newBuilder()
          .addAllBranches(project.branchesByName.keySet().stream()
            .filter(Objects::nonNull)
            .map(branchName -> ProjectBranches.Branch.newBuilder().setName(branchName).setIsMain(project.mainBranchName.equals(branchName)).setType(Common.BranchType.LONG).build())
            .toList())
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
          .collect(groupingBy(AbstractServerBuilder.ServerProjectBuilder.ServerProjectBranchBuilder.ServerHotspot::getFilePath,
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
        var allMessages = messagesPerFilePath.values().stream().flatMap(Collection::stream).toList();
        mockServer.stubFor(get("/api/hotspots/search.protobuf?projectKey=" + projectKey + branchParameter + "&ps=500&p=1")
          .willReturn(aResponse().withResponseBody(protobufBody(Hotspots.SearchWsResponse.newBuilder()
            .addAllComponents(messagesPerFilePath.keySet().stream().map(filePath -> Hotspots.Component.newBuilder().setPath(filePath).setKey(projectKey + ":" + filePath).build())
              .toList())
            .setPaging(Common.Paging.newBuilder().setTotal(allMessages.size()).build())
            .addAllHotspots(allMessages).build()))));
      }));
    }

    private void registerSearchIssueApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> {
        project.pullRequestsByName.forEach((pullRequestName, pullRequest) -> {
          var issuesPerFilePath = getIssuesPerFilePath(projectKey, pullRequest);

          var allIssues = issuesPerFilePath.values().stream().flatMap(Collection::stream).toList();

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
                    .setTextRange(issue.getTextRange()).setComponent(issue.getComponent())
                    .addAllImpacts(issue.getImpactsList().stream().map(i -> Common.Impact.newBuilder()
                      .setSoftwareQuality(i.getSoftwareQuality())
                      .setSeverity(i.getSeverity())
                      .build()).toList())
                    .build())
                .addAllComponents(
                  issuesPerFilePath.keySet().stream().map(issues -> Issues.Component.newBuilder().setPath(issues).setKey(projectKey + ":" + issues).build()).toList())
                .setRules(Issues.SearchWsResponse.newBuilder().getRulesBuilder().addRules(Common.Rule.newBuilder().setKey(issue.getRule()).build()))
                .setPaging(Common.Paging.newBuilder().setTotal(1).build())
                .build()))));
          });
        });
        project.branchesByName.forEach((branchName, branch) -> {
          var issuesPerFilePath = getIssuesPerFilePath(projectKey, branch);

          var allIssues = issuesPerFilePath.values().stream().flatMap(Collection::stream).toList();

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
                  issuesPerFilePath.keySet().stream().map(issues -> Issues.Component.newBuilder().setPath(issues).setKey(projectKey + ":" + issues).build()).toList())
                .setRules(Issues.SearchWsResponse.newBuilder().getRulesBuilder().addRules(Common.Rule.newBuilder().setKey(issue.getRule()).build()))
                .setPaging(Common.Paging.newBuilder().setTotal(1).build())
                .build()))));
          });

          var vulnerabilities = allIssues.stream().filter(issue -> issue.getType() == Common.RuleType.VULNERABILITY).toList();
          var searchUrl = "/api/issues/search.protobuf?statuses=OPEN,CONFIRMED,REOPENED,RESOLVED&types=VULNERABILITY&componentKeys=" + projectKey + "&rules=&branch=" + branchName
            + "&ps=500&p=1";
          mockServer.stubFor(get(searchUrl)
            .willReturn(aResponse().withResponseBody(protobufBody(Issues.SearchWsResponse.newBuilder()
              .addAllIssues(
                vulnerabilities.stream().map(vulnerability -> Issues.Issue.newBuilder()
                  .setKey(vulnerability.getKey()).setRule(vulnerability.getRule()).setCreationDate(vulnerability.getCreationDate()).setMessage(vulnerability.getMessage())
                  .setTextRange(vulnerability.getTextRange()).setComponent(vulnerability.getComponent()).setType(vulnerability.getType()).build()).toList())
              .addAllComponents(
                issuesPerFilePath.keySet().stream().map(issues -> Issues.Component.newBuilder().setPath(issues).setKey(projectKey + ":" + issues).build()).toList())
              .setPaging(Common.Paging.newBuilder().setTotal(vulnerabilities.size()).build())
              .build()))));
        });
      });
    }

    @NotNull
    private static Map<String, List<Issues.Issue>> getIssuesPerFilePath(String projectKey,
      AbstractServerBuilder.ServerProjectBuilder.ServerProjectBranchBuilder pullRequestOrBranch) {
      return Stream.concat(pullRequestOrBranch.issues.stream(), pullRequestOrBranch.taintIssues.stream())
        .collect(groupingBy(AbstractServerBuilder.ServerProjectBuilder.ServerProjectBranchBuilder.ServerIssue::getFilePath,
          mapping(issue -> Issues.Issue.newBuilder()
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
            .setType(Common.RuleType.valueOf(issue.ruleType.name()))
            .addAllImpacts(issue.impacts.entrySet().stream().map(i -> Common.Impact.newBuilder()
              .setSoftwareQuality(Common.SoftwareQuality.valueOf(i.getKey().name()))
              .setSeverity(Common.ImpactSeverity.valueOf(i.getValue().name()))
              .build()).toList())
            .build(), toList())));
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
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) ->
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
        })));
    }

    private void registerHotspotsStatusChangeApiResponses() {
      mockServer.stubFor(post("/api/hotspots/change_status").willReturn(aResponse().withStatus(responseCodes.statusCode)));
    }

    private void registerIssuesApiResponses() {
      if (version != null) {
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
              .setChecksum(issue.hash)
              .setMsg(issue.message)
              .setLine(issue.textRange.getStartLine())
              .setCreationDate(issue.introductionDate.toEpochMilli())
              .setPath(issue.filePath)
              .setType(issue.ruleType.name())
              .setManualSeverity(issue.manualSeverity)
              .setSeverity(issue.severity)
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
      mockServer.stubFor(post("/api/issues/do_transition").willReturn(aResponse().withStatus(responseCodes.issueTransitionStatusCode)));
    }

    private void registerAddIssueCommentApiResponses() {
      mockServer.stubFor(post("/api/issues/add_comment").willReturn(aResponse().withStatus(responseCodes.addCommentStatusCode)));
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
          .addAllImpacts(issue.impacts.entrySet().stream().map(i -> Common.Impact.newBuilder()
            .setSoftwareQuality(Common.SoftwareQuality.valueOf(i.getKey().name()))
            .setSeverity(Common.ImpactSeverity.valueOf(i.getValue().name()))
            .build()).toList())
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
      mockServer.stubFor(post("/api/issues/anticipated_transitions?projectKey=projectKey").willReturn(aResponse().withStatus(responseCodes.statusCode)));
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
      mockServer.stubFor(get("/api/developers/search_events?projects=&from=").willReturn(aResponse().withStatus(responseCodes.statusCode)));
      smartNotifications.forEach(sn -> {
        var projects = sn.projects.stream().map(UrlUtils::urlEncode).collect(Collectors.joining(","));
        mockServer.stubFor(get(urlMatching("\\Q/api/developers/search_events?projects=" + projects + "\\E(&from=.*)"))
          .willReturn(aResponse().withBody(sn.events).withStatus(responseCodes.statusCode)));
      });
    }

    private void registerMeasuresApiResponses() {
      var periodFieldName = this.serverKind == ServerKind.SONARCLOUD ? "periods" : "period";
      projectsByProjectKey.forEach((projectKey, project) -> {
        var uriPath = "/api/measures/component.protobuf?additionalFields=" + periodFieldName + "&metricKeys=projects&component=" + projectKey;
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
      registerComponentsShowApiResponses();
      registerComponentsTreeApiResponses();

      projectsByProjectKey.forEach((projectKey, project) -> {
        var organizationKey = project.organizationKey;
        if (organizationKey == null) {
          return;
        }
        var projectName = project.name;
        if (projectName == null) {
          return;
        }

        dopTranslation.projectBindings.entrySet().stream()
          .filter(e -> projectKey.equals(e.getValue().projectKey()))
          .forEach(e -> {
            var projectBinding = e.getValue();
            var endpoint = "/api/components/search_projects?projectIds=" + projectBinding.projectId() + "&organization=" + organizationKey;
            var body = """
              {"components":[{"key":"%s","name":"%s"}]}
              """.formatted(projectKey, projectName);
            mockServer.stubFor(get(urlEqualTo(endpoint))
              .willReturn(aResponse()
                .withStatus(responseCodes.statusCode)
                .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                .withBody(body)));
          });
      });
    }

    private void registerComponentsSearchApiResponses() {
      var projectsByOrganizationKey = projectsByProjectKey.entrySet().stream()
        .collect(groupingBy(e -> Optional.ofNullable(e.getValue().organizationKey), HashMap::new, toList()));
      if (projectsByOrganizationKey.isEmpty()) {
        projectsByOrganizationKey.put(Optional.empty(), List.of());
      }
      organizationsByKey.forEach((organizationKey, organization) -> {
        if (!projectsByOrganizationKey.containsKey(Optional.of(organizationKey))) {
          projectsByOrganizationKey.put(Optional.of(organizationKey), List.of());
        }
      });
      projectsByOrganizationKey
        .forEach((organizationKey, projects) -> {
          var url = "/api/components/search.protobuf?qualifiers=TRK";
          if (organizationKey.isPresent()) {
            url += "&organization=" + organizationKey.get();
          }
          url += "&ps=500&p=1";
          mockServer.stubFor(get(url)
            .willReturn(aResponse().withResponseBody(protobufBody(Components.SearchWsResponse.newBuilder()
              .addAllComponents(projects.stream().map(entry -> Components.Component.newBuilder().setKey(entry.getKey()).setName(entry.getValue().name).build())
                .toList())
              .setPaging(Common.Paging.newBuilder().setTotal(projectsByProjectKey.size()).build())
              .build()))));
        });
    }

    private void registerComponentsShowApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> {
        var url = API_COMPONENTS_SHOW_PROTOBUF_COMPONENT + projectKey;
        var projectComponent = projectsByProjectKey.entrySet().stream().filter(e -> e.getKey().equals(projectKey))
          .map(entry -> Components.Component.newBuilder()
            .setKey(entry.getKey())
            .setName(entry.getValue().name)
            .setIsAiCodeFixEnabled(project.aiCodeFixEnabled)
            .build())
          .findFirst().get();
        mockServer.stubFor(get(url)
          .willReturn(aResponse().withResponseBody(protobufBody(Components.ShowWsResponse.newBuilder()
            .setComponent(projectComponent)
            .build()))));
      });
    }

    private void registerComponentsTreeApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> {
        var url = "/api/components/tree.protobuf?qualifiers=FIL,UTS&component=" + projectKey;
        if (project.organizationKey != null) {
          url += "&organization=" + project.organizationKey;
        }
        url += "&ps=500&p=1";
        mockServer.stubFor(get(url)
          .willReturn(aResponse().withResponseBody(protobufBody(Components.TreeWsResponse.newBuilder()
            .addAllComponents(
              project.relativeFilePaths.stream().map(relativePath -> Components.Component.newBuilder().setKey(projectKey + ":" + relativePath).build()).toList())
            .setPaging(Common.Paging.newBuilder().setTotal(project.relativeFilePaths.size()).build())
            .build()))));
      });
    }

    private void registerSettingsApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> mockServer.stubFor(get("/api/settings/values.protobuf?component=" + projectKey)
        .willReturn(aResponse().withResponseBody(protobufBody(Settings.ValuesWsResponse.newBuilder().build())))));

      var settingsBuilder = Settings.ValuesWsResponse.newBuilder()
        .addSettings(Settings.Setting.newBuilder()
          .setKey("sonar.earlyAccess.misra.enabled")
          .setValue("false"));
      var mqrModeAvailable = this.version != null && this.version.compareToIgnoreQualifier(Version.create("10.8")) >= 0;
      if (mqrModeAvailable) {
        settingsBuilder
          .addSettings(Settings.Setting.newBuilder()
            .setKey("sonar.multi-quality-mode.enabled")
            .setValue("true"));
      }
      settingsBuilder.addAllSettings(globalSettings.entrySet().stream().map(entry -> Settings.Setting.newBuilder()
        .setKey(entry.getKey())
        .setValue(entry.getValue()).build()).toList());
      mockServer.stubFor(get("/api/settings/values.protobuf")
        .willReturn(aResponse().withResponseBody(protobufBody(settingsBuilder.build()))));
    }

    private void registerFixSuggestionsApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> {
        try {
          if (project.aiCodeFixSuggestion != null) {
            mockServer.stubFor(post(serverKind == ServerKind.SONARCLOUD ? "/fix-suggestions/ai-suggestions" : "/api/v2/fix-suggestions/ai-suggestions")
              .willReturn(jsonResponse(
                new ObjectMapper().setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY).writeValueAsString(project.aiCodeFixSuggestion.build()),
                responseCodes.statusCode)));
          }
        } catch (JsonProcessingException e) {
          throw new IllegalArgumentException(e);
        }
      });

      mockServer.stubFor(get(serverKind == ServerKind.SONARCLOUD ? "/fix-suggestions/supported-rules" : "/api/v2/fix-suggestions/supported-rules")
        .willReturn(jsonResponse("{\"rules\": [" + String.join(", ", aiCodeFixSupportedRules.stream().map(rule -> "\"" + rule + "\"").toList()) + "]}", responseCodes.statusCode)));
      organizationsByKey.forEach((organizationKey, organization) -> {
        var enabledProjectKeys = organization.aiCodeFixFeature.enabledProjectKeys == null ? null
          : ("[" + String.join(", ", organization.aiCodeFixFeature.enabledProjectKeys) + "]");
        // this payload will change in the future and will need to update the fixture to have up to date payload example
        var aiCodeFix = "\"aiCodeFix\": {" +
          "\"enablement\": \"" + organization.aiCodeFixFeature.enablement.name() + "\"," +
          "\"enabledProjectKeys\":" + enabledProjectKeys + "," +
          "\"organizationEligible\": " + organization.aiCodeFixFeature.organizationEligible +
          "}";
        var response = "{\"enablement\": \"" + organization.aiCodeFixFeature.enablement.name() + "\", \"organizationEligible\": "
          + organization.aiCodeFixFeature.organizationEligible + ",  \"enabledProjectKeys\": " + enabledProjectKeys + "," + aiCodeFix + "}";
        mockServer.stubFor(get("/fix-suggestions/organization-configs/" + organization.id)
          .willReturn(jsonResponse(response, responseCodes.statusCode)));
      });
    }

    private void registerOrganizationApiResponses() {
      organizationsByKey
        .forEach((organizationKey, organization) -> mockServer.stubFor(get("/organizations/organizations?organizationKey=" + organizationKey + "&excludeEligibility=true")
          .willReturn(jsonResponse("[{\"id\": \"" + organization.id + "\", \"uuidV4\": \"" + organization.uuidV4 + "\"}]", responseCodes.statusCode))));
      mockServer.stubFor(get("/api/organizations/search.protobuf?member=true&ps=500&p=1")
        .willReturn(aResponse().withStatus(responseCodes.statusCode).withResponseBody(protobufBody(Organizations.SearchWsResponse.newBuilder()
          .addAllOrganizations(organizationsByKey.entrySet().stream()
            .filter(e -> e.getValue().isCurrentUserMember)
            .map(e -> Organizations.Organization.newBuilder()
              .setKey(e.getKey())
              .setName(e.getValue().name)
              .setDescription(e.getValue().description)
              .build())
            .toList())
          .setPaging(Common.Paging.newBuilder().setTotal(organizationsByKey.size()).build())
          .build()))));
    }

    private void registerPushApiResponses() {
      if (!serverSentEventsEnabled) {
        return;
      }
      // wiremock does not support SSE, so we redirect to our custom SSE server
      mockServer.stubFor(get(urlPathEqualTo("/api/push/sonarlint_events"))
        .withQueryParam("projectKeys", equalTo(String.join(",", projectsByProjectKey.keySet())))
        .withQueryParam("languages", new AnythingPattern())
        .willReturn(aResponse()
          .withStatus(302)
          .withHeader("Location", sseServer.getUrl())));
    }

    private void registerFeaturesApiResponses() {
      mockServer.stubFor(get("/api/features/list")
        .willReturn(jsonResponse(
          "[" + String.join(", ", features.stream().map(feature -> "\"" + feature + "\"").toList()) + "]",
          responseCodes.statusCode)));
    }

    private void registerScaApiResponses() {
      projectsByProjectKey.forEach((projectKey, project) -> project.branchesByName.forEach((branchName, branch) -> {
        var dependencyRisksJson = branch.dependencyRisks.stream()
          .map(issue -> String.format("""
            {
              "key": "%s",
              "type": "%s",
              "severity": "%s",
              "quality": "%s",
              "status": "%s",
              "release": {
                "packageName": "%s",
                "version": "%s"
              },
              "transitions": [%s]
            }
            """, issue.id(), issue.type(), issue.severity(), issue.quality(), issue.status(), issue.packageName(), issue.packageVersion()
            , String.join(", ", issue.transitions())))
          .collect(Collectors.joining(","));

        var responseJson = String.format("""
          {
            "issuesReleases": [%s],
            "page": {
              "pageIndex": 1,
              "pageSize": %d,
              "total": %d
            }
          }
          """, dependencyRisksJson, branch.dependencyRisks.size(), branch.dependencyRisks.size());

        mockServer.stubFor(get("/api/v2/sca/issues-releases?projectKey=" + projectKey + "&branchName=" + branchName + "&pageSize=500&pageIndex=1")
          .willReturn(jsonResponse(responseJson, responseCodes.statusCode)));
        mockServer.stubFor(post("/api/v2/sca/issues-releases/change-status")
          .willReturn(aResponse().withStatus(200)));
      }));
    }

    public void pushEvent(String eventPayload) {
      if (!serverSentEventsEnabled) {
        throw new IllegalStateException("Please use withServerSentEventsEnabled() first");
      }
      sseServer.sendEventToAllClients(eventPayload);
    }

    public void shutdown() {
      mockServer.stop();
      if (sseServer != null) {
        sseServer.stop();
      }
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

    private void registerDopTranslationApiResponses() {
      dopTranslation.projectBindings.forEach((repositoryUrl, projectBinding) -> {
        var encodedUrl = UrlUtils.urlEncode(repositoryUrl);
        if (serverKind == ServerKind.SONARCLOUD) {
          var endpoint = "/dop-translation/project-bindings?url=" + encodedUrl;
          var responseBody = """
            {"bindings":[{"projectId":"%s"}]}
            """.formatted(projectBinding.projectId());
          mockServer.stubFor(get(urlEqualTo(endpoint))
            .willReturn(aResponse()
              .withStatus(200)
              .withHeader(CONTENT_TYPE, APPLICATION_JSON)
              .withBody(responseBody)));
        } else {
          var endpoint = "/api/v2/dop-translation/project-bindings?repositoryUrl=" + encodedUrl;
          var responseBody = """
            {"projectBindings":[{"projectId":"%s","projectKey":"%s"}]}
            """.formatted(projectBinding.projectId(), projectBinding.projectKey());
          mockServer.stubFor(get(urlEqualTo(endpoint))
            .willReturn(aResponse()
              .withStatus(200)
              .withHeader(CONTENT_TYPE, APPLICATION_JSON)
              .withBody(responseBody)));
        }
      });
    }
  }
}
