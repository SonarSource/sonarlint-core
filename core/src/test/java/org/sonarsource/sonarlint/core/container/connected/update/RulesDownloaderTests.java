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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.serverapi.rules.RulesApi.RULES_SEARCH_URL;

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
    emptyMockForAllSeverities();
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=MAJOR&languages=java,js&p=1&ps=500", "/update/rulesp1.pb");
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=MAJOR&languages=java,js&p=2&ps=500", "/update/rulesp2.pb");
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

  private void emptyMockForAllSeverities() {
    emptyMockForAllSeverities(RULES_SEARCH_URL, "java,js");
  }

  private void emptyMockForAllSeverities(String baseUrl, String languages) {
    for (Severity s : Severity.values()) {
      mockServer.addResponseFromResource(baseUrl + "&severities=" + s.name() + "&languages=" + languages + "&p=1&ps=500", "/update/empty_rules.pb");
    }
  }
}
