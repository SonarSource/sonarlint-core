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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonarqube.ws.Rules.SearchResponse;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.storage.ActiveRulesStore;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.RulesStore;
import org.sonarsource.sonarlint.core.container.storage.StorageFolder;
import org.sonarsource.sonarlint.core.container.storage.ProjectStoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.container.connected.update.RulesDownloader.RULES_SEARCH_URL;

class RulesDownloaderTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private final ProgressMonitor monitor = mock(ProgressMonitor.class);
  private final ProgressWrapper progressWrapper = new ProgressWrapper(monitor);
  private final ConnectedGlobalConfiguration globalConfig = mock(ConnectedGlobalConfiguration.class);

  @BeforeEach
  public void prepare() throws IOException {
    Context context = new Context();
    context.createRepository("javascript", "js").done();
    context.createRepository("squid", "java").done();
    when(globalConfig.getEnabledLanguages()).thenReturn(Collections.singleton(Language.JS));
  }

  @Test
  void rules_update_protobuf(@TempDir Path tempDir) {
    StorageFolder.Default storageFolder = new StorageFolder.Default(tempDir);
    RulesStore rulesStore = new RulesStore(storageFolder);
    ActiveRulesStore activeRulesStore = new ActiveRulesStore(storageFolder);
    emptyMockForAllSeverities("js,java");
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=MAJOR&languages=js,java&p=1&ps=500", "/update/rulesp1.pb");
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=MAJOR&languages=js,java&p=2&ps=500", "/update/rulesp2.pb");
    when(globalConfig.getEnabledLanguages()).thenReturn(new LinkedHashSet<>(Arrays.asList(Language.JS, Language.JAVA)));

    RulesDownloader rulesUpdate = new RulesDownloader(mockServer.serverApiHelper(), globalConfig, rulesStore, activeRulesStore);
    rulesUpdate.fetchRules(progressWrapper);

    Rules rules = ProtobufUtil.readFile(tempDir.resolve(RulesStore.RULES_PB), Rules.parser());
    assertThat(rules.getRulesByKeyMap()).hasSize(939);
    ActiveRules jsActiveRules = ProtobufUtil.readFile(tempDir.resolve(ActiveRulesStore.ACTIVE_RULES_FOLDER).resolve(ProjectStoragePaths.encodeForFs("js-sonar-way-62960") + ".pb"),
      ActiveRules.parser());
    assertThat(jsActiveRules.getActiveRulesByKeyMap()).hasSize(85);
  }

  @Test
  void throw_exception_if_server_contains_more_than_10k_rules(@TempDir Path tempDir) {
    StorageFolder.Default storageFolder = new StorageFolder.Default(tempDir);
    RulesStore rulesStore = new RulesStore(storageFolder);
    ActiveRulesStore activeRulesStore = new ActiveRulesStore(storageFolder);
    SearchResponse response = SearchResponse.newBuilder()
      .setTotal(10001)
      .build();
    emptyMockForAllSeverities("js");
    mockServer.addProtobufResponse(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500", response);

    RulesDownloader rulesUpdate = new RulesDownloader(mockServer.serverApiHelper(), globalConfig, rulesStore, activeRulesStore);

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> rulesUpdate.fetchRules(progressWrapper));
    assertThat(thrown).hasMessage("Found more than 10000 rules for severity 'MAJOR' in the SonarQube server, which is not supported by SonarLint.");
  }

  @Test
  void rules_update_protobuf_with_org(@TempDir Path tempDir) {
    StorageFolder.Default storageFolder = new StorageFolder.Default(tempDir);
    RulesStore rulesStore = new RulesStore(storageFolder);
    ActiveRulesStore activeRulesStore = new ActiveRulesStore(storageFolder);
    emptyMockForAllSeverities(RULES_SEARCH_URL + "&organization=myOrg", "js");
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&organization=myOrg&severities=MAJOR&languages=js&p=1&ps=500", "/update/rulesp1.pb");
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&organization=myOrg&severities=MAJOR&languages=js&p=2&ps=500", "/update/rulesp2.pb");

    RulesDownloader rulesUpdate = new RulesDownloader(mockServer.serverApiHelper("myOrg"), globalConfig, rulesStore, activeRulesStore);
    rulesUpdate.fetchRules(progressWrapper);

    Rules rules = ProtobufUtil.readFile(tempDir.resolve(RulesStore.RULES_PB), Rules.parser());
    assertThat(rules.getRulesByKeyMap()).hasSize(939);
    ActiveRules jsActiveRules = ProtobufUtil.readFile(tempDir.resolve(ActiveRulesStore.ACTIVE_RULES_FOLDER).resolve(ProjectStoragePaths.encodeForFs("js-sonar-way-62960") + ".pb"),
      ActiveRules.parser());
    assertThat(jsActiveRules.getActiveRulesByKeyMap()).hasSize(85);
  }

  @Test
  void should_get_rules_of_all_severities(@TempDir Path tempDir) {
    StorageFolder.Default storageFolder = new StorageFolder.Default(tempDir);
    RulesStore rulesStore = new RulesStore(storageFolder);
    ActiveRulesStore activeRulesStore = new ActiveRulesStore(storageFolder);
    emptyMockForAllSeverities("js");
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500", "/update/rulesp1.pb");
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=2&ps=500", "/update/rulesp2.pb");
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=INFO&languages=js&p=1&ps=500", "/update/rules_info_p1.pb");

    RulesDownloader rulesUpdate = new RulesDownloader(mockServer.serverApiHelper(), globalConfig, rulesStore, activeRulesStore);
    rulesUpdate.fetchRules(progressWrapper);

    Rules rules = ProtobufUtil.readFile(tempDir.resolve(RulesStore.RULES_PB), Rules.parser());
    assertThat(rules.getRulesByKeyMap()).hasSize(939 + 34);

    assertThat(mockServer.takeRequest().getPath()).isEqualTo(RULES_SEARCH_URL + "&severities=INFO&languages=js&p=1&ps=500");
    assertThat(mockServer.takeRequest().getPath()).isEqualTo(RULES_SEARCH_URL + "&severities=MINOR&languages=js&p=1&ps=500");
    assertThat(mockServer.takeRequest().getPath()).isEqualTo(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500");
    assertThat(mockServer.takeRequest().getPath()).isEqualTo(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=2&ps=500");
    assertThat(mockServer.takeRequest().getPath()).isEqualTo(RULES_SEARCH_URL + "&severities=CRITICAL&languages=js&p=1&ps=500");
    assertThat(mockServer.takeRequest().getPath()).isEqualTo(RULES_SEARCH_URL + "&severities=BLOCKER&languages=js&p=1&ps=500");

    verify(monitor).setMessage("Loading severity 'info'");
    verify(monitor).setFraction(0.0f);
    verify(monitor).setMessage("Loading severity 'major'");
    verify(monitor).setFraction(0.2f);
    verify(monitor).setMessage("Loading severity 'minor'");
    verify(monitor).setFraction(0.4f);
    verify(monitor).setMessage("Loading severity 'blocker'");
    verify(monitor).setFraction(0.6f);
    verify(monitor).setMessage("Loading severity 'critical'");
    verify(monitor).setFraction(0.8f);

    verify(monitor).setMessage("major - Loading page 1");
  }

  @Test
  void unknown_type(@TempDir Path tempDir) {
    StorageFolder.Default storageFolder = new StorageFolder.Default(tempDir);
    RulesStore rulesStore = new RulesStore(storageFolder);
    ActiveRulesStore activeRulesStore = new ActiveRulesStore(storageFolder);
    org.sonarqube.ws.Rules.SearchResponse response = org.sonarqube.ws.Rules.SearchResponse.newBuilder()
      .addRules(org.sonarqube.ws.Rules.Rule.newBuilder().setKey("S:101").build())
      .build();
    emptyMockForAllSeverities("js");
    mockServer.addProtobufResponse(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500", response);

    RulesDownloader rulesUpdate = new RulesDownloader(mockServer.serverApiHelper(), globalConfig, rulesStore, activeRulesStore);
    rulesUpdate.fetchRules(progressWrapper);

    Rules saved = ProtobufUtil.readFile(tempDir.resolve(RulesStore.RULES_PB), Rules.parser());
    assertThat(saved.getRulesByKeyMap()).hasSize(1);
    assertThat(saved.getRulesByKeyMap().get("S:101").getType()).isEmpty();
  }

  @Test
  void errorReadingStream(@TempDir Path tempDir) {
    StorageFolder.Default storageFolder = new StorageFolder.Default(tempDir);
    RulesStore rulesStore = new RulesStore(storageFolder);
    ActiveRulesStore activeRulesStore = new ActiveRulesStore(storageFolder);
    emptyMockForAllSeverities("js");
    mockServer.addStringResponse(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500", "trash");

    RulesDownloader rulesUpdate = new RulesDownloader(mockServer.serverApiHelper(), globalConfig, rulesStore, activeRulesStore);

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> rulesUpdate.fetchRules(progressWrapper));
    assertThat(thrown).hasMessage("Failed to load rules");
  }

  private void emptyMockForAllSeverities(String languages) {
    emptyMockForAllSeverities(RULES_SEARCH_URL, languages);
  }

  private void emptyMockForAllSeverities(String baseUrl, String languages) {
    for (Severity s : Severity.values()) {
      mockServer.addResponseFromResource(baseUrl + "&severities=" + s.name() + "&languages=" + languages + "&p=1&ps=500", "/update/empty_rules.pb");
    }

  }
}
