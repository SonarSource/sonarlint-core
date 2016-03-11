/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.sync;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class RulesSyncTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void rules_sync() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithStreamResponse(
      "/api/rules/search.protobuf?f=repo,name,severity,lang,internalKey,isTemplate,templateKey,htmlDesc,mdDesc,actives&statuses=BETA,DEPRECATED,READY&p=1&ps=500",
      "/sync/rulesp1.pb");
    WsClientTestUtils.addStreamResponse(wsClient,
      "/api/rules/search.protobuf?f=repo,name,severity,lang,internalKey,isTemplate,templateKey,htmlDesc,mdDesc,actives&statuses=BETA,DEPRECATED,READY&p=2&ps=500",
      "/sync/rulesp2.pb");
    WsClientTestUtils.addStreamResponse(wsClient,
      "/api/qualityprofiles/search.protobuf?defaults=true",
      "/sync/default_qualityprofiles.pb");

    RulesSync rulesSync = new RulesSync(wsClient);
    File tempDir = temp.newFolder();
    rulesSync.fetchRulesTo(tempDir.toPath());

    Rules rules = ProtobufUtil.readFile(tempDir.toPath().resolve(StorageManager.RULES_PB), Rules.parser());
    assertThat(rules.getQprofilesByKey()).containsOnlyKeys("cs-sonar-way-12514",
      "js-sonar-way-62960",
      "java-sonar-way-00237",
      "js-sonar-security-way-36063",
      "web-sonar-way-52189",
      "xml-sonar-way-75287");
    assertThat(rules.getDefaultQProfilesByLanguage()).containsOnly(entry("cs", "cs-sonar-way-12514"),
      entry("java", "java-sonar-way-00237"), entry("js", "js-sonar-way-62960"), entry("web", "web-sonar-way-52189"), entry("xml", "xml-sonar-way-75287"));

    assertThat(rules.getRulesByKey()).hasSize(939);

    ActiveRules jsActiveRules = ProtobufUtil.readFile(tempDir.toPath().resolve(StorageManager.ACTIVE_RULES_FOLDER).resolve("js-sonar-way-62960.pb"), ActiveRules.parser());
    assertThat(jsActiveRules.getActiveRulesByKey()).hasSize(85);
  }
}
