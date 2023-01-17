/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.rules;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.exception.UnexpectedBodyException;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

class RulesApiTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private final ProgressMonitor progress = mock(ProgressMonitor.class);

  @Test
  void errorReadingRuleDescription() {
    mockServer.addStringResponse("/api/rules/show.protobuf?key=java:S1234", "trash");

    var rulesApi = new RulesApi(mockServer.serverApiHelper());

    var error = catchThrowable(() -> rulesApi.getRule("java:S1234").get());
    assertThat(error).hasCauseInstanceOf(UnexpectedBodyException.class);
  }

  @Test
  void should_get_rule() throws ExecutionException, InterruptedException {
    mockServer.addProtobufResponse("/api/rules/show.protobuf?key=java:S1234",
      Rules.ShowResponse.newBuilder().setRule(
        Rules.Rule.newBuilder()
          .setName("name")
          .setSeverity("MINOR")
          .setType(Common.RuleType.VULNERABILITY)
          .setLang(Language.PYTHON.getLanguageKey())
          .setHtmlDesc("htmlDesc")
          .setHtmlNote("htmlNote")
          .build())
        .build());

    var rulesApi = new RulesApi(mockServer.serverApiHelper());

    var rule = rulesApi.getRule("java:S1234").get();

    assertThat(rule).extracting("name", "severity", "type", "language", "htmlDesc", "htmlNote")
      .contains("name", IssueSeverity.MINOR, RuleType.VULNERABILITY, Language.PYTHON, "htmlDesc", "htmlNote");
  }

  @Test
  void should_get_rule_with_description_sections() throws ExecutionException, InterruptedException {
    mockServer.addProtobufResponse("/api/rules/show.protobuf?key=java:S1234",
      Rules.ShowResponse.newBuilder().setRule(
        Rules.Rule.newBuilder()
          .setName("name")
          .setSeverity("MINOR")
          .setType(Common.RuleType.VULNERABILITY)
          .setLang(Language.PYTHON.getLanguageKey())
          .setHtmlDesc("htmlDesc")
          .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder()
            .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder().setKey("sectionKey").setContent("htmlContent").build())
            .addDescriptionSections(
              Rules.Rule.DescriptionSection.newBuilder().setKey("sectionKey2").setContent("htmlContent2").setContext(Rules.Rule.DescriptionSection.Context.newBuilder()
                .setKey("contextKey").setDisplayName("displayName").build()).build())
            .build())
          .setHtmlNote("htmlNote")
          .build())
        .build());

    var rulesApi = new RulesApi(mockServer.serverApiHelper());

    var rule = rulesApi.getRule("java:S1234").get();

    assertThat(rule).extracting("name", "severity", "type", "language", "htmlDesc", "htmlNote")
      .contains("name", IssueSeverity.MINOR, RuleType.VULNERABILITY, Language.PYTHON, "htmlDesc", "htmlNote");

    var sections = rule.getDescriptionSections();
    assertThat(sections).hasSize(2);
    assertThat(sections.get(0)).extracting("key", "htmlContent", "context")
      .containsExactly("sectionKey", "htmlContent", Optional.empty());
    assertThat(sections.get(1)).extracting("key", "htmlContent")
      .containsExactly("sectionKey2", "htmlContent2");
    assertThat(sections.get(1).getContext()).hasValueSatisfying(context -> {
      assertThat(context.getKey()).isEqualTo("contextKey");
      assertThat(context.getDisplayName()).isEqualTo("displayName");
    });

  }

  @Test
  void should_get_rule_from_organization() throws ExecutionException, InterruptedException {
    mockServer.addProtobufResponse("/api/rules/show.protobuf?key=java:S1234&organization=orgKey",
      Rules.ShowResponse.newBuilder().setRule(
        Rules.Rule.newBuilder()
          .setName("name")
          .setSeverity("MAJOR")
          .setType(Common.RuleType.VULNERABILITY)
          .setLang(Language.PYTHON.getLanguageKey())
          .setHtmlDesc("htmlDesc")
          .setHtmlNote("htmlNote")
          .build())
        .build());

    var rulesApi = new RulesApi(mockServer.serverApiHelper("orgKey"));

    var rule = rulesApi.getRule("java:S1234").get();

    assertThat(rule).extracting("name", "severity", "type", "language", "htmlDesc", "htmlNote")
      .contains("name", IssueSeverity.MAJOR, RuleType.VULNERABILITY, Language.PYTHON, "htmlDesc", "htmlNote");
  }

  @Test
  void should_get_active_rules_of_a_given_quality_profile() {
    mockServer.addProtobufResponse(
      "/api/rules/search.protobuf?qprofile=QPKEY%2B&organization=orgKey&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY,SECURITY_HOTSPOT&s=key&ps=500&p=1",
      Rules.SearchResponse.newBuilder()
        .setTotal(2)
        .setPs(1)
        .addRules(Rules.Rule.newBuilder().setKey("repo:key_with_template").setTemplateKey("template").build())
        .addRules(Rules.Rule.newBuilder().setKey("repo:key").build())
        .setActives(
          Rules.Actives.newBuilder()
            .putActives("repo:key_with_template", Rules.ActiveList.newBuilder().addActiveList(
              Rules.Active.newBuilder()
                .setSeverity("MAJOR")
                .addParams(Rules.Active.Param.newBuilder().setKey("paramKey").setValue("paramValue").build())
                .build())
              .build())
            .putActives("repo:key", Rules.ActiveList.newBuilder().addActiveList(
              Rules.Active.newBuilder()
                .setSeverity("MINOR")
                .build())
              .build())
            .build())
        .build());

    var rulesApi = new RulesApi(mockServer.serverApiHelper("orgKey"));

    var activeRules = rulesApi.getAllActiveRules("QPKEY+", progress);

    assertThat(activeRules)
      .extracting("ruleKey", "severity", "templateKey", "params")
      .containsOnly(tuple("repo:key", IssueSeverity.MINOR, "", Map.of()),
        tuple("repo:key_with_template", IssueSeverity.MAJOR, "template", Map.of("paramKey", "paramValue")));
  }

}
