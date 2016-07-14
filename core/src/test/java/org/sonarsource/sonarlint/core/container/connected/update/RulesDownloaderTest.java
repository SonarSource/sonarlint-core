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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StorageManager;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules;

import static org.assertj.core.api.Assertions.assertThat;

public class RulesDownloaderTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void prepare() {
    Context context = new Context();
    context.createRepository("javascript", "js").done();
    context.createRepository("squid", "java").done();
  }

  @Test
  public void rules_update_protobuf() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithStreamResponse(
      "/api/rules/search.protobuf?f=repo,name,severity,lang,htmlDesc,internalKey,isTemplate,templateKey,actives&statuses=BETA,DEPRECATED,READY&p=1&ps=500",
      "/update/rulesp1.pb");
    WsClientTestUtils.addStreamResponse(wsClient,
      "/api/rules/search.protobuf?f=repo,name,severity,lang,htmlDesc,internalKey,isTemplate,templateKey,actives&statuses=BETA,DEPRECATED,READY&p=2&ps=500",
      "/update/rulesp2.pb");

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    File tempDir = temp.newFolder();
    rulesUpdate.fetchRulesTo(tempDir.toPath(), "5.5");

    Rules rules = ProtobufUtil.readFile(tempDir.toPath().resolve(StorageManager.RULES_PB), Rules.parser());
    assertThat(rules.getRulesByKey()).hasSize(939);
    assertThat(rules.getRulesByKey().values().iterator().next().getHtmlDesc()).isNotEqualTo(RulesDownloader.MSG_NO_DESC);
    ActiveRules jsActiveRules = ProtobufUtil.readFile(tempDir.toPath().resolve(StorageManager.ACTIVE_RULES_FOLDER).resolve("js-sonar-way-62960.pb"), ActiveRules.parser());
    assertThat(jsActiveRules.getActiveRulesByKey()).hasSize(85);
  }

  @Test
  public void rules_update_protobuf_before_5dot5() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithStreamResponse(
      "/api/rules/search.protobuf?f=repo,name,severity,lang,htmlDesc,mdDesc,internalKey,isTemplate,templateKey,actives&statuses=BETA,DEPRECATED,READY&p=1&ps=500",
      "/update/rulesp1.pb");
    WsClientTestUtils.addStreamResponse(wsClient,
      "/api/rules/search.protobuf?f=repo,name,severity,lang,htmlDesc,mdDesc,internalKey,isTemplate,templateKey,actives&statuses=BETA,DEPRECATED,READY&p=2&ps=500",
      "/update/rulesp2.pb");

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    File tempDir = temp.newFolder();
    rulesUpdate.fetchRulesTo(tempDir.toPath(), "5.2");

    Rules rules = ProtobufUtil.readFile(tempDir.toPath().resolve(StorageManager.RULES_PB), Rules.parser());
    assertThat(rules.getRulesByKey()).hasSize(939);
    assertThat(rules.getRulesByKey().values().iterator().next().getHtmlDesc()).isNotEqualTo(RulesDownloader.MSG_NO_DESC);
    ActiveRules jsActiveRules = ProtobufUtil.readFile(tempDir.toPath().resolve(StorageManager.ACTIVE_RULES_FOLDER).resolve("js-sonar-way-62960.pb"), ActiveRules.parser());
    assertThat(jsActiveRules.getActiveRulesByKey()).hasSize(85);
  }

  @Test
  public void rules_update_json() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithReaderResponse(
      "/api/rules/search?f=repo,name,severity,lang,htmlDesc,internalKey,isTemplate,templateKey,actives&statuses=BETA,DEPRECATED,READY&p=1&ps=500",
      new InputStreamReader(this.getClass().getResourceAsStream("/update/rulesp1.json"), StandardCharsets.UTF_8));

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    File tempDir = temp.newFolder();
    rulesUpdate.fetchRulesTo(tempDir.toPath(), "5.1");

    Rules rules = ProtobufUtil.readFile(tempDir.toPath().resolve(StorageManager.RULES_PB), Rules.parser());
    assertThat(rules.getRulesByKey()).hasSize(459);
    assertThat(rules.getRulesByKey().values().iterator().next().getHtmlDesc()).isNotEqualTo(RulesDownloader.MSG_NO_DESC);
    ActiveRules jsActiveRules = ProtobufUtil.readFile(tempDir.toPath().resolve(StorageManager.ACTIVE_RULES_FOLDER).resolve("java-sonar-way-62501.pb"), ActiveRules.parser());
    assertThat(jsActiveRules.getActiveRulesByKey()).hasSize(231);
  }

  @Test
  public void rules_update_json_no_desc() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithReaderResponse(
      "/api/rules/search?f=repo,name,severity,lang,internalKey,isTemplate,templateKey,actives&statuses=BETA,DEPRECATED,READY&p=1&ps=500",
      new InputStreamReader(this.getClass().getResourceAsStream("/update/rulesp1nodesc.json"), StandardCharsets.UTF_8));

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    File tempDir = temp.newFolder();
    rulesUpdate.fetchRulesTo(tempDir.toPath(), "4.5.6");

    Rules rules = ProtobufUtil.readFile(tempDir.toPath().resolve(StorageManager.RULES_PB), Rules.parser());
    assertThat(rules.getRulesByKey()).hasSize(459);
    assertThat(rules.getRulesByKey().values().iterator().next().getHtmlDesc()).isEqualTo(RulesDownloader.MSG_NO_DESC);
    ActiveRules jsActiveRules = ProtobufUtil.readFile(tempDir.toPath().resolve(StorageManager.ACTIVE_RULES_FOLDER).resolve("java-sonar-way-62501.pb"), ActiveRules.parser());
    assertThat(jsActiveRules.getActiveRulesByKey()).hasSize(231);
  }
}
