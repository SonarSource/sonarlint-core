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
package mediumtest.fixtures.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import jetbrains.exodus.env.Environments;
import jetbrains.exodus.util.CompressBackupUtil;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.InstantBinding;
import org.sonarsource.sonarlint.core.serverconnection.storage.IssueSeverityBinding;
import org.sonarsource.sonarlint.core.serverconnection.storage.IssueTypeBinding;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

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

    public ProjectStorageBuilder withDefaultBranch(Consumer<BranchBuilder> consumer) {
      return withBranch("main", consumer);
    }

    public ProjectStorageBuilder withBranch(String name, Consumer<BranchBuilder> consumer) {
      var branchBuilder = new BranchBuilder(name);
      consumer.accept(branchBuilder);
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
      entityStore.executeInTransaction(txn -> branches.forEach(branch -> {
        entityStore.registerCustomPropertyType(txn, IssueSeverity.class, new IssueSeverityBinding());
        entityStore.registerCustomPropertyType(txn, RuleType.class, new IssueTypeBinding());
        entityStore.registerCustomPropertyType(txn, Instant.class, new InstantBinding());
        var branchEntity = txn.newEntity("Branch");
        branchEntity.setProperty("name", branch.name);
        branch.serverIssues.stream()
          .map(ServerIssueFixtures.ServerIssueBuilder::build)
          .collect(Collectors.groupingBy(ServerIssueFixtures.ServerIssue::getFilePath))
          .forEach((filePath, issues) -> {
            var fileEntity = txn.newEntity("File");
            fileEntity.setProperty("path", filePath);
            branchEntity.addLink("files", fileEntity);
            issues.forEach(issue -> {
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
          });
      }));
      try {
        CompressBackupUtil.backup(entityStore, xodusBackupPath.toFile(),false);
      } catch (Exception e) {
        throw new IllegalStateException("Unable to backup server issue database", e);
      }
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
      private final String name;

      public BranchBuilder(String name) {
        this.name = name;
      }

      public BranchBuilder withIssue(ServerIssueFixtures.ServerIssueBuilder serverIssueBuilder) {
        serverIssues.add(serverIssueBuilder);
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
