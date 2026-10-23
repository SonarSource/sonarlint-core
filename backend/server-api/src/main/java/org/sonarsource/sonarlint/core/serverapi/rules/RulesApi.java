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

import com.google.common.base.Enums;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.sentry.Sentry;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.exception.InvalidServerItemException;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Rules;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.common.ImpactPayload;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

public class RulesApi {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static final Map<SonarLanguage, String> TAINT_REPOS_BY_LANGUAGE = Map.of(
    SonarLanguage.GO, "gosecurity",
    SonarLanguage.JAVA, "javasecurity",
    SonarLanguage.JS, "jssecurity",
    SonarLanguage.KOTLIN, "kotlinsecurity",
    SonarLanguage.PHP, "phpsecurity",
    SonarLanguage.PYTHON, "pythonsecurity",
    SonarLanguage.CS, "roslyn.sonaranalyzer.security.cs",
    SonarLanguage.TS, "tssecurity",
    SonarLanguage.VBNET, "vbnetsecurity");

  public static final Set<String> TAINT_REPOS = Set.copyOf(TAINT_REPOS_BY_LANGUAGE.values());

  private static final String ORGANIZATION_PARAM = "&organization=";

  public static final String RULE_SHOW_URL = "/api/rules/show.protobuf?key=";

  private final ServerApiHelper serverApiHelper;

  public RulesApi(ServerApiHelper serverApiHelper) {
    this.serverApiHelper = serverApiHelper;
  }

  public Optional<ServerRule> getRule(String ruleKey, SonarLintCancelMonitor cancelMonitor) {
    cancelMonitor.checkCanceled();
    var builder = new StringBuilder(RULE_SHOW_URL + ruleKey);
    serverApiHelper.getOrganizationKey().ifPresent(org -> builder.append(ORGANIZATION_PARAM).append(UrlUtils.urlEncode(org)));
    try (var response = serverApiHelper.get(builder.toString(), cancelMonitor)) {
      var rule = Rules.ShowResponse.parseFrom(response.bodyAsStream()).getRule();
      rejectUnknownEnum(rule, Rules.Rule.TYPE_FIELD_NUMBER);
      rejectUnknownEnum(rule, Rules.Rule.CLEANCODEATTRIBUTE_FIELD_NUMBER);
      var cleanCodeAttribute = Enums.getIfPresent(CleanCodeAttribute.class, rule.getCleanCodeAttribute().name()).orNull();
      var impacts = rule.getImpacts().getImpactsList().stream().map(RulesApi::convertImpact).collect(toMap(
        Map.Entry::getKey,
        Map.Entry::getValue,
        (first, duplicate) -> { throw new InvalidServerItemException("Duplicate rule impact quality"); }));
      var severity = parseEnum("rule severity", () -> IssueSeverity.valueOf(rule.getSeverity()));
      var type = parseEnum("rule type", () -> RuleType.valueOf(rule.getType().name()));
      if (SonarLanguage.forKey(rule.getLang()).isEmpty()) {
        throw new InvalidServerItemException("Unsupported rule language");
      }
      var result = new ServerRule(rule.getName(), severity, type, rule.getLang(), rule.getHtmlDesc(),
        convertDescriptionSections(rule),
        rule.getHtmlNote(), Set.copyOf(rule.getEducationPrinciples().getEducationPrinciplesList()), cleanCodeAttribute, impacts);
      cancelMonitor.checkCanceled();
      return Optional.of(result);
    } catch (Exception e) {
      rethrowCancellation(e);
      cancelMonitor.checkCanceled();
      LOG.error("Error when fetching rule '" + ruleKey + "'", e);
      if (e instanceof InvalidServerItemException || (e instanceof InvalidProtocolBufferException protobuf && protobuf.unwrapIOException() == protobuf)) {
        Sentry.captureException(e);
      }
    }
    return Optional.empty();
  }

  private static List<ServerRule.DescriptionSection> convertDescriptionSections(Rules.Rule rule) {
    if (rule.hasDescriptionSections()) {
      return rule.getDescriptionSections().getDescriptionSectionsList().stream()
        .map(s -> {
          ServerRule.DescriptionSection.Context context = null;
          if (s.hasContext()) {
            var contextFromServer = s.getContext();
            context = new ServerRule.DescriptionSection.Context(contextFromServer.getKey(), contextFromServer.getDisplayName());
          }
          return new ServerRule.DescriptionSection(s.getKey(), s.getContent(), Optional.ofNullable(context));
        }).toList();
    }
    return Collections.emptyList();
  }

