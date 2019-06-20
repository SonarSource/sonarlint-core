/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2019 SonarSource SA
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
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonarqube.ws.Rules.SearchResponse;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.container.storage.StoragePaths;
import org.sonarsource.sonarlint.core.proto.Sonarlint.ActiveRules;
import org.sonarsource.sonarlint.core.proto.Sonarlint.Rules;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.container.connected.update.RulesDownloader.RULES_SEARCH_URL;

public class RulesDownloaderTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private ProgressMonitor monitor = mock(ProgressMonitor.class);
  private ProgressWrapper progressWrapper = new ProgressWrapper(monitor);
  private File tempDir;

  @Before
  public void prepare() throws IOException {
    tempDir = temp.newFolder();
    Context context = new Context();
    context.createRepository("javascript", "js").done();
    context.createRepository("squid", "java").done();
  }

  @Test
  public void rules_update_protobuf() {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    emptyMockForAllSeverities(wsClient);
    WsClientTestUtils.addStreamResponse(wsClient, RULES_SEARCH_URL + "&severities=MAJOR&p=1&ps=500", "/update/rulesp1.pb");
    WsClientTestUtils.addStreamResponse(wsClient, RULES_SEARCH_URL + "&severities=MAJOR&p=2&ps=500", "/update/rulesp2.pb");

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    rulesUpdate.fetchRulesTo(tempDir.toPath(), progressWrapper);

    Rules rules = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.RULES_PB), Rules.parser());
    assertThat(rules.getRulesByKeyMap()).hasSize(939);
    ActiveRules jsActiveRules = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.ACTIVE_RULES_FOLDER).resolve(StoragePaths.encodeForFs("js-sonar-way-62960") + ".pb"),
      ActiveRules.parser());
    assertThat(jsActiveRules.getActiveRulesByKeyMap()).hasSize(85);
  }

  @Test
  public void throw_exception_if_server_contains_more_than_10k_rules() throws IOException {
    SearchResponse response = SearchResponse.newBuilder()
      .setTotal(10001)
      .build();
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    emptyMockForAllSeverities(wsClient);
    WsClientTestUtils.addResponse(wsClient, RULES_SEARCH_URL + "&severities=MAJOR&p=1&ps=500", response);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Found more than 10000 rules for severity 'MAJOR' in the SonarQube server, which is not supported by SonarLint.");

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    rulesUpdate.fetchRulesTo(tempDir.toPath(), new ProgressWrapper(null));
  }

  @Test
  public void rules_update_protobuf_with_org() {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    emptyMockForAllSeverities(wsClient, RULES_SEARCH_URL + "&organization=myOrg");
    WsClientTestUtils.addStreamResponse(wsClient, RULES_SEARCH_URL + "&organization=myOrg&severities=MAJOR&p=1&ps=500", "/update/rulesp1.pb");
    WsClientTestUtils.addStreamResponse(wsClient, RULES_SEARCH_URL + "&organization=myOrg&severities=MAJOR&p=2&ps=500", "/update/rulesp2.pb");
    when(wsClient.getOrganizationKey()).thenReturn("myOrg");

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    rulesUpdate.fetchRulesTo(tempDir.toPath(), progressWrapper);

    Rules rules = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.RULES_PB), Rules.parser());
    assertThat(rules.getRulesByKeyMap()).hasSize(939);
    ActiveRules jsActiveRules = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.ACTIVE_RULES_FOLDER).resolve(StoragePaths.encodeForFs("js-sonar-way-62960") + ".pb"),
      ActiveRules.parser());
    assertThat(jsActiveRules.getActiveRulesByKeyMap()).hasSize(85);
  }

  @Test
  public void should_get_rules_of_all_severities() {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    emptyMockForAllSeverities(wsClient);
    WsClientTestUtils.addStreamResponse(wsClient, RULES_SEARCH_URL + "&severities=MAJOR&p=1&ps=500", "/update/rulesp1.pb");
    WsClientTestUtils.addStreamResponse(wsClient, RULES_SEARCH_URL + "&severities=MAJOR&p=2&ps=500", "/update/rulesp2.pb");
    WsClientTestUtils.addStreamResponse(wsClient, RULES_SEARCH_URL + "&severities=INFO&p=1&ps=500", "/update/rules_info_p1.pb");

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    rulesUpdate.fetchRulesTo(tempDir.toPath(), progressWrapper);

    Rules rules = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.RULES_PB), Rules.parser());
    assertThat(rules.getRulesByKeyMap()).hasSize(939 + 34);

    verify(wsClient).get(RULES_SEARCH_URL + "&severities=MAJOR&p=1&ps=500");
    verify(wsClient).get(RULES_SEARCH_URL + "&severities=MAJOR&p=2&ps=500");
    verify(wsClient).get(RULES_SEARCH_URL + "&severities=INFO&p=1&ps=500");
    verify(wsClient).get(RULES_SEARCH_URL + "&severities=CRITICAL&p=1&ps=500");
    verify(wsClient).get(RULES_SEARCH_URL + "&severities=MINOR&p=1&ps=500");
    verify(wsClient).get(RULES_SEARCH_URL + "&severities=BLOCKER&p=1&ps=500");
    verify(wsClient, times(6)).getOrganizationKey();
    verifyNoMoreInteractions(wsClient);

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
  public void unknown_type() throws IOException {
    org.sonarqube.ws.Rules.SearchResponse response = org.sonarqube.ws.Rules.SearchResponse.newBuilder()
      .addRules(org.sonarqube.ws.Rules.Rule.newBuilder().setKey("S:101").build())
      .build();
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    emptyMockForAllSeverities(wsClient);
    WsClientTestUtils.addResponse(wsClient, RULES_SEARCH_URL + "&severities=MAJOR&p=1&ps=500", response);

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);
    rulesUpdate.fetchRulesTo(tempDir.toPath(), progressWrapper);

    Rules saved = ProtobufUtil.readFile(tempDir.toPath().resolve(StoragePaths.RULES_PB), Rules.parser());
    assertThat(saved.getRulesByKeyMap()).hasSize(1);
    assertThat(saved.getRulesByKeyMap().get("S:101").getType()).isEqualTo("");
  }

  @Test
  public void errorReadingStream() {
    SonarLintWsClient wsClient = WsClientTestUtils.createMock();
    emptyMockForAllSeverities(wsClient);
    InputStream stream = new ByteArrayInputStream("trash".getBytes(StandardCharsets.UTF_8));
    WsClientTestUtils.addResponse(wsClient, RULES_SEARCH_URL + "&severities=MAJOR&p=1&ps=500", stream);

    RulesDownloader rulesUpdate = new RulesDownloader(wsClient);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Failed to load rules");
    rulesUpdate.fetchRulesTo(tempDir.toPath(), progressWrapper);
  }

  private void emptyMockForAllSeverities(SonarLintWsClient mock) {
    emptyMockForAllSeverities(mock, RULES_SEARCH_URL);
  }

  private void emptyMockForAllSeverities(SonarLintWsClient mock, String baseUrl) {
    for (Severity s : Severity.values()) {
      WsClientTestUtils.addStreamResponse(mock, baseUrl + "&severities=" + s.name() + "&p=1&ps=500",
        "/update/empty_rules.pb");
    }

  }
}
