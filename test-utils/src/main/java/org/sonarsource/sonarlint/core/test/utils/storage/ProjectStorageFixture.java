/*
 * SonarLint Core - Test Utils
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.test.utils.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.serverapi.hotspot.ServerHotspot;
import org.sonarsource.sonarlint.core.serverconnection.issues.RangeLevelServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerIssue;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufFileUtil;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerFindingRepository;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

public class ProjectStorageFixture {

  public static class ProjectStorageBuilder {
    private final String connectionId;
    private final String projectKey;
    private final List<RuleSetBuilder> ruleSets = new ArrayList<>();
    private final List<BranchBuilder> branches = new ArrayList<>();
    private final Map<String, String> projectSettings = new HashMap<>();
    private ZonedDateTime lastSmartNotificationPoll;
    private Sonarlint.NewCodeDefinition newCodeDefinition;
    private boolean shouldThrowOnReadLastEvenPollingTime = false;

    public ProjectStorageBuilder(String connectionId, String projectKey) {
      this.connectionId = connectionId;
      this.projectKey = projectKey;
    }

    public ProjectStorageBuilder withRuleSet(String languageKey, Consumer<RuleSetBuilder> consumer) {
      var ruleSetBuilder = new RuleSetBuilder(languageKey);
      consumer.accept(ruleSetBuilder);
      ruleSets.add(ruleSetBuilder);
      return this;
    }

    public ProjectStorageBuilder withNewCodeDefinition(Sonarlint.NewCodeDefinition newCodeDefinition) {
      this.newCodeDefinition = newCodeDefinition;
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

    public ProjectStorageBuilder withLastSmartNotificationPoll(ZonedDateTime dateTime) {
      this.lastSmartNotificationPoll = dateTime;
      return this;
    }

    /**
     * It writes an illegal content to the last_event_polling.pb file,
     * that leads to StorageException being thrown on the file read.
     * It emulates the situation when this file is not accessible during sync.
     */
    public ProjectStorageBuilder shouldThrowOnReadLastEvenPollingTime() {
      this.shouldThrowOnReadLastEvenPollingTime = true;
      return this;
    }

    void populate(Path projectsRootPath, TestDatabase database) {
      var projectFolder = projectsRootPath.resolve(ProjectStoragePaths.encodeForFs(projectKey));
      try {
        FileUtils.forceMkdir(projectFolder.toFile());
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }

      createAnalyzerConfig(projectFolder);
      createSmartNotificationPoll(projectFolder);
      createServerBranches(projectFolder);
      createNewCodeDefinition(projectFolder);

      populateDatabase(database);
    }

    private void createNewCodeDefinition(Path projectFolder) {
      if (newCodeDefinition != null) {
        ProtobufFileUtil.writeToFile(newCodeDefinition, projectFolder.resolve("new_code_definition.pb"));
      }
    }

    private void createSmartNotificationPoll(Path projectFolder) {
      if (shouldThrowOnReadLastEvenPollingTime) {
        try {
          FileUtils.write(projectFolder.resolve("last_event_polling.pb").toFile(), "illegal content", StandardCharsets.UTF_8);
        } catch (IOException e) {
          throw new IllegalStateException("Unable to create the last smart notification poll file", e);
        }
      } else if (lastSmartNotificationPoll != null) {
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
        ruleSet.activeRules.forEach(activeRule -> ruleSetBuilder.addRule(Sonarlint.RuleSet.ActiveRule.newBuilder()
          .setRuleKey(activeRule.ruleKey)
          .setSeverity(activeRule.severity)
          .setTemplateKey(trimToEmpty(activeRule.templateKey))
          .putAllParams(activeRule.params)
          .build()));
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
        .addAllBranchName(branches.stream().map(branch -> branch.name).toList())
        .build();
      ProtobufFileUtil.writeToFile(projectBranches, projectFolder.resolve("project_branches.pb"));
    }

    public void populateDatabase(TestDatabase database) {
      var serverFindingRepository = new ServerFindingRepository(database.dsl(), connectionId, projectKey);
      branches.forEach(branch -> {

        serverFindingRepository.replaceAllIssuesOfBranch(branch.name,
          branch.serverIssues.stream().map(ServerIssueFixtures.ServerIssueBuilder::build).<ServerIssue<?>>map(issue -> new RangeLevelServerIssue(
            UUID.randomUUID(),
            issue.key(),
            issue.resolved(),
            issue.resolutionStatus(),
            issue.ruleKey(),
            issue.message(),
            Paths.get(issue.filePath()),
            issue.introductionDate(),
            issue.userSeverity(),
            issue.ruleType(),
            issue.textRangeWithHash(),
            issue.impacts())).toList(),
          Set.of());

        serverFindingRepository.replaceAllHotspotsOfBranch(branch.name,
          branch.serverHotspots.stream().map(ServerSecurityHotspotFixture.ServerSecurityHotspotBuilder::build).map(hotspot -> new ServerHotspot(
            UUID.randomUUID(),
            hotspot.key(),
            hotspot.ruleKey(),
            hotspot.message(),
            Paths.get(hotspot.filePath()),
            hotspot.textRangeWithHash(),
            hotspot.introductionDate(),
            hotspot.status(),
            hotspot.vulnerabilityProbability(),
            hotspot.assignee())).toList(),
          Set.of());

        serverFindingRepository.replaceAllTaintsOfBranch(branch.name,
          branch.serverTaintIssues.stream().map(ServerTaintIssueFixtures.ServerTaintIssueBuilder::build).map(i -> new ServerTaintIssue(
            i.id(),
            i.key(),
            i.resolved(),
            i.resolutionStatus(),
            i.ruleKey(),
            i.message(),
            Paths.get(i.filePath()),
            i.creationDate(),
            i.severity(),
            i.type(),
            i.textRange(),
            i.ruleDescriptionContextKey(),
            i.cleanCodeAttribute(),
            i.impacts(),
            List.of())).toList(),
          Set.of());

        serverFindingRepository.replaceAllDependencyRisksOfBranch(branch.name,
          branch.serverDependencyRisks.stream().map(ServerDependencyRiskFixtures.ServerDependencyRiskBuilder::build).map(i -> new ServerDependencyRisk(
            i.key(),
            i.type(),
            i.severity(),
            i.quality(),
            i.status(),
            i.packageName(),
            i.packageVersion(),
            i.vulnerabilityId(),
            i.cvssScore(),
            i.transitions())).toList());
      });
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
      private final List<ServerDependencyRiskFixtures.ServerDependencyRiskBuilder> serverDependencyRisks = new ArrayList<>();
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

      public BranchBuilder withDependencyRisk(ServerDependencyRiskFixtures.ServerDependencyRiskBuilder serverDependencyRiskBuilder) {
        serverDependencyRisks.add(serverDependencyRiskBuilder);
        return this;
      }

      public BranchBuilder withHotspot(ServerSecurityHotspotFixture.ServerSecurityHotspotBuilder serverHotspotBuilder) {
        serverHotspots.add(serverHotspotBuilder);
        return this;
      }
    }

    private record ActiveRule(String ruleKey, String severity, @Nullable String templateKey, Map<String, String> params) {
    }
  }
}
