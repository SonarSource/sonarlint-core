/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.storage;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.ProjectBranches;
import org.sonarqube.ws.Qualityprofiles;
import org.sonarqube.ws.Rules;
import org.sonarqube.ws.Settings;
import org.sonarsource.sonarlint.core.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageSynchronizerTests {
  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();
  private final ProgressMonitor progressMonitor = new ProgressMonitor(null);

  @BeforeEach
  void prepare() {
    mockServer.addStringResponse("/api/plugins/installed", "{\"plugins\": []}");
  }

  @Test
  void should_synchronize_a_project_with_a_single_active_rule(@TempDir Path tmpDir) {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"1\", \"status\": \"UP\", \"version\": \"1\"}");
    mockServer.addProtobufResponse("/api/settings/values.protobuf?component=projectKey", Settings.ValuesWsResponse.newBuilder()
      .addSettings(Settings.Setting.newBuilder().setKey("settingKey").setValue("settingValue").build())
      .build());
    mockServer.addProtobufResponse("/api/qualityprofiles/search.protobuf?project=projectKey", Qualityprofiles.SearchWsResponse.newBuilder()
      .addProfiles(Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder()
        .setKey("qpKey")
        .setIsDefault(true)
        .setName("qpName")
        .setLanguage("js")
        .setLanguageName("Javascript")
        .setActiveRuleCount(1)
        .setRulesUpdatedAt("2020-10-27T23:08:58+0000")
        .setUserUpdatedAt("2020-10-27T23:08:58+0000")
        .build())
      .build());
    mockServer.addProtobufResponse("/api/rules/search.protobuf?qprofile=qpKey&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY&ps=500&p=1",
      Rules.SearchResponse.newBuilder()
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
    mockServer.addProtobufResponse("/api/project_branches/list.protobuf?project=projectKey", ProjectBranches.ListWsResponse.newBuilder()
      .addBranches(ProjectBranches.Branch.newBuilder().setName("feature/foo").setIsMain(false))
      .addBranches(ProjectBranches.Branch.newBuilder().setName("master").setIsMain(true)).build());
    var synchronizer = new LocalStorageSynchronizer(Set.of(Language.JS), emptySet(), new PluginsStorage(tmpDir), new ProjectStorage(tmpDir));

    synchronizer.synchronize(new ServerApi(mockServer.serverApiHelper()), Set.of("projectKey"), progressMonitor);

    var analyzerConfigFile = tmpDir.resolve("70726f6a6563744b6579/analyzer_config.pb");
    assertThat(analyzerConfigFile).exists();
    var analyzerConfiguration = ProtobufUtil.readFile(analyzerConfigFile, Sonarlint.AnalyzerConfiguration.parser());
    assertThat(analyzerConfiguration.getSettingsMap()).containsEntry("settingKey", "settingValue");
    var ruleSetsByLanguageKeyMap = analyzerConfiguration.getRuleSetsByLanguageKeyMap();
    assertThat(ruleSetsByLanguageKeyMap).containsKey("js");
    var ruleSet = ruleSetsByLanguageKeyMap.get("js");
    assertThat(ruleSet.getRulesCount()).isEqualTo(1);
    var activeRule = ruleSet.getRulesList().get(0);
    assertThat(activeRule.getRuleKey()).isEqualTo("ruleKey");
    assertThat(activeRule.getSeverity()).isEqualTo("MAJOR");
    assertThat(activeRule.getTemplateKey()).isEqualTo("templateKey");
    assertThat(activeRule.getParamsMap()).containsEntry("paramKey", "paramValue");

    var projectBranchesFile = tmpDir.resolve("70726f6a6563744b6579/project_branches.pb");
    assertThat(projectBranchesFile).exists();
    var projectBranches = ProtobufUtil.readFile(projectBranchesFile, Sonarlint.ProjectBranches.parser());
    assertThat(projectBranches.getBranchNameList()).containsOnly("feature/foo", "master");
    assertThat(projectBranches.getMainBranchName()).isEqualTo("master");
  }

  @Test
  void should_not_synchronize_up_to_date_ruleset(@TempDir Path tmpDir) {
    var storageFile = tmpDir.resolve("70726f6a6563744b6579/analyzer_config.pb");
    FileUtils.mkdirs(storageFile.getParent());
    ProtobufUtil.writeToFile(Sonarlint.AnalyzerConfiguration.newBuilder()
      .putAllRuleSetsByLanguageKey(Map.of("js", Sonarlint.RuleSet.newBuilder()
        .setLastModified("2020-10-27T23:08:58+0000")
        .build()))
      .build(), storageFile);
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"1\", \"status\": \"UP\", \"version\": \"1\"}");
    mockServer.addProtobufResponse("/api/settings/values.protobuf?component=projectKey", Settings.ValuesWsResponse.newBuilder()
      .addSettings(Settings.Setting.newBuilder().setKey("settingKey").setValue("settingValue").build())
      .build());
    mockServer.addProtobufResponse("/api/qualityprofiles/search.protobuf?project=projectKey", Qualityprofiles.SearchWsResponse.newBuilder()
      .addProfiles(Qualityprofiles.SearchWsResponse.QualityProfile.newBuilder()
        .setKey("qpKey")
        .setIsDefault(true)
        .setName("qpName")
        .setLanguage("js")
        .setLanguageName("Javascript")
        .setActiveRuleCount(1)
        .setRulesUpdatedAt("2020-10-27T23:08:58+0000")
        .setUserUpdatedAt("2020-10-27T23:08:58+0000")
        .build())
      .build());
    var synchronizer = new LocalStorageSynchronizer(Set.of(Language.JS), emptySet(), new PluginsStorage(tmpDir), new ProjectStorage(tmpDir));

    synchronizer.synchronize(new ServerApi(mockServer.serverApiHelper()), Set.of("projectKey"), progressMonitor);

    assertThat(storageFile).exists();
    var analyzerConfiguration = ProtobufUtil.readFile(storageFile, Sonarlint.AnalyzerConfiguration.parser());
    assertThat(analyzerConfiguration.getSettingsMap()).containsEntry("settingKey", "settingValue");
  }

  @Test
  void should_not_synchronize_when_server_is_down(@TempDir Path tmpDir) {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"1\", \"status\": \"DOWN\", \"version\": \"1\"}");
    var synchronizer = new LocalStorageSynchronizer(Set.of(Language.JS), emptySet(), new PluginsStorage(tmpDir), new ProjectStorage(tmpDir));

    synchronizer.synchronize(new ServerApi(mockServer.serverApiHelper()), Set.of("projectKey"), progressMonitor);

    var storageFile = tmpDir.resolve("70726f6a6563744b6579/analyzer_config.pb");
    assertThat(storageFile).doesNotExist();
  }
}
