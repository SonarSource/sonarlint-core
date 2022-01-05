/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.mediumtest.fixtures;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

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
      var analyzerConfiguration = ProtobufUtil.readFile(configFile, Sonarlint.AnalyzerConfiguration.parser());
      ProtobufUtil.writeToFile(Sonarlint.AnalyzerConfiguration.newBuilder(analyzerConfiguration)
        .clearSettings()
        .putAllSettings(settings).build(), configFile);
    }
  }

  public static class ProjectStorageBuilder {
    private final String projectKey;
    private boolean isStale;
    private final List<RuleSetBuilder> ruleSets = new ArrayList<>();

    public ProjectStorageBuilder(String projectKey) {
      this.projectKey = projectKey;
    }

    String getProjectKey() {
      return projectKey;
    }

    public ProjectStorageBuilder stale() {
      isStale = true;
      return this;
    }

    public ProjectStorageBuilder withRuleSet(String languageKey, Consumer<RuleSetBuilder> consumer) {
      var ruleSetBuilder = new RuleSetBuilder(languageKey);
      consumer.accept(ruleSetBuilder);
      ruleSets.add(ruleSetBuilder);
      return this;
    }

    public ProjectStorage create(Path projectsRootPath) {
      var projectFolder = projectsRootPath.resolve(ProjectStoragePaths.encodeForFs(projectKey));
      var storageStatus = Sonarlint.StorageStatus.newBuilder()
        .setStorageVersion(isStale ? "0" : ProjectStoragePaths.STORAGE_VERSION)
        .setSonarlintCoreVersion("1.0")
        .setUpdateTimestamp(new Date().getTime())
        .build();
      org.sonarsource.sonarlint.core.client.api.util.FileUtils.mkdirs(projectFolder);
      ProtobufUtil.writeToFile(storageStatus, projectFolder.resolve(ProjectStoragePaths.STORAGE_STATUS_PB));
      ProtobufUtil.writeToFile(Sonarlint.ProjectConfiguration.newBuilder().build(), projectFolder.resolve(ProjectStoragePaths.PROJECT_CONFIGURATION_PB));

      Map<String, Sonarlint.RuleSet> protoRuleSets = new HashMap<>();
      ruleSets.forEach(ruleSet -> {
        var ruleSetBuilder = Sonarlint.RuleSet.newBuilder();
        ruleSet.activeRules.forEach(activeRule -> {
          ruleSetBuilder.addRules(Sonarlint.RuleSet.ActiveRule.newBuilder()
            .setRuleKey(activeRule.ruleKey)
            .setSeverity(activeRule.severity)
            .build());
        });
        protoRuleSets.put(ruleSet.languageKey, ruleSetBuilder.build());
      });
      var analyzerConfiguration = Sonarlint.AnalyzerConfiguration.newBuilder().putAllRuleSetsByLanguageKey(protoRuleSets).build();
      ProtobufUtil.writeToFile(analyzerConfiguration, projectFolder.resolve("analyzer_config.pb"));
      return new ProjectStorage(projectFolder);
    }

    public static class RuleSetBuilder {
      private final String languageKey;
      private final List<ActiveRule> activeRules = new ArrayList<>();

      public RuleSetBuilder(String languageKey) {
        this.languageKey = languageKey;
      }

      public RuleSetBuilder withActiveRule(String ruleKey, String severity) {
        activeRules.add(new ActiveRule(ruleKey, severity));
        return this;
      }
    }

    private static class ActiveRule {
      private final String ruleKey;
      private final String severity;

      private ActiveRule(String ruleKey, String severity) {
        this.ruleKey = ruleKey;
        this.severity = severity;
      }
    }
  }
}
