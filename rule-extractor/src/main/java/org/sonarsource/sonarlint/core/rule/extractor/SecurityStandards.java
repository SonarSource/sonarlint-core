/*
 * SonarLint Core - Rule Extractor
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
package org.sonarsource.sonarlint.core.rule.extractor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.sonarsource.sonarlint.core.commons.VulnerabilityProbability.HIGH;
import static org.sonarsource.sonarlint.core.commons.VulnerabilityProbability.LOW;
import static org.sonarsource.sonarlint.core.commons.VulnerabilityProbability.MEDIUM;

@Immutable
public final class SecurityStandards {

  public static final String UNKNOWN_STANDARD = "unknown";
  private static final String CWE_PREFIX = "cwe:";

  public enum SLCategory {
    BUFFER_OVERFLOW("buffer-overflow", HIGH),
    SQL_INJECTION("sql-injection", HIGH),
    RCE("rce", MEDIUM),
    OBJECT_INJECTION("object-injection", LOW),
    COMMAND_INJECTION("command-injection", HIGH),
    PATH_TRAVERSAL_INJECTION("path-traversal-injection", HIGH),
    LDAP_INJECTION("ldap-injection", LOW),
    XPATH_INJECTION("xpath-injection", LOW),
    LOG_INJECTION("log-injection", LOW),
    XXE("xxe", MEDIUM),
    XSS("xss", HIGH),
    DOS("dos", MEDIUM),
    SSRF("ssrf", MEDIUM),
    CSRF("csrf", HIGH),
    HTTP_RESPONSE_SPLITTING("http-response-splitting", LOW),
    OPEN_REDIRECT("open-redirect", MEDIUM),
    WEAK_CRYPTOGRAPHY("weak-cryptography", MEDIUM),
    AUTH("auth", HIGH),
    INSECURE_CONF("insecure-conf", LOW),
    FILE_MANIPULATION("file-manipulation", LOW),
    ENCRYPTION_OF_SENSITIVE_DATA("encrypt-data", LOW),
    TRACEABILITY("traceability", LOW),
    PERMISSION("permission", MEDIUM),
    OTHERS("others", LOW);

    private final String key;
    private final VulnerabilityProbability vulnerability;

    SLCategory(String key, VulnerabilityProbability vulnerability) {
      this.key = key;
      this.vulnerability = vulnerability;
    }

    public String getKey() {
      return key;
    }

    public VulnerabilityProbability getVulnerability() {
      return vulnerability;
    }
  }

  public static final Map<SLCategory, Set<String>> CWES_BY_SL_CATEGORY = Map.ofEntries(
      Map.entry(SLCategory.BUFFER_OVERFLOW, Set.of("119", "120", "131", "676", "788")),
      Map.entry(SLCategory.SQL_INJECTION, Set.of("89", "564", "943")),
      Map.entry(SLCategory.COMMAND_INJECTION, Set.of("77", "78", "88", "214")),
      Map.entry(SLCategory.PATH_TRAVERSAL_INJECTION, Set.of("22")),
      Map.entry(SLCategory.LDAP_INJECTION, Set.of("90")),
      Map.entry(SLCategory.XPATH_INJECTION, Set.of("643")),
      Map.entry(SLCategory.RCE, Set.of("94", "95")),
      Map.entry(SLCategory.DOS, Set.of("400", "624")),
      Map.entry(SLCategory.SSRF, Set.of("918")),
      Map.entry(SLCategory.CSRF, Set.of("352")),
      Map.entry(SLCategory.XSS, Set.of("79", "80", "81", "82", "83", "84", "85", "86", "87")),
      Map.entry(SLCategory.LOG_INJECTION, Set.of("117")),
      Map.entry(SLCategory.HTTP_RESPONSE_SPLITTING, Set.of("113")),
      Map.entry(SLCategory.OPEN_REDIRECT, Set.of("601")),
      Map.entry(SLCategory.XXE, Set.of("611", "827")),
      Map.entry(SLCategory.OBJECT_INJECTION, Set.of("134", "470", "502")),
      Map.entry(SLCategory.WEAK_CRYPTOGRAPHY, Set.of("295", "297", "321", "322", "323", "324", "325", "326", "327", "328", "330", "780")),
      Map.entry(SLCategory.AUTH, Set.of("798", "640", "620", "549", "522", "521", "263", "262", "261", "259", "308")),
      Map.entry(SLCategory.INSECURE_CONF, Set.of("102", "215", "346", "614", "489", "942")),
      Map.entry(SLCategory.FILE_MANIPULATION, Set.of("97", "73")),
      Map.entry(SLCategory.ENCRYPTION_OF_SENSITIVE_DATA, Set.of("311", "315", "319")),
      Map.entry(SLCategory.TRACEABILITY, Set.of("778")),
      Map.entry(SLCategory.PERMISSION, Set.of("266", "269", "284", "668", "732")));

  private final Set<String> standards;
  private final Set<String> cwe;
  private final SLCategory sLCategory;
  private final Set<SLCategory> ignoredSLCategories;

  private SecurityStandards(Set<String> standards, Set<String> cwe, SLCategory sLCategory, Set<SLCategory> ignoredSLCategories) {
    this.standards = standards;
    this.cwe = cwe;
    this.sLCategory = sLCategory;
    this.ignoredSLCategories = ignoredSLCategories;
  }

  public SLCategory getSlCategory() {
    return sLCategory;
  }

  /**
   * If CWEs mapped to multiple {@link SLCategory}, those which are not taken into account are listed here.
   */
  public Set<SLCategory> getIgnoredSLCategories() {
    return ignoredSLCategories;
  }

  public Set<String> getStandards() {
    return standards;
  }

  public Set<String> getCwe() {
    return cwe;
  }

  /**
   * @throws IllegalStateException if {@code securityStandards} maps to multiple {@link SLCategory SLCategories}
   */
  public static SecurityStandards fromSecurityStandards(Set<String> securityStandards) {
    Set<String> standards = securityStandards.stream().filter(Objects::nonNull).collect(toSet());
    Set<String> cwe = toCwes(standards);
    List<SLCategory> sl = toSLCategories(cwe);
    var slCategory = sl.iterator().next();
    Set<SLCategory> ignoredSLCategories = sl.stream().skip(1).collect(toSet());
    return new SecurityStandards(standards, cwe, slCategory, ignoredSLCategories);
  }

  private static Set<String> toCwes(Collection<String> securityStandards) {
    Set<String> result = securityStandards.stream()
      .filter(s -> s.startsWith(CWE_PREFIX))
      .map(s -> s.substring(CWE_PREFIX.length()))
      .collect(toSet());
    return result.isEmpty() ? singleton(UNKNOWN_STANDARD) : result;
  }

  private static List<SLCategory> toSLCategories(Collection<String> cwe) {
    List<SLCategory> result = CWES_BY_SL_CATEGORY
      .keySet()
      .stream()
      .filter(k -> cwe.stream().anyMatch(CWES_BY_SL_CATEGORY.get(k)::contains))
      .collect(toList());
    return result.isEmpty() ? singletonList(SLCategory.OTHERS) : result;
  }

}
