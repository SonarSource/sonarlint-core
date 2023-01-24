/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.events.ruleset;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.serverapi.push.RuleSetChangedEvent;
import org.sonarsource.sonarlint.core.serverapi.rules.ServerActiveRule;
import org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfiguration;
import org.sonarsource.sonarlint.core.serverconnection.RuleSet;
import org.sonarsource.sonarlint.core.serverconnection.Settings;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStorage;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonarsource.sonarlint.core.serverconnection.AnalyzerConfiguration.CURRENT_SCHEMA_VERSION;

class UpdateStorageOnRuleSetChangedTests {

  private UpdateStorageOnRuleSetChanged handler;
  private ProjectStorage projectStorage;

  @BeforeEach
  void setUp(@TempDir Path tempDir) {
    projectStorage = new ProjectStorage(tempDir);
    handler = new UpdateStorageOnRuleSetChanged(projectStorage);
  }

  @Test
  void should_create_storage_if_does_not_exist() {
    var event = new RuleSetChangedEvent(
      List.of("projectKey1"),
      List.of(
        new RuleSetChangedEvent.ActiveRule("ruleKey1", "lang1", IssueSeverity.MAJOR, emptyMap(), null),
        new RuleSetChangedEvent.ActiveRule("ruleKey2", "lang2", IssueSeverity.MINOR, Map.of("paramKey", "paramValue"), "templateKey")),
      Collections.emptyList());

    handler.handle(event);

    var projectConfig = projectStorage.getAnalyzerConfiguration("projectKey1");
    assertThat(projectConfig.getSettings().getAll()).isEmpty();
    assertThat(projectConfig.getRuleSetByLanguageKey()).containsOnlyKeys("lang1", "lang2");
    var lang1RuleSet = projectConfig.getRuleSetByLanguageKey().get("lang1");
    assertThat(lang1RuleSet.getLastModified()).isEmpty();
    assertThat(lang1RuleSet.getRules())
      .extracting("ruleKey", "severity", "params", "templateKey")
      .containsOnly(tuple("ruleKey1", IssueSeverity.MAJOR, Map.of(), ""));
    var lang2RuleSet = projectConfig.getRuleSetByLanguageKey().get("lang2");
    assertThat(lang2RuleSet.getLastModified()).isEmpty();
    assertThat(lang2RuleSet.getRules())
      .extracting("ruleKey", "severity", "params", "templateKey")
      .containsOnly(tuple("ruleKey2", IssueSeverity.MINOR, Map.of("paramKey", "paramValue"), "templateKey"));
  }

  @Test
  void should_update_existing_rule_in_storage() {
    projectStorage.store("projectKey1", new AnalyzerConfiguration(new Settings(emptyMap()), Map.of("lang1", new RuleSet(List.of(
      new ServerActiveRule("ruleKey1", IssueSeverity.MINOR, Map.of("paramKey", "paramValue"), "")), "2020-10-27T23:08:58+0000")), CURRENT_SCHEMA_VERSION));
    var event = new RuleSetChangedEvent(
      List.of("projectKey1"),
      List.of(new RuleSetChangedEvent.ActiveRule("ruleKey1", "lang1", IssueSeverity.MAJOR, emptyMap(), null)),
      Collections.emptyList());

    handler.handle(event);

    var projectConfig = projectStorage.getAnalyzerConfiguration("projectKey1");
    assertThat(projectConfig.getSettings().getAll()).isEmpty();
    assertThat(projectConfig.getRuleSetByLanguageKey()).containsOnlyKeys("lang1");
    var ruleSet = projectConfig.getRuleSetByLanguageKey().get("lang1");
    assertThat(ruleSet.getLastModified()).isEqualTo("2020-10-27T23:08:58+0000");
    assertThat(ruleSet.getRules())
      .extracting("ruleKey", "severity", "params", "templateKey")
      .containsOnly(tuple("ruleKey1", IssueSeverity.MAJOR, Map.of(), ""));
  }

  @Test
  void should_activate_rule_of_existing_language_in_storage() {
    projectStorage.store("projectKey1", new AnalyzerConfiguration(new Settings(emptyMap()), Map.of("lang1", new RuleSet(List.of(
      new ServerActiveRule("ruleKey1", IssueSeverity.MINOR, Map.of("paramKey", "paramValue"), "")), "")), CURRENT_SCHEMA_VERSION));
    var event = new RuleSetChangedEvent(
      List.of("projectKey1"),
      List.of(new RuleSetChangedEvent.ActiveRule("ruleKey2", "lang1", IssueSeverity.MAJOR, emptyMap(), null)),
      Collections.emptyList());

    handler.handle(event);

    var projectConfig = projectStorage.getAnalyzerConfiguration("projectKey1");
    assertThat(projectConfig.getSettings().getAll()).isEmpty();
    assertThat(projectConfig.getRuleSetByLanguageKey()).containsOnlyKeys("lang1");
    var ruleSet = projectConfig.getRuleSetByLanguageKey().get("lang1");
    assertThat(ruleSet.getLastModified()).isEmpty();
    assertThat(ruleSet.getRules())
      .extracting("ruleKey", "severity", "params", "templateKey")
      .containsOnly(
        tuple("ruleKey1", IssueSeverity.MINOR, Map.of("paramKey", "paramValue"), ""),
        tuple("ruleKey2", IssueSeverity.MAJOR, Map.of(), ""));
  }

