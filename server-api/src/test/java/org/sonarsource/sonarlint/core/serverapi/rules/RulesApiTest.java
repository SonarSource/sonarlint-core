/*
 * SonarLint Server API
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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.rules.RulesApi.Severity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarlint.core.serverapi.rules.RulesApi.RULES_SEARCH_URL;

class RulesApiTest {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private final ProgressMonitor progress = mock(ProgressMonitor.class);
  private final Set<String> enabledLanguageKeys = Collections.singleton("js");

  @Test
  void throw_exception_if_server_contains_more_than_10k_rules() {
    Rules.SearchResponse response = Rules.SearchResponse.newBuilder()
      .setTotal(10001)
      .build();
    emptyMockForAllSeverities();
    mockServer.addProtobufResponse(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500", response);

    RulesApi rulesApi = new RulesApi(mockServer.serverApiHelper());

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> rulesApi.getAll(enabledLanguageKeys, progress));
    assertThat(thrown).hasMessage("Found more than 10000 rules for severity 'MAJOR' in the SonarQube server, which is not supported by SonarLint.");
  }

  @Test
  void should_get_rules_of_all_severities() {
    when(progress.subProgress(anyFloat(), anyFloat(), anyString())).thenReturn(progress);

    emptyMockForAllSeverities();
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500", "/update/rulesp1.pb");
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=2&ps=500", "/update/rulesp2.pb");
    mockServer.addResponseFromResource(RULES_SEARCH_URL + "&severities=INFO&languages=js&p=1&ps=500", "/update/rules_info_p1.pb");

    RulesApi rulesApi = new RulesApi(mockServer.serverApiHelper());
    ServerRules serverRules = rulesApi.getAll(enabledLanguageKeys, progress);

    List<ServerRules.Rule> rules = serverRules.getAll();
    assertThat(rules).hasSize(939 + 34);
    assertThat(serverRules.getActiveRulesByQualityProfileKey().values().stream().flatMap(Collection::stream)).hasSize(500);

    assertThat(mockServer.takeRequest().getPath()).isEqualTo(RULES_SEARCH_URL + "&severities=INFO&languages=js&p=1&ps=500");
    assertThat(mockServer.takeRequest().getPath()).isEqualTo(RULES_SEARCH_URL + "&severities=MINOR&languages=js&p=1&ps=500");
    assertThat(mockServer.takeRequest().getPath()).isEqualTo(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500");
    assertThat(mockServer.takeRequest().getPath()).isEqualTo(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=2&ps=500");
    assertThat(mockServer.takeRequest().getPath()).isEqualTo(RULES_SEARCH_URL + "&severities=CRITICAL&languages=js&p=1&ps=500");
    assertThat(mockServer.takeRequest().getPath()).isEqualTo(RULES_SEARCH_URL + "&severities=BLOCKER&languages=js&p=1&ps=500");

    verify(progress).setProgressAndCheckCancel("Loading severity 'info'", 0.0f);
    verify(progress).setProgressAndCheckCancel("Loading severity 'minor'", 0.2f);
    verify(progress).setProgressAndCheckCancel("Loading severity 'major'", 0.4f);
    verify(progress).setProgressAndCheckCancel("Loading severity 'critical'", 0.6f);
    verify(progress).setProgressAndCheckCancel("Loading severity 'blocker'", 0.8f);
  }

  @Test
  void unknown_type() {
    org.sonarqube.ws.Rules.SearchResponse response = org.sonarqube.ws.Rules.SearchResponse.newBuilder()
      .addRules(org.sonarqube.ws.Rules.Rule.newBuilder().setKey("S:101").build())
      .build();
    emptyMockForAllSeverities();
    mockServer.addProtobufResponse(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500", response);

    RulesApi rulesApi = new RulesApi(mockServer.serverApiHelper());
    ServerRules serverRules = rulesApi.getAll(enabledLanguageKeys, progress);

    List<ServerRules.Rule> allRules = serverRules.getAll();
    assertThat(allRules).hasSize(1);
    assertThat(allRules.stream().filter(r -> "S:101".equals(r.getRuleKey())).findAny().get().getType()).isEmpty();
  }

  @Test
  void errorReadingStream() {
    emptyMockForAllSeverities();
    mockServer.addStringResponse(RULES_SEARCH_URL + "&severities=MAJOR&languages=js&p=1&ps=500", "trash");

    RulesApi rulesApi = new RulesApi(mockServer.serverApiHelper());

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> rulesApi.getAll(enabledLanguageKeys, progress));
    assertThat(thrown).hasMessage("Failed to load rules");
  }

  @Test
  void should_get_rule_description() {
    mockServer.addProtobufResponse("/api/rules/show.protobuf?key=java:S1234",
      Rules.ShowResponse.newBuilder().setRule(
        Rules.Rule.newBuilder()
          .setHtmlDesc("htmlDesc")
          .setHtmlNote("htmlNote")
          .build())
        .build());

    var rulesApi = new RulesApi(mockServer.serverApiHelper());

    var ruleDescription = rulesApi.getRuleDescription("java:S1234");

    assertThat(ruleDescription).contains("htmlDesc\nhtmlNote");
  }

  @Test
  void should_get_active_rules_of_a_given_quality_profile() {
    mockServer.addProtobufResponse(
      "/api/rules/search.protobuf?qprofile=QPKEY&organization=orgKey&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY&ps=500&p=1",
      Rules.SearchResponse.newBuilder()
        .setTotal(1)
        .setPs(1)
        .setActives(
          Rules.Actives.newBuilder()
            .putActives("repo:key", Rules.ActiveList.newBuilder().addActiveList(
              Rules.Active.newBuilder()
                .setSeverity("MAJOR")
                .addParams(Rules.Active.Param.newBuilder().setKey("paramKey").setValue("paramValue").build())
                .build())
              .build())
            .build())
        .build());

    var rulesApi = new RulesApi(mockServer.serverApiHelper("orgKey"));

    var activeRules = rulesApi.getAllActiveRules("QPKEY", progress);

    assertThat(activeRules)
      .extracting("ruleKey", "severity")
      .containsOnly(tuple("repo:key", "MAJOR"));
    assertThat(activeRules)
      .flatExtracting("params")
      .extracting("key", "value")
      .containsOnly(tuple("paramKey", "paramValue"));
  }

  private void emptyMockForAllSeverities() {
    for (Severity s : Severity.values()) {
      mockServer.addResponseFromResource(RulesApi.RULES_SEARCH_URL + "&severities=" + s.name() + "&languages=" + "js" + "&p=1&ps=500", "/update/empty_rules.pb");
    }
  }
}
