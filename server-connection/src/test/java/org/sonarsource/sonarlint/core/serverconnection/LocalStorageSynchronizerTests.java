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
package org.sonarsource.sonarlint.core.serverconnection;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common.BranchType;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.ProjectBranches;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Qualityprofiles;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Settings;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.PluginsStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStorage;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.serverconnection.storage.ServerInfoStorage;
import testutils.MockWebServerExtensionWithProtobuf;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class LocalStorageSynchronizerTests {

  public static final String JS_QP_KEY = "jsQpKey";
  public static final String JAVA_QP_KEY = "javaQpKey";
  public static final String DATE_T0 = "2020-10-27T23:08:58+0000";
  public static final String DATE_AFTER_T0 = "2020-10-27T23:18:58+0000";
  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();
  private final ProgressMonitor progressMonitor = new ProgressMonitor(null);
  private ServerApi serverApi;

  @BeforeEach
  void prepare() {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": []}");
    serverApi = new ServerApi(mockServer.serverApiHelper());
  }

  @Test
  void should_synchronize_a_project_with_a_single_active_rule(@TempDir Path tmpDir) {
    mockApiSystemStatus();
    mockApiSettings();
    mockApiQualityProfilesSearch(jsProfile(DATE_T0));
    mockApiRulesSearchReturnsOneRule(JS_QP_KEY);
    mockApiProjectBranchesList();

    final var serverInfoStorage = new ServerInfoStorage(tmpDir);
    var synchronizer = new LocalStorageSynchronizer(Set.of(Language.JS), emptySet(), new ServerInfoSynchronizer(serverInfoStorage), new PluginsStorage(tmpDir),
      new ProjectStorage(tmpDir));

    synchronizer.synchronize(serverApi, Set.of("projectKey"), progressMonitor);

    var analyzerConfigFile = tmpDir.resolve("70726f6a6563744b6579/analyzer_config.pb");
    assertThat(analyzerConfigFile).exists();
    var analyzerConfiguration = ProtobufUtil.readFile(analyzerConfigFile, Sonarlint.AnalyzerConfiguration.parser());
    assertThat(analyzerConfiguration.getSettingsMap()).containsEntry("settingKey", "settingValue");
    var ruleSetsByLanguageKeyMap = analyzerConfiguration.getRuleSetsByLanguageKeyMap();
    assertThat(ruleSetsByLanguageKeyMap).containsKey(Language.JS.getLanguageKey());
    var ruleSet = ruleSetsByLanguageKeyMap.get(Language.JS.getLanguageKey());
    assertThat(ruleSet.getRuleCount()).isEqualTo(1);
    var activeRule = ruleSet.getRuleList().get(0);
    assertThat(activeRule.getRuleKey()).isEqualTo("ruleKey");
    assertThat(activeRule.getSeverity()).isEqualTo("MAJOR");
    assertThat(activeRule.getTemplateKey()).isEqualTo("templateKey");
    assertThat(activeRule.getParamsMap()).containsEntry("paramKey", "paramValue");

    var projectBranchesFile = tmpDir.resolve("70726f6a6563744b6579/project_branches.pb");
    assertThat(projectBranchesFile).exists();
    var projectBranches = ProtobufUtil.readFile(projectBranchesFile, Sonarlint.ProjectBranches.parser());
    assertThat(projectBranches.getBranchNameList()).containsExactlyInAnyOrder("master", "feature/foo");
    assertThat(projectBranches.getMainBranchName()).isEqualTo("master");
  }

  @Test
  void should_synchronize_a_ruleset_if_missing_language(@TempDir Path tmpDir) {
    var storageFile = tmpDir.resolve("70726f6a6563744b6579/analyzer_config.pb");
    FileUtils.mkdirs(storageFile.getParent());
    ProtobufUtil.writeToFile(Sonarlint.AnalyzerConfiguration.newBuilder()
      .setSchemaVersion(AnalyzerConfiguration.CURRENT_SCHEMA_VERSION)
      // js is not part of profiles in the storage, so it should be sync
      .putAllRuleSetsByLanguageKey(Map.of(Language.JAVA.getLanguageKey(), Sonarlint.RuleSet.newBuilder()
        .setLastModified(DATE_T0)
        .build()))
      .build(), storageFile);
    mockApiSystemStatus();
    mockApiSettings();
    mockApiQualityProfilesSearch(jsProfile(DATE_T0), javaProfile(DATE_T0));
    mockApiRulesSearchReturnsOneRule(JS_QP_KEY);
    // 404 if trying to fetch Java rules
    mockApiProjectBranchesList();

    final var serverInfoStorage = new ServerInfoStorage(tmpDir);
    var synchronizer = new LocalStorageSynchronizer(Set.of(Language.JS, Language.JAVA), emptySet(), new ServerInfoSynchronizer(serverInfoStorage), new PluginsStorage(tmpDir),
      new ProjectStorage(tmpDir));

    synchronizer.synchronize(serverApi, Set.of("projectKey"), progressMonitor);

    assertThat(storageFile).exists();
    var analyzerConfiguration = ProtobufUtil.readFile(storageFile, Sonarlint.AnalyzerConfiguration.parser());
    var ruleSetsByLanguageKeyMap = analyzerConfiguration.getRuleSetsByLanguageKeyMap();
    assertThat(ruleSetsByLanguageKeyMap).containsOnlyKeys(Language.JS.getLanguageKey(), Language.JAVA.getLanguageKey());
    var ruleSetJs = ruleSetsByLanguageKeyMap.get(Language.JS.getLanguageKey());
    assertThat(ruleSetJs.getRuleCount()).isEqualTo(1);
    var ruleSetJava = ruleSetsByLanguageKeyMap.get(Language.JAVA.getLanguageKey());
    assertThat(ruleSetJava.getRuleCount()).isZero();
  }

  @Test
  void should_not_synchronize_up_to_date_ruleset(@TempDir Path tmpDir) {
    var storageFile = tmpDir.resolve("70726f6a6563744b6579/analyzer_config.pb");
    FileUtils.mkdirs(storageFile.getParent());
    ProtobufUtil.writeToFile(Sonarlint.AnalyzerConfiguration.newBuilder()
      .setSchemaVersion(AnalyzerConfiguration.CURRENT_SCHEMA_VERSION)
      .putAllRuleSetsByLanguageKey(Map.of(Language.JAVA.getLanguageKey(), Sonarlint.RuleSet.newBuilder()
        .setLastModified(DATE_T0)
        .build()))
      .putAllRuleSetsByLanguageKey(Map.of(Language.JS.getLanguageKey(), Sonarlint.RuleSet.newBuilder()
        .setLastModified(DATE_T0)
        .build()))
      .build(), storageFile);
    mockApiSystemStatus();
    mockApiSettings();
    mockApiQualityProfilesSearch(javaProfile(DATE_T0), jsProfile(DATE_AFTER_T0));
    mockApiRulesSearchReturnsOneRule(JS_QP_KEY);
    // 404 if trying to fetch Java rules
    mockApiProjectBranchesList();
    final var serverInfoStorage = new ServerInfoStorage(tmpDir);
    var synchronizer = new LocalStorageSynchronizer(Set.of(Language.JS, Language.JAVA), emptySet(), new ServerInfoSynchronizer(serverInfoStorage), new PluginsStorage(tmpDir),
      new ProjectStorage(tmpDir));

    synchronizer.synchronize(serverApi, Set.of("projectKey"), progressMonitor);

    assertThat(storageFile).exists();
    var analyzerConfiguration = ProtobufUtil.readFile(storageFile, Sonarlint.AnalyzerConfiguration.parser());
    var ruleSetsByLanguageKeyMap = analyzerConfiguration.getRuleSetsByLanguageKeyMap();
    assertThat(ruleSetsByLanguageKeyMap).containsOnlyKeys(Language.JS.getLanguageKey(), Language.JAVA.getLanguageKey());
    var ruleSetJs = ruleSetsByLanguageKeyMap.get(Language.JS.getLanguageKey());
    assertThat(ruleSetJs.getRuleCount()).isEqualTo(1);
    var ruleSetJava = ruleSetsByLanguageKeyMap.get(Language.JAVA.getLanguageKey());
    assertThat(ruleSetJava.getRuleCount()).isZero();
  }

  @Test
  void should_force_synchronization_of_outdated_ruleset(@TempDir Path tmpDir) {
    var storageFile = tmpDir.resolve("70726f6a6563744b6579/analyzer_config.pb");
    FileUtils.mkdirs(storageFile.getParent());
    ProtobufUtil.writeToFile(Sonarlint.AnalyzerConfiguration.newBuilder()
      // No schema version defined, this is equivalent to "0"
      .putAllRuleSetsByLanguageKey(Map.of(Language.JS.getLanguageKey(), Sonarlint.RuleSet.newBuilder()
        .setLastModified(DATE_T0)
        .build()))
      .build(), storageFile);
    mockApiSystemStatus();
    mockApiSettings();
    mockApiQualityProfilesSearch(jsProfile(DATE_T0));
    mockApiRulesSearchReturnsOneRule(JS_QP_KEY);
    mockApiProjectBranchesList();
    final var serverInfoStorage = new ServerInfoStorage(tmpDir);
    var synchronizer = new LocalStorageSynchronizer(Set.of(Language.JS), emptySet(), new ServerInfoSynchronizer(serverInfoStorage), new PluginsStorage(tmpDir),
      new ProjectStorage(tmpDir));

    synchronizer.synchronize(serverApi, Set.of("projectKey"), progressMonitor);

    assertThat(storageFile).exists();
    var analyzerConfiguration = ProtobufUtil.readFile(storageFile, Sonarlint.AnalyzerConfiguration.parser());
    var ruleSetsByLanguageKeyMap = analyzerConfiguration.getRuleSetsByLanguageKeyMap();
    assertThat(ruleSetsByLanguageKeyMap).containsKey(Language.JS.getLanguageKey());
    var ruleSet = ruleSetsByLanguageKeyMap.get(Language.JS.getLanguageKey());
    assertThat(ruleSet.getRuleCount()).isEqualTo(1);
  }

  @Test
  void should_fail_synchronization_when_server_is_down(@TempDir Path tmpDir) {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"1\", \"status\": \"DOWN\", \"version\": \"1\"}");
    final var serverInfoStorage = new ServerInfoStorage(tmpDir);
    var synchronizer = new LocalStorageSynchronizer(Set.of(Language.JS), emptySet(), new ServerInfoSynchronizer(serverInfoStorage), new PluginsStorage(tmpDir),
      new ProjectStorage(tmpDir));

    var throwable = catchThrowable(() -> synchronizer.synchronize(serverApi, Set.of("projectKey"), progressMonitor));

    assertThat(throwable)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Server not ready (DOWN)");
    var storageFile = tmpDir.resolve("70726f6a6563744b6579/analyzer_config.pb");
    assertThat(storageFile).doesNotExist();
  }

  private static void mockApiProjectBranchesList() {
    mockServer.addProtobufResponse("/api/project_branches/list.protobuf?project=projectKey",
      ProjectBranches.ListWsResponse.newBuilder()
        .addBranches(ProjectBranches.Branch.newBuilder().setName("master").setIsMain(true).setType(BranchType.BRANCH))
        .addBranches(ProjectBranches.Branch.newBuilder().setName("feature/foo").setIsMain(false).setType(BranchType.BRANCH)).build());
  }

  private static void mockApiQualityProfilesSearch(Qualityprofiles.SearchWsResponse.QualityProfile... profiles) {
    mockServer.addProtobufResponse("/api/qualityprofiles/search.protobuf?project=projectKey", Qualityprofiles.SearchWsResponse.newBuilder()
      .addAllProfiles(Arrays.asList(profiles))
      .build());
  }

  @NotNull
  private static Qualityprofiles.SearchWsResponse.QualityProfile jsProfile(String rulesUpdatedAt) {
    return Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder()
      .setKey(JS_QP_KEY)
      .setName("SonarWay JS")
      .setLanguage(Language.JS.getLanguageKey())
      .setLanguageName("JavaScript")
      .setRulesUpdatedAt(rulesUpdatedAt)
      .build();
  }

  @NotNull
  private static Qualityprofiles.SearchWsResponse.QualityProfile javaProfile(String rulesUpdatedAt) {
    return Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder()
      .setKey(JAVA_QP_KEY)
      .setName("SonarWay Java")
      .setLanguage(Language.JAVA.getLanguageKey())
      .setLanguageName("Java")
      .setRulesUpdatedAt(rulesUpdatedAt)
      .build();
  }

  private static void mockApiSettings() {
    mockServer.addProtobufResponse("/api/settings/values.protobuf?component=projectKey", Settings.ValuesWsResponse.newBuilder()
      .addSettings(Settings.Setting.newBuilder().setKey("settingKey").setValue("settingValue").build())
      .build());
  }

  private static void mockApiSystemStatus() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"1\", \"status\": \"UP\", \"version\": \"8.9\"}");
  }

  private static void mockApiRulesSearchReturnsOneRule(String profileKey) {
    mockServer.addProtobufResponse(
      "/api/rules/search.protobuf?qprofile=" + profileKey + "&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY,SECURITY_HOTSPOT&s=key&ps=500&p=1",
      Rules.SearchResponse.newBuilder()
        .setTotal(1)
        .setPs(1)
        .addRules(Rules.Rule.newBuilder().setKey("ruleKey").setTemplateKey("templateKey").build())
        .setActives(Rules.Actives.newBuilder()
          .putActives("ruleKey", Rules.ActiveList.newBuilder()
            .addActiveList(Rules.Active.newBuilder()
              .addParams(Rules.Active.Param.newBuilder().setKey("paramKey").setValue("paramValue").build())
              .setSeverity("MAJOR")
              .build())
            .build())
          .build())
        .build());
  }
}