  @Test
  void should_activate_rule_of_unknown_language_in_storage() {
    projectStorage.store("projectKey1", new AnalyzerConfiguration(new Settings(emptyMap()), Map.of("lang1", new RuleSet(List.of(
      new ServerActiveRule("ruleKey1", IssueSeverity.MINOR, Map.of("paramKey", "paramValue"), "")), "")), CURRENT_SCHEMA_VERSION));
    var event = new RuleSetChangedEvent(
      List.of("projectKey1"),
      List.of(new RuleSetChangedEvent.ActiveRule("ruleKey2", "lang2", IssueSeverity.MAJOR, emptyMap(), null)),
      Collections.emptyList());

    handler.handle(event);

    var projectConfig = projectStorage.getAnalyzerConfiguration("projectKey1");
    assertThat(projectConfig.getSettings().getAll()).isEmpty();
    assertThat(projectConfig.getRuleSetByLanguageKey()).containsOnlyKeys("lang1", "lang2");
    var lang1RuleSet = projectConfig.getRuleSetByLanguageKey().get("lang1");
    assertThat(lang1RuleSet.getLastModified()).isEmpty();
    assertThat(lang1RuleSet.getRules())
      .extracting("ruleKey", "severity", "params", "templateKey")
      .containsOnly(tuple("ruleKey1", IssueSeverity.MINOR, Map.of("paramKey", "paramValue"), ""));

    var lang2RuleSet = projectConfig.getRuleSetByLanguageKey().get("lang2");
    assertThat(lang2RuleSet.getLastModified()).isEmpty();
    assertThat(lang2RuleSet.getRules())
      .extracting("ruleKey", "severity", "params", "templateKey")
      .containsOnly(tuple("ruleKey2", IssueSeverity.MAJOR, Map.of(), ""));
  }

  @Test
  void should_deactivate_rule_in_storage() {
    projectStorage.store("projectKey1", new AnalyzerConfiguration(new Settings(emptyMap()), Map.of("lang1", new RuleSet(List.of(
      new ServerActiveRule("ruleKey1", IssueSeverity.MINOR, Map.of("paramKey", "paramValue"), ""),
      new ServerActiveRule("ruleKey2", IssueSeverity.MAJOR, Map.of(), "")), "")), CURRENT_SCHEMA_VERSION));
    var event = new RuleSetChangedEvent(
      List.of("projectKey1"),
      List.of(),
      List.of("ruleKey1"));

    handler.handle(event);

    var projectConfig = projectStorage.getAnalyzerConfiguration("projectKey1");
    assertThat(projectConfig.getSettings().getAll()).isEmpty();
    assertThat(projectConfig.getRuleSetByLanguageKey()).containsOnlyKeys("lang1");
    var ruleSet = projectConfig.getRuleSetByLanguageKey().get("lang1");
    assertThat(ruleSet.getLastModified()).isEmpty();
    assertThat(ruleSet.getRules())
      .extracting("ruleKey", "severity", "params", "templateKey")
      .containsOnly(tuple("ruleKey2", IssueSeverity.MAJOR, Map.of(), ""));
  }

  @Test
  void should_remove_ruleset_from_storage_when_deactivating_last_rule() {
    projectStorage.store("projectKey1", new AnalyzerConfiguration(new Settings(emptyMap()), Map.of("lang1", new RuleSet(List.of(
      new ServerActiveRule("ruleKey1", IssueSeverity.MINOR, Map.of("paramKey", "paramValue"), "")), ""),
      "lang2", new RuleSet(List.of(new ServerActiveRule("otherRule", IssueSeverity.MAJOR, emptyMap(), "")), "")), CURRENT_SCHEMA_VERSION));
    var event = new RuleSetChangedEvent(
      List.of("projectKey1"),
      List.of(),
      List.of("ruleKey1"));

    handler.handle(event);

    var projectConfig = projectStorage.getAnalyzerConfiguration("projectKey1");
    assertThat(projectConfig.getSettings().getAll()).isEmpty();
    assertThat(projectConfig.getRuleSetByLanguageKey()).containsOnlyKeys("lang2");
  }
}
