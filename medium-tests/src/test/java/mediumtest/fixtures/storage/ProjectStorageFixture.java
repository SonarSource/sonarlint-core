/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2024 SonarSource SA
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
package mediumtest.fixtures.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.util.CompressBackupUtil;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.HotspotReviewStatusBinding;
import org.sonarsource.sonarlint.core.serverconnection.storage.InstantBinding;
import org.sonarsource.sonarlint.core.serverconnection.storage.IssueSeverityBinding;
import org.sonarsource.sonarlint.core.serverconnection.storage.IssueTypeBinding;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.serverconnection.storage.UuidBinding;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.sonarsource.sonarlint.core.serverconnection.storage.XodusServerIssueStore.toProtoFlow;
import static org.sonarsource.sonarlint.core.serverconnection.storage.XodusServerIssueStore.toProtoImpact;

public class ProjectStorageFixture {

  public static class ProjectStorage {
    private final Path path;

    public ProjectStorage(Path path) {
      this.path = path;
    }

    public Path getPath() {
      return path;
    }

    public void setSettings(Map<String, String> settings) {
      var configFile = path.resolve("analyzer_config.pb");
      var analyzerConfiguration = ProtobufFileUtil.readFile(configFile, Sonarlint.AnalyzerConfiguration.parser());
      ProtobufFileUtil.writeToFile(Sonarlint.AnalyzerConfiguration.newBuilder(analyzerConfiguration)
        .clearSettings()
        .putAllSettings(settings).build(), configFile);
    }
  }

  public static class ProjectStorageBuilder {
    private final String projectKey;
    private final List<RuleSetBuilder> ruleSets = new ArrayList<>();
    private final List<BranchBuilder> branches = new ArrayList<>();
    private final Map<String, String> projectSettings = new HashMap<>();
    private ZonedDateTime lastSmartNotificationPoll;

    public ProjectStorageBuilder(String projectKey) {
      this.projectKey = projectKey;
    }

    public ProjectStorageBuilder withRuleSet(String languageKey, Consumer<RuleSetBuilder> consumer) {
      var ruleSetBuilder = new RuleSetBuilder(languageKey);
      consumer.accept(ruleSetBuilder);
      ruleSets.add(ruleSetBuilder);
      return this;
    }

    public ProjectStorageBuilder withSetting(String key, String value) {
      projectSettings.put(key, value);
      return this;
    }

    public ProjectStorageBuilder withMainBranch(Consumer<BranchBuilder> consumer) {
      return withMainBranch("main", consumer);
    }

    public ProjectStorageBuilder withMainBranch(String name) {
      return withMainBranch(name, branch -> {
      });
    }

    public ProjectStorageBuilder withMainBranch(String name, Consumer<BranchBuilder> consumer) {
      var branchBuilder = new BranchBuilder(name, true);
      consumer.accept(branchBuilder);
      branches.add(branchBuilder);
      return this;
    }

    public ProjectStorageBuilder withNonMainBranch(String name) {
      var branchBuilder = new BranchBuilder(name, false);
      branches.add(branchBuilder);
      return this;
    }

    public void withLastSmartNotificationPoll(ZonedDateTime dateTime) {
      this.lastSmartNotificationPoll = dateTime;
    }

    ProjectStorage create(Path projectsRootPath) {
      var projectFolder = projectsRootPath.resolve(ProjectStoragePaths.encodeForFs(projectKey));
      try {
        FileUtils.forceMkdir(projectFolder.toFile());
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }

      createAnalyzerConfig(projectFolder);
      createSmartNotificationPoll(projectFolder);
      createServerBranches(projectFolder);
      createFindings(projectFolder);

      return new ProjectStorage(projectFolder);
    }

    private void createSmartNotificationPoll(Path projectFolder) {
      if (lastSmartNotificationPoll != null) {
        var lastPoll = Sonarlint.LastEventPolling.newBuilder()
          .setLastEventPolling(lastSmartNotificationPoll.toInstant().toEpochMilli())
          .build();
        ProtobufFileUtil.writeToFile(lastPoll, projectFolder.resolve("last_event_polling.pb"));
      }
    }

