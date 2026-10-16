/*
 * SonarLint Core - Server API
 * Copyright (C) SonarSource Sàrl
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

import com.google.protobuf.UnknownFieldSet;
import io.sentry.Sentry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.MockWebServerExtensionWithProtobuf;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.exception.InvalidServerItemException;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RulesApiTests {
  private MockedStatic<Sentry> sentry;

  @BeforeEach
  void mockSentry() {
    var logger = Sentry.logger();
    sentry = mockStatic(Sentry.class);
    sentry.when(Sentry::logger).thenReturn(logger);
  }

  @AfterEach
  void closeSentry() {
    sentry.close();
  }

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();

  private static final String SEARCH_URL = "/api/rules/search.protobuf?qprofile=profile&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY,SECURITY_HOTSPOT&s=key&ps=500&p=";

  @ParameterizedTest
  @ValueSource(strings = {"empty", "severity", "duplicate", "missingImpact", "unknownQuality", "unknownSeverity", "unsupportedQuality", "unsupportedSeverity"})
  void should_skip_malformed_active_rules_and_report_once(String invalidKind) {
    var invalid = active("MAJOR");
    switch (invalidKind) {
      case "severity" -> invalid.setSeverity("NEW_SEVERITY");
      case "duplicate" -> invalid.addParams(Rules.Active.Param.newBuilder().setKey("key").setValue("a"))
        .addParams(Rules.Active.Param.newBuilder().setKey("key").setValue("b"));
      case "missingImpact" -> invalid.setImpacts(Rules.Active.Impacts.newBuilder().addImpacts(Common.Impact.newBuilder().buildPartial()).buildPartial());
      case "unknownQuality", "unknownSeverity" -> {
        var field = "unknownQuality".equals(invalidKind) ? Common.Impact.SOFTWAREQUALITY_FIELD_NUMBER : Common.Impact.SEVERITY_FIELD_NUMBER;
        var impact = Common.Impact.newBuilder().setSoftwareQuality(Common.SoftwareQuality.SECURITY).setSeverity(Common.ImpactSeverity.HIGH);
        if (field == Common.Impact.SOFTWAREQUALITY_FIELD_NUMBER) { impact.clearSoftwareQuality(); } else { impact.clearSeverity(); }
        impact.setUnknownFields(unknownEnum(field));
        invalid.setImpacts(Rules.Active.Impacts.newBuilder().addImpacts(impact.buildPartial()).buildPartial());
      }
      case "unsupportedQuality" -> invalid.setImpacts(Rules.Active.Impacts.newBuilder().addImpacts(Common.Impact.newBuilder()
        .setSoftwareQuality(Common.SoftwareQuality.UNKNOWN_IMPACT_QUALITY).setSeverity(Common.ImpactSeverity.HIGH)));
      case "unsupportedSeverity" -> invalid.setImpacts(Rules.Active.Impacts.newBuilder().addImpacts(Common.Impact.newBuilder()
        .setSoftwareQuality(Common.SoftwareQuality.SECURITY).setSeverity(Common.ImpactSeverity.UNKNOWN_IMPACT_SEVERITY)));
      default -> { }
    }
    var invalidList = Rules.ActiveList.newBuilder();
    if (!"empty".equals(invalidKind)) { invalidList.addActiveList(invalid.buildPartial()); }
    mockServer.addProtobufResponse(SEARCH_URL + "1", Rules.SearchResponse.newBuilder().setTotal(3).setActives(Rules.Actives.newBuilder()
      .putActives("first", Rules.ActiveList.newBuilder().addActiveList(active("MINOR")).build())
      .putActives("invalid", invalidList.buildPartial())
      .putActives("last", Rules.ActiveList.newBuilder().addActiveList(active("BLOCKER")).build()).buildPartial()).buildPartial());

    var result = new RulesApi(mockServer.serverApiHelper()).getAllActiveRules("profile", new SonarLintCancelMonitor());

    assertThat(result).extracting(ServerActiveRule::getRuleKey).containsExactlyInAnyOrder("first", "last");
    var captor = ArgumentCaptor.forClass(Throwable.class);
    sentry.verify(() -> Sentry.captureException(captor.capture()));
    assertThat(captor.getValue()).isInstanceOf(InvalidServerItemException.class);
    if (List.of("severity", "unsupportedQuality", "unsupportedSeverity").contains(invalidKind)) {
      assertThat(captor.getValue()).hasCauseInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void should_keep_valid_duplicate_when_later_active_is_malformed() {
    mockServer.addProtobufResponse(SEARCH_URL + "1", Rules.SearchResponse.newBuilder().setTotal(501)
      .addRules(Rules.Rule.newBuilder().setKey("same").setTemplateKey("old"))
      .addRules(Rules.Rule.newBuilder().setKey("same").setTemplateKey("latest"))
      .setActives(Rules.Actives.newBuilder().putActives("same", Rules.ActiveList.newBuilder().addActiveList(active("MINOR")).build())).build());
    mockServer.addProtobufResponse(SEARCH_URL + "2", Rules.SearchResponse.newBuilder().setTotal(501)
      .setActives(Rules.Actives.newBuilder().putActives("same", Rules.ActiveList.getDefaultInstance())).build());

    var result = new RulesApi(mockServer.serverApiHelper()).getAllActiveRules("profile", new SonarLintCancelMonitor());

    assertThat(result).extracting("ruleKey", "severity", "templateKey").containsExactly(tuple("same", IssueSeverity.MINOR, "latest"));
    sentry.verify(() -> Sentry.captureException(any(Throwable.class)));
  }

  @Test
  void should_continue_after_entirely_rejected_active_page() {
    mockServer.addProtobufResponse(SEARCH_URL + "1", Rules.SearchResponse.newBuilder().setTotal(501)
      .setActives(Rules.Actives.newBuilder().putActives("invalid", Rules.ActiveList.getDefaultInstance())).build());
    mockServer.addProtobufResponse(SEARCH_URL + "2", Rules.SearchResponse.newBuilder().setTotal(501)
      .setActives(Rules.Actives.newBuilder().putActives("valid", Rules.ActiveList.newBuilder().addActiveList(active("MAJOR")).build())).build());

    assertThat(new RulesApi(mockServer.serverApiHelper()).getAllActiveRules("profile", new SonarLintCancelMonitor()))
      .extracting(ServerActiveRule::getRuleKey).containsExactly("valid");
    sentry.verify(() -> Sentry.captureException(any(Throwable.class)));
  }

  @Test
  void should_accept_severity_aliases_and_ignore_unused_partial_metadata() {
    var impacts = Rules.Active.Impacts.newBuilder();
    for (var severity : List.of(Common.ImpactSeverity.ImpactSeverity_INFO, Common.ImpactSeverity.ImpactSeverity_BLOCKER)) {
      impacts.addImpacts(Common.Impact.newBuilder().setSoftwareQuality(Common.SoftwareQuality.SECURITY).setSeverity(severity));
    }
    mockServer.addProtobufResponse(SEARCH_URL + "1", Rules.SearchResponse.newBuilder().setTotal(1)
      .addRules(Rules.Rule.newBuilder().setKey("valid").setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder()
        .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder().buildPartial()).buildPartial()).buildPartial())
      .setActives(Rules.Actives.newBuilder().putActives("valid", Rules.ActiveList.newBuilder()
        .addActiveList(active("MAJOR").setImpacts(impacts).setUnknownFields(unknownEnum(1234)))
        .addActiveList(active("unused invalid severity")).build())).buildPartial());

    var result = new RulesApi(mockServer.serverApiHelper()).getAllActiveRules("profile", new SonarLintCancelMonitor());
    assertThat(result).hasSize(1);
    assertThat(result.iterator().next().getOverriddenImpacts()).extracting("severity").containsExactly("INFO", "BLOCKER");
    sentry.verify(() -> Sentry.captureException(any(Throwable.class)), never());
  }

  @ParameterizedTest
  @ValueSource(strings = {"direct", "wrapped", "monitor", "successMonitor"})
  void should_propagate_rule_cancellation_without_reporting(String mode) {
    var helper = mock(ServerApiHelper.class);
    var monitor = new SonarLintCancelMonitor();
    when(helper.getOrganizationKey()).thenReturn(Optional.empty());
    var cancellation = new CancellationException();
    if ("monitor".equals(mode)) {
      when(helper.get(anyString(), any())).thenAnswer(invocation -> { monitor.cancel(); throw new IllegalStateException(); });
    } else if ("successMonitor".equals(mode)) {
      var response = mock(HttpClient.Response.class);
      when(response.bodyAsStream()).thenAnswer(invocation -> {
        monitor.cancel();
        return new java.io.ByteArrayInputStream(Rules.ShowResponse.newBuilder().setRule(Rules.Rule.newBuilder()
          .setLang("java").setSeverity("MAJOR").setType(Common.RuleType.BUG)).build().toByteArray());
      });
      when(helper.get(anyString(), any())).thenReturn(response);
    } else {
      when(helper.get(anyString(), any())).thenThrow("direct".equals(mode) ? cancellation : new CompletionException(cancellation));
    }
    assertThatThrownBy(() -> new RulesApi(helper).getRule("key", monitor)).isInstanceOf(CancellationException.class);
    sentry.verify(() -> Sentry.captureException(any(Throwable.class)), never());
  }

  @Test
  void should_not_fetch_rule_when_monitor_already_canceled() {
    var helper = mock(ServerApiHelper.class);
    var monitor = new SonarLintCancelMonitor();
    monitor.cancel();
    assertThatThrownBy(() -> new RulesApi(helper).getRule("key", monitor)).isInstanceOf(CancellationException.class);
    verify(helper, never()).get(anyString(), any());
    sentry.verify(() -> Sentry.captureException(any(Throwable.class)), never());
  }

  @ParameterizedTest
  @ValueSource(strings = {"omitted", "recognizedUnknown", "numericUnknown"})
  void should_preserve_clean_code_attribute_defaults_and_reject_unknown_numbers(String mode) {
    var rule = Rules.Rule.newBuilder().setLang("java").setSeverity("MAJOR").setType(Common.RuleType.BUG);
    if ("recognizedUnknown".equals(mode)) { rule.setCleanCodeAttribute(Common.CleanCodeAttribute.UNKNOWN_ATTRIBUTE); }
    if ("numericUnknown".equals(mode)) { rule.setUnknownFields(unknownEnum(Rules.Rule.CLEANCODEATTRIBUTE_FIELD_NUMBER)); }
    mockServer.addProtobufResponse("/api/rules/show.protobuf?key=key", Rules.ShowResponse.newBuilder().setRule(rule).build());

    var result = new RulesApi(mockServer.serverApiHelper()).getRule("key", new SonarLintCancelMonitor());
    if ("numericUnknown".equals(mode)) {
      assertThat(result).isEmpty();
      sentry.verify(() -> Sentry.captureException(any(Throwable.class)));
    } else {
      assertThat(result).hasValueSatisfying(value -> assertThat(value.getCleanCodeAttribute()).isNull());
      sentry.verify(() -> Sentry.captureException(any(Throwable.class)), never());
    }
  }

  @Test
  void should_propagate_malformed_active_wire_data() {
    mockServer.addStringResponse(SEARCH_URL + "1", "trash");
    assertThatThrownBy(() -> new RulesApi(mockServer.serverApiHelper()).getAllActiveRules("profile", new SonarLintCancelMonitor()))
      .isInstanceOf(IllegalStateException.class);
    sentry.verify(() -> Sentry.captureException(any(Throwable.class)), never());
  }

  private static Rules.Active.Builder active(String severity) {
    return Rules.Active.newBuilder().setSeverity(severity);
  }

  private static UnknownFieldSet unknownEnum(int field) {
    return UnknownFieldSet.newBuilder().addField(field, UnknownFieldSet.Field.newBuilder().addVarint(999).build()).build();
  }

  @Test
  void logErrorParsingRuleDescription() {
    mockServer.addStringResponse("/api/rules/show.protobuf?key=java:S1234", "trash");

    var rulesApi = new RulesApi(mockServer.serverApiHelper());

    assertThat(rulesApi.getRule("java:S1234", new SonarLintCancelMonitor())).isEmpty();

    assertThat(logTester.logs()).contains("Error when fetching rule 'java:S1234'");
    sentry.verify(() -> Sentry.captureException(any(Throwable.class)));
  }

  @Test
  void should_get_rule() {
    mockServer.addProtobufResponse("/api/rules/show.protobuf?key=java:S1234",
      Rules.ShowResponse.newBuilder().setRule(
          Rules.Rule.newBuilder()
            .setName("name")
            .setSeverity("MINOR")
            .setType(Common.RuleType.VULNERABILITY)
            .setLang(SonarLanguage.PYTHON.getSonarLanguageKey())
            .setHtmlNote("htmlNote")
            .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder()
              .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
                .setKey("default")
                .setContent("desc")
                .build())
              .build())
            .setCleanCodeAttribute(Common.CleanCodeAttribute.COMPLETE)
            .setImpacts(Rules.Rule.Impacts.newBuilder().addImpacts(Common.Impact.newBuilder().setSeverity(Common.ImpactSeverity.HIGH).setSoftwareQuality(Common.SoftwareQuality.MAINTAINABILITY).build()).build())
            .build())
        .build());

    var rulesApi = new RulesApi(mockServer.serverApiHelper());

    var rule = rulesApi.getRule("java:S1234", new SonarLintCancelMonitor()).get();

    assertThat(rule).extracting("name", "severity", "type", "language", "htmlNote", "cleanCodeAttribute", "impacts")
      .contains("name", IssueSeverity.MINOR, RuleType.VULNERABILITY, SonarLanguage.PYTHON, "htmlNote", CleanCodeAttribute.COMPLETE, Map.of(SoftwareQuality.MAINTAINABILITY, ImpactSeverity.HIGH));
    assertThat(rule.getDescriptionSections().get(0).getHtmlContent()).isEqualTo("desc");
  }

  @Test
  void should_get_rule_with_description_sections() {
    mockServer.addProtobufResponse("/api/rules/show.protobuf?key=java:S1234",
      Rules.ShowResponse.newBuilder().setRule(
          Rules.Rule.newBuilder()
            .setName("name")
            .setSeverity("MINOR")
            .setType(Common.RuleType.VULNERABILITY)
            .setLang(SonarLanguage.PYTHON.getSonarLanguageKey())
            .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder()
              .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder().setKey("sectionKey").setContent("htmlContent").build())
              .addDescriptionSections(
                Rules.Rule.DescriptionSection.newBuilder().setKey("sectionKey2").setContent("htmlContent2").setContext(Rules.Rule.DescriptionSection.Context.newBuilder()
                  .setKey("contextKey").setDisplayName("displayName").build()).build())
              .build())
            .setHtmlNote("htmlNote")
            .setCleanCodeAttribute(Common.CleanCodeAttribute.CONVENTIONAL)
            .setImpacts(Rules.Rule.Impacts.newBuilder().addImpacts(Common.Impact.newBuilder().setSeverity(Common.ImpactSeverity.LOW).setSoftwareQuality(Common.SoftwareQuality.RELIABILITY).build()).build())
            .build())
        .build());

    var rulesApi = new RulesApi(mockServer.serverApiHelper());

    var rule = rulesApi.getRule("java:S1234", new SonarLintCancelMonitor()).get();

    assertThat(rule).extracting("name", "severity", "type", "language","htmlNote", "cleanCodeAttribute", "impacts")
      .contains("name", IssueSeverity.MINOR, RuleType.VULNERABILITY, SonarLanguage.PYTHON, "htmlNote", CleanCodeAttribute.CONVENTIONAL, Map.of(SoftwareQuality.RELIABILITY, ImpactSeverity.LOW));

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
  void should_get_rule_from_organization() {
    mockServer.addProtobufResponse("/api/rules/show.protobuf?key=java:S1234&organization=orgKey",
      Rules.ShowResponse.newBuilder().setRule(
          Rules.Rule.newBuilder()
            .setName("name")
            .setSeverity("MAJOR")
            .setType(Common.RuleType.VULNERABILITY)
            .setLang(SonarLanguage.PYTHON.getSonarLanguageKey())
            .setHtmlNote("htmlNote")
            .setDescriptionSections(Rules.Rule.DescriptionSections.newBuilder()
              .addDescriptionSections(Rules.Rule.DescriptionSection.newBuilder()
                .setKey("default")
                .setContent("desc")
                  .build())
              .build())
            .build())
        .build());

    var rulesApi = new RulesApi(mockServer.serverApiHelper("orgKey"));

    var rule = rulesApi.getRule("java:S1234", new SonarLintCancelMonitor()).get();

    assertThat(rule).extracting("name", "severity", "type", "language", "htmlNote")
      .contains("name", IssueSeverity.MAJOR, RuleType.VULNERABILITY, SonarLanguage.PYTHON, "htmlNote");
    assertThat(rule.getDescriptionSections().get(0).getHtmlContent()).isEqualTo("desc");
  }

  @Test
  void should_get_active_rules_of_a_given_quality_profile() {
    mockServer.addProtobufResponse(
      "/api/rules/search.protobuf?qprofile=QPKEY%2B&organization=orgKey&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY,SECURITY_HOTSPOT&s=key&ps=500&p=1",
      Rules.SearchResponse.newBuilder()
        .setPaging(Common.Paging.newBuilder().setTotal(2).build())
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

    var activeRules = rulesApi.getAllActiveRules("QPKEY+", new SonarLintCancelMonitor());

    assertThat(activeRules)
      .extracting("ruleKey", "severity", "templateKey", "params")
      .containsOnly(tuple("repo:key", IssueSeverity.MINOR, "", Map.of()),
        tuple("repo:key_with_template", IssueSeverity.MAJOR, "template", Map.of("paramKey", "paramValue")));
  }

  @Test
  void should_fallback_on_deprecated_pagination_for_sonarqube_older_than_9_8() {
    mockServer.addProtobufResponse(
      "/api/rules/search.protobuf?qprofile=QPKEY%2B&organization=orgKey&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY,SECURITY_HOTSPOT&s=key&ps=500&p=1",
      Rules.SearchResponse.newBuilder()
        .setTotal(501)
        .addRules(Rules.Rule.newBuilder().setKey("repo:key1").build())
        .setActives(
          Rules.Actives.newBuilder()
            .putActives("repo:key1", Rules.ActiveList.newBuilder().addActiveList(
                Rules.Active.newBuilder()
                  .setSeverity("MINOR")
                  .build())
              .build())
            .build())
        .build());
    mockServer.addProtobufResponse(
      "/api/rules/search.protobuf?qprofile=QPKEY%2B&organization=orgKey&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY,SECURITY_HOTSPOT&s=key&ps=500&p=2",
      Rules.SearchResponse.newBuilder()
        .setTotal(501)
        .addRules(Rules.Rule.newBuilder().setKey("repo:key2").build())
        .setActives(
          Rules.Actives.newBuilder()
            .putActives("repo:key2", Rules.ActiveList.newBuilder().addActiveList(
                Rules.Active.newBuilder()
                  .setSeverity("MAJOR")
                  .build())
              .build())
            .build())
        .build());

    var rulesApi = new RulesApi(mockServer.serverApiHelper("orgKey"));

    var activeRules = rulesApi.getAllActiveRules("QPKEY+", new SonarLintCancelMonitor());

    assertThat(activeRules).extracting(ServerActiveRule::getRuleKey).containsExactlyInAnyOrder("repo:key1", "repo:key2");
  }

}