  public Collection<ServerActiveRule> getAllActiveRules(String qualityProfileKey, SonarLintCancelMonitor cancelMonitor) {
    // Use a map to avoid duplicates during pagination
    Map<String, ServerActiveRule> activeRulesByKey = new HashMap<>();
    Map<String, String> ruleTemplatesByRuleKey = new HashMap<>();
    serverApiHelper.getPaginated(getSearchByQualityProfileUrl(qualityProfileKey),
      response -> Rules.SearchResponse.parser().parsePartialFrom(response),
      r -> r.hasPaging() ? r.getPaging().getTotal() : r.getTotal(),
      r -> {
        r.getRulesList().forEach(rule -> ruleTemplatesByRuleKey.put(rule.getKey(), rule.getTemplateKey()));
        return List.copyOf(r.getActives().getActivesMap().entrySet());
      },
      activeEntry -> {
        var ruleKey = activeEntry.getKey();
        var activeRule = convertActiveRule(ruleKey, activeEntry.getValue(), ruleTemplatesByRuleKey.get(ruleKey));
        activeRulesByKey.put(ruleKey, activeRule);
      },
      false,
      cancelMonitor);
    return activeRulesByKey.values();
  }

  private static ServerActiveRule convertActiveRule(String ruleKey, Rules.ActiveList activeList, String templateKey) {
    if (activeList.getActiveListCount() == 0) {
      throw new InvalidServerItemException("Missing active rule for '" + ruleKey + "'");
    }
    // The search is restricted to one profile, so only the first active entry is consumed.
    var active = activeList.getActiveList(0);
    var severity = parseEnum("active rule severity", () -> IssueSeverity.valueOf(active.getSeverity()));
    Map<String, String> params = new HashMap<>();
    for (var param : active.getParamsList()) {
      if (params.putIfAbsent(param.getKey(), param.getValue()) != null) {
        throw new InvalidServerItemException("Duplicate active rule parameter '" + param.getKey() + "'");
      }
    }
    var impacts = active.getImpacts().getImpactsList().stream().map(RulesApi::convertImpact)
      .map(impact -> new ImpactPayload(impact.getKey().name(), impact.getValue().name())).toList();
    return new ServerActiveRule(ruleKey, severity, params, templateKey, impacts);
  }

  private static Map.Entry<SoftwareQuality, ImpactSeverity> convertImpact(Common.Impact impact) {
    rejectUnknownEnum(impact, Common.Impact.SOFTWAREQUALITY_FIELD_NUMBER);
    rejectUnknownEnum(impact, Common.Impact.SEVERITY_FIELD_NUMBER);
    if (!impact.hasSoftwareQuality() || !impact.hasSeverity()) {
      throw new InvalidServerItemException("Missing required rule impact quality or severity");
    }
    return Map.entry(
      parseEnum("rule impact quality", () -> SoftwareQuality.valueOf(impact.getSoftwareQuality().name())),
      parseEnum("rule impact severity", () -> ImpactSeverity.mapSeverity(impact.getSeverity().name())));
  }

  private static void rejectUnknownEnum(Message message, int fieldNumber) {
    if (!message.getUnknownFields().getField(fieldNumber).getVarintList().isEmpty()) {
      throw new InvalidServerItemException("Unknown rule enum value in field " + fieldNumber);
    }
  }

  private static <T> T parseEnum(String field, Supplier<T> parser) {
    try {
      return parser.get();
    } catch (IllegalArgumentException e) {
      throw new InvalidServerItemException("Unsupported " + field, e);
    }
  }

  private static void rethrowCancellation(Exception exception) {
    var seen = new HashSet<Throwable>();
    for (Throwable cause = exception; cause != null && seen.add(cause); cause = cause.getCause()) {
      if (cause instanceof CancellationException cancellation) {
        throw cancellation;
      }
    }
  }

  private String getSearchByQualityProfileUrl(String qualityProfileKey) {
    var builder = new StringBuilder();
    builder.append("/api/rules/search.protobuf?qprofile=");
    builder.append(UrlUtils.urlEncode(qualityProfileKey));
    serverApiHelper.getOrganizationKey().ifPresent(org -> builder.append(ORGANIZATION_PARAM).append(UrlUtils.urlEncode(org)));
    builder.append("&activation=true&f=templateKey,actives&types=CODE_SMELL,BUG,VULNERABILITY,SECURITY_HOTSPOT&s=key");
    return builder.toString();
  }

  public Set<String> getAllTaintRules(List<SonarLanguage> enabledLanguages, SonarLintCancelMonitor cancelMonitor) {
    Set<String> taintRules = new HashSet<>();
    serverApiHelper.getPaginated(getSearchByRepoUrl(enabledLanguages.stream().map(TAINT_REPOS_BY_LANGUAGE::get).filter(Objects::nonNull).toList()),
      Rules.SearchResponse::parseFrom,
      Rules.SearchResponse::getTotal,
      Rules.SearchResponse::getRulesList,
      rule -> taintRules.add(rule.getKey()),
      false,
      cancelMonitor);
    return taintRules;
  }

  private String getSearchByRepoUrl(List<String> repositories) {
    var builder = new StringBuilder();
    builder.append("/api/rules/search.protobuf?repositories=");
    builder.append(repositories.stream().map(UrlUtils::urlEncode).collect(joining(",")));
    serverApiHelper.getOrganizationKey().ifPresent(org -> builder.append(ORGANIZATION_PARAM).append(UrlUtils.urlEncode(org)));
    // Add only f=repo even if we don't need it, else too many fields are returned by default
    builder.append("&f=repo&s=key");
    return builder.toString();
  }

}