    private void createAnalyzerConfig(Path projectFolder) {
      Map<String, Sonarlint.RuleSet> protoRuleSets = new HashMap<>();
      ruleSets.forEach(ruleSet -> {
        var ruleSetBuilder = Sonarlint.RuleSet.newBuilder();
        ruleSet.activeRules.forEach(activeRule -> {
          ruleSetBuilder.addRule(Sonarlint.RuleSet.ActiveRule.newBuilder()
            .setRuleKey(activeRule.ruleKey)
            .setSeverity(activeRule.severity)
            .setTemplateKey(trimToEmpty(activeRule.templateKey))
            .putAllParams(activeRule.params)
            .build());
        });
        protoRuleSets.put(ruleSet.languageKey, ruleSetBuilder.build());
      });
      var analyzerConfiguration = Sonarlint.AnalyzerConfiguration.newBuilder()
        .putAllSettings(projectSettings)
        .putAllRuleSetsByLanguageKey(protoRuleSets).build();
      ProtobufFileUtil.writeToFile(analyzerConfiguration, projectFolder.resolve("analyzer_config.pb"));
    }

    private void createServerBranches(Path projectFolder) {
      if (branches.isEmpty()) {
        return;
      }
      var projectBranches = Sonarlint.ProjectBranches.newBuilder()
        .setMainBranchName(
          branches.stream().filter(branch -> branch.isMain).map(branch -> branch.name).findFirst().orElseThrow(() -> new IllegalArgumentException("No main branch defined")))
        .addAllBranchName(branches.stream().map(branch -> branch.name).collect(Collectors.toList()))
        .build();
      ProtobufFileUtil.writeToFile(projectBranches, projectFolder.resolve("project_branches.pb"));
    }

