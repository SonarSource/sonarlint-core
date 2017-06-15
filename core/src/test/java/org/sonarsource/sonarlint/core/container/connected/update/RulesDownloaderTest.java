/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.container.connected.update.RulesDownloader.RULES_SEARCH_URL;

public class RulesDownloaderTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();

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
      RULES_SEARCH_URL + "&p=1&ps=500",
      "/update/rulesp1.pb");
    WsClientTestUtils.addStreamResponse(wsClient,
      RULES_SEARCH_URL + "&p=2&ps=500",
      "/update/rulesp2.pb");

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    File tempDir = temp.newFolder();
    rulesUpdate.fetchRulesTo(tempDir.toPath());

    Rules rules = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.RULES_PB), Rules.parser());
    assertThat(rules.getRulesByKeyMap()).hasSize(939);
    ActiveRules jsActiveRules = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.ACTIVE_RULES_FOLDER).resolve("js-sonar-way-62960.pb"), ActiveRules.parser());
    assertThat(jsActiveRules.getActiveRulesByKeyMap()).hasSize(85);
  }

  @Test
  public void rules_update_protobuf_with_org() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithStreamResponse(
      RULES_SEARCH_URL + "&organization=myOrg&p=1&ps=500",
      "/update/rulesp1.pb");
    WsClientTestUtils.addStreamResponse(wsClient,
      RULES_SEARCH_URL + "&organization=myOrg&p=2&ps=500",
      "/update/rulesp2.pb");
    when(wsClient.getOrganizationKey()).thenReturn("myOrg");

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    File tempDir = temp.newFolder();
    rulesUpdate.fetchRulesTo(tempDir.toPath());

    Rules rules = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.RULES_PB), Rules.parser());
    assertThat(rules.getRulesByKeyMap()).hasSize(939);
    ActiveRules jsActiveRules = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.ACTIVE_RULES_FOLDER).resolve("js-sonar-way-62960.pb"), ActiveRules.parser());
    assertThat(jsActiveRules.getActiveRulesByKeyMap()).hasSize(85);
  }

  @Test
  public void unknown_type() throws IOException {
    org.sonarqube.ws.Rules.SearchResponse response = org.sonarqube.ws.Rules.SearchResponse.newBuilder()
      .addRules(org.sonarqube.ws.Rules.Rule.newBuilder().setKey("S:101").build())
      .build();
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    WsClientTestUtils.addResponse(wsClient, RULES_SEARCH_URL + "&p=1&ps=500", response);

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    File tempDir = temp.newFolder();
    rulesUpdate.fetchRulesTo(tempDir.toPath());

    Rules saved = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.RULES_PB), Rules.parser());
    assertThat(saved.getRulesByKeyMap()).hasSize(1);
    assertThat(saved.getRulesByKeyMap().get("S:101").getType()).isEqualTo("");
  }

  @Test
  public void errorReadingStream() throws IOException {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    InputStream stream = new ByteArrayInputStream("trash".getBytes(StandardCharsets.UTF_8));
    WsClientTestUtils.addResponse(wsClient, RULES_SEARCH_URL + "&p=1&ps=500", stream);

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    File tempDir = temp.newFolder();
    exception.expect(IllegalStateException.class);
    exception.expectMessage("Failed to load rules");
    rulesUpdate.fetchRulesTo(tempDir.toPath());
  }
}
