/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.telemetry.payload;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiSuggestionSource;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.FixSuggestionStatus;
import org.sonarsource.sonarlint.core.telemetry.TelemetryHelpAndFeedbackCounter;
import org.sonarsource.sonarlint.core.telemetry.payload.cayc.CleanAsYouCodePayload;
import org.sonarsource.sonarlint.core.telemetry.payload.cayc.NewCodeFocusPayload;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryPayloadTests {

  @Test
  void testGenerationJson() {
    var installTime = OffsetDateTime.of(2017, 11, 10, 12, 1, 14, 984_123_123, ZoneOffset.ofHours(2));
    var systemTime = installTime.plusMinutes(1);
    var perf = new TelemetryAnalyzerPerformancePayload[1];
    Map<String, BigDecimal> distrib = new LinkedHashMap<>();
    distrib.put("0-300", BigDecimal.valueOf(9.90));
    distrib.put("1000-2000", BigDecimal.valueOf(90.10));
    perf[0] = new TelemetryAnalyzerPerformancePayload("java", distrib);
    Map<String, TelemetryNotificationsCounterPayload> counters = new HashMap<>();
    counters.put("QUALITY_GATE", new TelemetryNotificationsCounterPayload(5, 3));
    counters.put("NEW_ISSUES", new TelemetryNotificationsCounterPayload(10, 1));
    var notifPayload = new TelemetryNotificationsPayload(true, counters);
    var showHotspotPayload = new ShowHotspotPayload(4);
    var showIssuePayload = new ShowIssuePayload(3);
    var hotspotPayload = new HotspotPayload(5, 3);
    var taintVulnerabilitiesPayload = new TaintVulnerabilitiesPayload(6, 7);
    var issuePayload = new IssuePayload(Set.of("java:S123"), 1);
    var rulesPayload = new TelemetryRulesPayload(Arrays.asList("enabledRuleKey1", "enabledRuleKey2"), Arrays.asList("disabledRuleKey1", "disabledRuleKey2"),
      Arrays.asList("reportedRuleKey1", "reportedRuleKey2"), Arrays.asList("quickFixedRuleKey1", "quickFixedRuleKey2"));
    Map<String, TelemetryHelpAndFeedbackCounter> helpAndFeedbackCounter = new HashMap<>();
    helpAndFeedbackCounter.put("faq", new TelemetryHelpAndFeedbackCounter(4));
    helpAndFeedbackCounter.put("docs", new TelemetryHelpAndFeedbackCounter(5));
    var helpAndFeedbackPayload = new TelemetryHelpAndFeedbackPayload(helpAndFeedbackCounter);
    var aiFixSuggestionsPayload = getTelemetryFixSuggestionPayloads();
    Map<String, Object> additionalProps = new LinkedHashMap<>();
    additionalProps.put("aString", "stringValue");
    additionalProps.put("aBool", false);
    additionalProps.put("aNumber", 1.5);
    var sharedConnectedModePayload = new ShareConnectedModePayload(3, 2, 1, 4);
    Map<String, Object> additionalPropsSub = new LinkedHashMap<>();
    additionalPropsSub.put("aSubNumber", 2);
    additionalProps.put("sub", additionalPropsSub);
    var cleanAsYouCodePayload = new CleanAsYouCodePayload(new NewCodeFocusPayload(true, 2));
    var m = new TelemetryPayload(4, 15, "SLI", "2.4", "Pycharm 3.2", "platform", "architecture",
      true, true, systemTime, installTime, "Windows 10", "1.8.0", "10.5.2", perf,
      notifPayload, showHotspotPayload, showIssuePayload, taintVulnerabilitiesPayload, rulesPayload, hotspotPayload, issuePayload, helpAndFeedbackPayload,
      aiFixSuggestionsPayload, 1, cleanAsYouCodePayload, sharedConnectedModePayload, additionalProps);
    var s = m.toJson();

    assertThat(s).isEqualTo("{\"days_since_installation\":4,"
      + "\"days_of_use\":15,"
      + "\"sonarlint_version\":\"2.4\","
      + "\"sonarlint_product\":\"SLI\","
      + "\"ide_version\":\"Pycharm 3.2\","
      + "\"platform\":\"platform\","
      + "\"architecture\":\"architecture\","
      + "\"connected_mode_used\":true,"
      + "\"connected_mode_sonarcloud\":true,"
      + "\"system_time\":\"2017-11-10T12:02:14.984+02:00\","
      + "\"install_time\":\"2017-11-10T12:01:14.984+02:00\","
      + "\"os\":\"Windows 10\","
      + "\"jre\":\"1.8.0\","
      + "\"nodejs\":\"10.5.2\","
      + "\"analyses\":[{\"language\":\"java\",\"rate_per_duration\":{\"0-300\":9.9,\"1000-2000\":90.1}}],"
      + "\"server_notifications\":{\"disabled\":true,\"count_by_type\":{\"NEW_ISSUES\":{\"received\":10,\"clicked\":1},\"QUALITY_GATE\":{\"received\":5,\"clicked\":3}}},"
      + "\"show_hotspot\":{\"requests_count\":4},"
      + "\"show_issue\":{\"requests_count\":3},"
      + "\"taint_vulnerabilities\":{\"investigated_locally_count\":6,\"investigated_remotely_count\":7},"
      + "\"rules\":{\"non_default_enabled\":[\"enabledRuleKey1\",\"enabledRuleKey2\"],\"default_disabled\":[\"disabledRuleKey1\",\"disabledRuleKey2\"],\"raised_issues\":[\"reportedRuleKey1\",\"reportedRuleKey2\"],\"quick_fix_applied\":[\"quickFixedRuleKey1\",\"quickFixedRuleKey2\"]},"
      + "\"hotspot\":{\"open_in_browser_count\":5,\"status_changed_count\":3},"
      + "\"issue\":{\"status_changed_rule_keys\":[\"java:S123\"],\"status_changed_count\":1},"
      + "\"help_and_feedback\":{\"count_by_link\":{\"docs\":5,\"faq\":4}},"
      + "\"ai_fix_suggestions\":[{\"suggestion_id\":\"suggestionId1\",\"count_snippets\":1,\"ai_fix_suggestion_provider\":\"SONARCLOUD\",\"snippets\":[{\"status\":\"ACCEPTED\",\"snippet_index\":0},{\"status\":\"DECLINED\",\"snippet_index\":1}],\"was_ai_fix_suggestion_generated_from_ide\":true},{\"suggestion_id\":\"suggestionId2\",\"count_snippets\":2,\"ai_fix_suggestion_provider\":\"SONARCLOUD\",\"snippets\":[{\"status\":\"ACCEPTED\",\"snippet_index\":null}],\"was_ai_fix_suggestion_generated_from_ide\":true},{\"suggestion_id\":\"suggestionId3\",\"count_snippets\":3,\"ai_fix_suggestion_provider\":\"SONARCLOUD\",\"snippets\":[{\"status\":null,\"snippet_index\":null}],\"was_ai_fix_suggestion_generated_from_ide\":false}],"
      + "\"count_issues_with_possible_ai_fix_from_ide\":1,"
      + "\"cayc\":{\"new_code_focus\":{\"enabled\":true,\"changes\":2}},"
      + "\"shared_connected_mode\":{\"manual_bindings_count\":3,\"imported_bindings_count\":2,\"auto_bindings_count\":1,\"exported_connected_mode_count\":4},"
      + "\"aString\":\"stringValue\","
      + "\"aBool\":false,"
      + "\"aNumber\":1.5,"
      + "\"sub\":{\"aSubNumber\":2}}");

    assertThat(m.connectedMode()).isTrue();
    assertThat(m.analyses()).hasSize(1);
    assertThat(m.connectedModeSonarcloud()).isTrue();
    assertThat(m.systemTime()).isEqualTo(systemTime);
    assertThat(m.notifications().disabled()).isTrue();
    assertThat(m.notifications().counters()).containsOnlyKeys("QUALITY_GATE", "NEW_ISSUES");
    assertThat(m.helpAndFeedbackPayload().getCounters()).containsOnlyKeys("docs", "faq");
    assertThat(m.getAiFixSuggestionsPayload()).hasSize(3);
    assertThat(m.getCountIssuesWithPossibleAiFixFromIde()).isEqualTo(1);
    assertThat(m.cleanAsYouCodePayload().newCodeFocusPayload())
      .extracting(NewCodeFocusPayload::enabled, NewCodeFocusPayload::changes)
      .containsExactly(true, 2);
    assertThat(m.issuePayload().statusChangedRuleKeys()).isEqualTo(Set.of("java:S123"));
    assertThat(m.issuePayload().statusChangedCount()).isEqualTo(1);
    assertThat(m.additionalAttributes()).containsExactlyEntriesOf(additionalProps);
    assertThat(m.getShowHotspotPayload().requestsCount()).isEqualTo(4);
    assertThat(m.getShowIssuePayload().requestsCount()).isEqualTo(3);
    assertThat(m.getHotspotPayload().openInBrowserCount()).isEqualTo(5);
    assertThat(m.getHotspotPayload().statusChangedCount()).isEqualTo(3);
    assertThat(m.getTaintVulnerabilitiesPayload().investigatedLocallyCount()).isEqualTo(6);
    assertThat(m.getTaintVulnerabilitiesPayload().investigatedRemotelyCount()).isEqualTo(7);
    assertRulesPayload(m);
    assertShareConnectedModePayload(m);
    assertMetadata(m);
  }

  private static void assertMetadata(TelemetryPayload m) {
    assertThat(m.getIdeVersion()).isEqualTo("Pycharm 3.2");
    assertThat(m.getPlatform()).isEqualTo("platform");
    assertThat(m.getArchitecture()).isEqualTo("architecture");
    assertThat(m.getInstallTime()).hasToString("2017-11-10T12:01:14.984123123+02:00");
    assertThat(m.os()).isEqualTo("Windows 10");
    assertThat(m.jre()).isEqualTo("1.8.0");
    assertThat(m.nodejs()).isEqualTo("10.5.2");
    assertThat(m.daysOfUse()).isEqualTo(15);
    assertThat(m.daysSinceInstallation()).isEqualTo(4);
    assertThat(m.product()).isEqualTo("SLI");
    assertThat(m.version()).isEqualTo("2.4");
  }

  private static void assertShareConnectedModePayload(TelemetryPayload m) {
    assertThat(m.getShareConnectedModePayload().manualAddedBindingsCount()).isEqualTo(3);
    assertThat(m.getShareConnectedModePayload().importedAddedBindingsCount()).isEqualTo(2);
    assertThat(m.getShareConnectedModePayload().autoAddedBindingsCount()).isEqualTo(1);
    assertThat(m.getShareConnectedModePayload().exportedConnectedModeCount()).isEqualTo(4);
  }

  private static void assertRulesPayload(TelemetryPayload m) {
    assertThat(m.getTelemetryRulesPayload().defaultDisabled()).containsExactly("disabledRuleKey1", "disabledRuleKey2");
    assertThat(m.getTelemetryRulesPayload().nonDefaultEnabled()).containsExactly("enabledRuleKey1", "enabledRuleKey2");
    assertThat(m.getTelemetryRulesPayload().raisedIssues()).containsExactly("reportedRuleKey1", "reportedRuleKey2");
    assertThat(m.getTelemetryRulesPayload().quickFixesApplied()).containsExactly("quickFixedRuleKey1", "quickFixedRuleKey2");
  }

  private static TelemetryFixSuggestionPayload[] getTelemetryFixSuggestionPayloads() {
    var fixSuggestionPayload1 = new TelemetryFixSuggestionPayload("suggestionId1", 1,
      AiSuggestionSource.SONARCLOUD,
      List.of(new TelemetryFixSuggestionResolvedPayload(FixSuggestionStatus.ACCEPTED, 0),
        new TelemetryFixSuggestionResolvedPayload(FixSuggestionStatus.DECLINED, 1)),
      true);
    var fixSuggestionPayload2 = new TelemetryFixSuggestionPayload("suggestionId2", 2,
      AiSuggestionSource.SONARCLOUD,
      List.of(new TelemetryFixSuggestionResolvedPayload(FixSuggestionStatus.ACCEPTED, null)),
      true);
    var fixSuggestionPayload3 = new TelemetryFixSuggestionPayload("suggestionId3", 3,
      AiSuggestionSource.SONARCLOUD,
      List.of(new TelemetryFixSuggestionResolvedPayload(null, null)),
      false);
    return new TelemetryFixSuggestionPayload[]{fixSuggestionPayload1, fixSuggestionPayload2, fixSuggestionPayload3};
  }

  @Test
  void testMergeEmptyJson() {
    Map<String, Object> source = new LinkedHashMap<>();
    Map<String, Object> target = new LinkedHashMap<>();
    var gson = new Gson();
    var type = new TypeToken<Map<String, Object>>() {
    }.getType();
    var jsonSource = gson.toJsonTree(source, type).getAsJsonObject();
    var jsonTarget = gson.toJsonTree(target, type).getAsJsonObject();

    var merged = gson.toJson(TelemetryPayload.mergeObjects(jsonSource, jsonTarget));
    assertThat(merged).isEqualTo("{}");
  }

  @Test
  void testMergeEmptyTargetJson() {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("keyInSource", "valueInSource");
    Map<String, Object> target = new LinkedHashMap<>();
    var gson = new Gson();
    var type = new TypeToken<Map<String, Object>>() {
    }.getType();
    var jsonSource = gson.toJsonTree(source, type).getAsJsonObject();
    var jsonTarget = gson.toJsonTree(target, type).getAsJsonObject();

    var merged = gson.toJson(TelemetryPayload.mergeObjects(jsonSource, jsonTarget));
    assertThat(merged).isEqualTo("{\"keyInSource\":\"valueInSource\"}");
  }

  @Test
  void testMergeEmptySourceJson() {
    Map<String, Object> source = new LinkedHashMap<>();
    Map<String, Object> target = new LinkedHashMap<>();
    target.put("keyInTarget", "valueInTarget");
    var gson = new Gson();
    var type = new TypeToken<Map<String, Object>>() {
    }.getType();
    var jsonSource = gson.toJsonTree(source, type).getAsJsonObject();
    var jsonTarget = gson.toJsonTree(target, type).getAsJsonObject();

    var merged = gson.toJson(TelemetryPayload.mergeObjects(jsonSource, jsonTarget));
    assertThat(merged).isEqualTo("{\"keyInTarget\":\"valueInTarget\"}");
  }

  @Test
  void testMergeJson() {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("keyInSource", "valueInSource");
    Map<String, Object> target = new LinkedHashMap<>();
    target.put("keyInTarget", "valueInTarget");
    var gson = new Gson();
    var type = new TypeToken<Map<String, Object>>() {
    }.getType();
    var jsonSource = gson.toJsonTree(source, type).getAsJsonObject();
    var jsonTarget = gson.toJsonTree(target, type).getAsJsonObject();

    var merged = gson.toJson(TelemetryPayload.mergeObjects(jsonSource, jsonTarget));
    assertThat(merged).isEqualTo("{\"keyInTarget\":\"valueInTarget\",\"keyInSource\":\"valueInSource\"}");
  }

  @Test
  void testDeepMergeJson() {
    Map<String, Object> source = new LinkedHashMap<>();
    Map<String, Object> sourceSub = new LinkedHashMap<>();
    source.put("key", sourceSub);
    sourceSub.put("sub2", "sub2Value");
    Map<String, Object> target = new LinkedHashMap<>();
    Map<String, Object> targetSub = new LinkedHashMap<>();
    target.put("key", targetSub);
    targetSub.put("sub1", "sub1Value");
    var gson = new Gson();
    var type = new TypeToken<Map<String, Object>>() {
    }.getType();
    var jsonSource = gson.toJsonTree(source, type).getAsJsonObject();
    var jsonTarget = gson.toJsonTree(target, type).getAsJsonObject();

    var merged = gson.toJson(TelemetryPayload.mergeObjects(jsonSource, jsonTarget));
    assertThat(merged).isEqualTo("{\"key\":{\"sub1\":\"sub1Value\",\"sub2\":\"sub2Value\"}}");
  }

  @Test
  void testMergeJsonDontOverrideExistingKey() {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("key", "valueInSource");
    Map<String, Object> target = new LinkedHashMap<>();
    target.put("key", "valueInTarget");
    var gson = new Gson();
    var type = new TypeToken<Map<String, Object>>() {
    }.getType();
    var jsonSource = gson.toJsonTree(source, type).getAsJsonObject();
    var jsonTarget = gson.toJsonTree(target, type).getAsJsonObject();

    var merged = gson.toJson(TelemetryPayload.mergeObjects(jsonSource, jsonTarget));
    assertThat(merged).isEqualTo("{\"key\":\"valueInTarget\"}");
  }

}