    private void createFindings(Path projectFolder) {
      if (branches.isEmpty()) {
        return;
      }
      var xodusTempDbPath = projectFolder.resolve("xodus_temp_db");
      var xodusBackupPath = projectFolder.resolve("issues").resolve("backup.tar.gz");
      try {
        Files.createDirectories(xodusBackupPath.getParent());
      } catch (IOException e) {
        throw new IllegalStateException("Unable to create the Xodus backup parent folders", e);
      }
      var environment = Environments.newInstance(xodusTempDbPath.toAbsolutePath().toFile());
      var entityStore = PersistentEntityStores.newInstance(environment);
      entityStore.executeInTransaction(txn -> {
        entityStore.registerCustomPropertyType(txn, IssueSeverity.class, new IssueSeverityBinding());
        entityStore.registerCustomPropertyType(txn, RuleType.class, new IssueTypeBinding());
        entityStore.registerCustomPropertyType(txn, Instant.class, new InstantBinding());
        entityStore.registerCustomPropertyType(txn, HotspotReviewStatus.class, new HotspotReviewStatusBinding());
        entityStore.registerCustomPropertyType(txn, UUID.class, new UuidBinding());
        branches.forEach(branch -> {
          var branchEntity = txn.newEntity("Branch");
          branchEntity.setProperty("name", branch.name);
          var issuesByFilePath = branch.serverIssues.stream()
            .map(ServerIssueFixtures.ServerIssueBuilder::build)
            .collect(Collectors.groupingBy(ServerIssueFixtures.ServerIssue::getFilePath));
          var taintIssuesByFilePath = branch.serverTaintIssues.stream()
            .map(ServerTaintIssueFixtures.ServerTaintIssueBuilder::build)
            .collect(Collectors.groupingBy(ServerTaintIssueFixtures.ServerTaintIssue::getFilePath));
          var hotspotsByFilePath = branch.serverHotspots.stream()
            .map(ServerSecurityHotspotFixture.ServerSecurityHotspotBuilder::build)
            .collect(Collectors.groupingBy(ServerSecurityHotspotFixture.ServerHotspot::getFilePath));
          Stream.of(issuesByFilePath, taintIssuesByFilePath, hotspotsByFilePath)
            .flatMap(map -> map.keySet().stream())
            .collect(Collectors.toList())
            .forEach(filePath -> {
              var fileEntity = txn.newEntity("File");
              fileEntity.setProperty("path", filePath);
              branchEntity.addLink("files", fileEntity);
              issuesByFilePath.getOrDefault(filePath, Collections.emptyList())
                .forEach(issue -> {
                  var issueEntity = txn.newEntity("Issue");
                  issueEntity.setProperty("key", issue.key);
                  issueEntity.setProperty("type", issue.ruleType);
                  issueEntity.setProperty("resolved", issue.resolved);
                  issueEntity.setProperty("ruleKey", issue.ruleKey);
                  issueEntity.setBlobString("message", issue.message);
                  issueEntity.setProperty("creationDate", issue.introductionDate);
                  var userSeverity = issue.userSeverity;
                  if (userSeverity != null) {
                    issueEntity.setProperty("userSeverity", userSeverity);
                  }
                  if (issue.lineNumber != null && issue.lineHash != null) {
                    issueEntity.setBlobString("lineHash", issue.lineHash);
                    issueEntity.setProperty("startLine", issue.lineNumber);
                  } else if (issue.textRangeWithHash != null) {
                    var textRange = issue.textRangeWithHash;
                    issueEntity.setProperty("startLine", textRange.getStartLine());
                    issueEntity.setProperty("startLineOffset", textRange.getStartLineOffset());
                    issueEntity.setProperty("endLine", textRange.getEndLine());
                    issueEntity.setProperty("endLineOffset", textRange.getEndLineOffset());
                    issueEntity.setBlobString("rangeHash", textRange.getHash());
                  }

                  issueEntity.setLink("file", fileEntity);
                  fileEntity.addLink("issues", issueEntity);
                });

              taintIssuesByFilePath.getOrDefault(filePath, Collections.emptyList())
                .forEach(taint -> {
                  var taintIssueEntity = txn.newEntity("TaintIssue");
                  taintIssueEntity.setProperty("id", UUID.randomUUID());
                  taintIssueEntity.setProperty("key", taint.key);
                  taintIssueEntity.setProperty("type", taint.type);
                  taintIssueEntity.setProperty("resolved", taint.resolved);
                  taintIssueEntity.setProperty("ruleKey", taint.ruleKey);
                  taintIssueEntity.setBlobString("message", taint.message);
                  taintIssueEntity.setProperty("creationDate", taint.creationDate);
                  taintIssueEntity.setProperty("severity", taint.severity);
                  if (taint.textRange != null) {
                    var textRange = taint.textRange;
                    taintIssueEntity.setProperty("startLine", textRange.getStartLine());
                    taintIssueEntity.setProperty("startLineOffset", textRange.getStartLineOffset());
                    taintIssueEntity.setProperty("endLine", textRange.getEndLine());
                    taintIssueEntity.setProperty("endLineOffset", textRange.getEndLineOffset());
                    taintIssueEntity.setBlobString("rangeHash", textRange.getHash());
                  }
                  taintIssueEntity.setBlob("flows", toProtoFlow(taint.flows));
                  if (taint.ruleDescriptionContextKey != null) {
                    taintIssueEntity.setProperty("ruleDescriptionContextKey", taint.ruleDescriptionContextKey);
                  }
                  if (taint.cleanCodeAttribute != null) {
                    taintIssueEntity.setProperty("cleanCodeAttribute", taint.cleanCodeAttribute.name());
                  }
                  taintIssueEntity.setBlob("impacts", toProtoImpact(taint.impacts));

                  taintIssueEntity.setLink("file", fileEntity);
                  fileEntity.addLink("taintIssues", taintIssueEntity);
                  branchEntity.addLink("taintIssues", taintIssueEntity);
                  taintIssueEntity.setLink("branch", branchEntity);
                });

              hotspotsByFilePath.getOrDefault(filePath, Collections.emptyList())
                .forEach(hotspot -> {
                  var hotspotEntity = txn.newEntity("Hotspot");
                  hotspotEntity.setProperty("key", hotspot.key);
                  hotspotEntity.setProperty("ruleKey", hotspot.ruleKey);
                  hotspotEntity.setBlobString("message", hotspot.message);
                  hotspotEntity.setProperty("creationDate", hotspot.introductionDate);
                  var textRange = hotspot.textRangeWithHash;
                  hotspotEntity.setProperty("startLine", textRange.getStartLine());
                  hotspotEntity.setProperty("startLineOffset", textRange.getStartLineOffset());
                  hotspotEntity.setProperty("endLine", textRange.getEndLine());
                  hotspotEntity.setProperty("endLineOffset", textRange.getEndLineOffset());
                  hotspotEntity.setBlobString("rangeHash", textRange.getHash());

                  hotspotEntity.setProperty("status", hotspot.status);
                  hotspotEntity.setProperty("vulnerabilityProbability", hotspot.vulnerabilityProbability.toString());
                  if (hotspot.assignee != null) {
                    hotspotEntity.setProperty("assignee", hotspot.assignee);
                  }

                  hotspotEntity.setLink("file", fileEntity);
                  fileEntity.addLink("hotspots", hotspotEntity);
                });
            });
        });
      });
      try {
        CompressBackupUtil.backup(entityStore, xodusBackupPath.toFile(), false);
      } catch (Exception e) {
        throw new IllegalStateException("Unable to backup server issue database", e);
      }
    }

