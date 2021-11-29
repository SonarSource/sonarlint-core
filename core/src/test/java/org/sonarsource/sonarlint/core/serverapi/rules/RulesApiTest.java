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
package org.sonarsource.sonarlint.core.serverapi.rules;

import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.batch.rule.Severity;
import org.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import testutils.MockWebServerExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.sonarsource.sonarlint.core.serverapi.rules.RulesApi.RULES_SEARCH_URL;

class RulesApiTest {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();

  private final ProgressMonitor monitor = mock(ProgressMonitor.class);
  private final ProgressWrapper progressWrapper = new ProgressWrapper(monitor);
  private final Set<Language> enabledLanguages = Collections.singleton(Language.JS);

  @Test
  void throw_exception_if_server_contains_more_than_10k_rules() {
    Rules.SearchResponse response = Rules.SearchResponse.newBuilder()
      .setTotal(10001)
      .build();
    emptyMockForAllSeverities();
    mockServer.addProtobufResponse(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500", response);

    RulesApi rulesApi = new RulesApi(mockServer.serverApiHelper());

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> rulesApi.getAll(enabledLanguages, progressWrapper));
    assertThat(thrown).hasMessage("Found more than 10000 rules for severity 'MAJOR' in the SonarQube server, which is not supported by SonarLint.");
  }

  @Test
  void should_get_rules_of_all_severities() {
    emptyMockForAllSeverities();
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500", "/update/rulesp1.pb");
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=2&ps=500", "/update/rulesp2.pb");
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=INFO&languages=js&p=1&ps=500", "/update/rules_info_p1.pb");

    RulesApi rulesApi = new RulesApi(mockServer.serverApiHelper());
    ServerRules serverRules = rulesApi.getAll(enabledLanguages, progressWrapper);

    Sonarlint.Rules rules = serverRules.getAll();
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
  void unknown_type() {
    org.sonarqube.ws.Rules.SearchResponse response = org.sonarqube.ws.Rules.SearchResponse.newBuilder()
      .addRules(org.sonarqube.ws.Rules.Rule.newBuilder().setKey("S:101").build())
      .build();
    emptyMockForAllSeverities();
    mockServer.addProtobufResponse(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500", response);

    RulesApi rulesApi = new RulesApi(mockServer.serverApiHelper());
    ServerRules serverRules = rulesApi.getAll(enabledLanguages, progressWrapper);

    Sonarlint.Rules allRules = serverRules.getAll();
    assertThat(allRules.getRulesByKeyMap()).hasSize(1);
    assertThat(allRules.getRulesByKeyMap().get("S:101").getType()).isEmpty();
  }

  @Test
  void errorReadingStream() {
    emptyMockForAllSeverities();
    mockServer.addStringResponse(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500", "trash");

    RulesApi rulesApi = new RulesApi(mockServer.serverApiHelper());

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> rulesApi.getAll(enabledLanguages, progressWrapper));
    assertThat(thrown).hasMessage("Failed to load rules");
  }

  private void emptyMockForAllSeverities() {
    for (Severity s : Severity.values()) {
      mockServer.addResponseFromResource(RulesApi.RULES_SEARCH_URL + "&severities=" + s.name() + "&languages=" + "js" + "&p=1&ps=500", "/update/empty_rules.pb");
    }
  }
}