    private <T> Set<T> concat(Set<T> set, Set<T> otherSet) {
      var concatSet = new HashSet<T>(set);
      concatSet.addAll(otherSet);
      return concatSet;
    }

    public static class RuleSetBuilder {
      private final String languageKey;
      private final List<ActiveRule> activeRules = new ArrayList<>();

      public RuleSetBuilder(String languageKey) {
        this.languageKey = languageKey;
      }

      public RuleSetBuilder withActiveRule(String ruleKey, String severity) {
        return withActiveRule(ruleKey, severity, Map.of());
      }

      public RuleSetBuilder withActiveRule(String ruleKey, String severity, Map<String, String> params) {
        activeRules.add(new ActiveRule(ruleKey, severity, null, params));
        return this;
      }

      public RuleSetBuilder withCustomActiveRule(String ruleKey, String templateKey, String severity, Map<String, String> params) {
        activeRules.add(new ActiveRule(ruleKey, severity, templateKey, params));
        return this;
      }
    }

    public static class BranchBuilder {
      private final List<ServerIssueFixtures.ServerIssueBuilder> serverIssues = new ArrayList<>();
      private final List<ServerTaintIssueFixtures.ServerTaintIssueBuilder> serverTaintIssues = new ArrayList<>();
      private final List<ServerSecurityHotspotFixture.ServerSecurityHotspotBuilder> serverHotspots = new ArrayList<>();
      private final String name;
      private final boolean isMain;

      public BranchBuilder(String name, boolean isMain) {
        this.name = name;
        this.isMain = isMain;
      }

      public BranchBuilder withIssue(ServerIssueFixtures.ServerIssueBuilder serverIssueBuilder) {
        serverIssues.add(serverIssueBuilder);
        return this;
      }

      public BranchBuilder withTaintIssue(ServerTaintIssueFixtures.ServerTaintIssueBuilder serverTaintIssueBuilder) {
        serverTaintIssues.add(serverTaintIssueBuilder);
        return this;
      }

      public BranchBuilder withHotspot(ServerSecurityHotspotFixture.ServerSecurityHotspotBuilder serverHotspotBuilder) {
        serverHotspots.add(serverHotspotBuilder);
        return this;
      }
    }

    private static class ActiveRule {
      private final String ruleKey;
      private final String severity;
      private final String templateKey;
      private final Map<String, String> params;

      private ActiveRule(String ruleKey, String severity, @Nullable String templateKey, Map<String, String> params) {
        this.ruleKey = ruleKey;
        this.severity = severity;
        this.templateKey = templateKey;
        this.params = params;
      }
    }
  }
}
