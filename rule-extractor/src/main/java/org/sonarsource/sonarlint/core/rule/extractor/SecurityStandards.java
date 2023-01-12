/*
 * SonarLint Core - Rule Extractor
 * Copyright (C) 2016-2022 SonarSource SA
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

import static java.util.Arrays.stream;
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

  public static final Map<SLCategory, Set<String>> CWES_BY_SL_CATEGORY = ImmutableMap.<SLCategory, Set<String>>builder()
    .put(SLCategory.BUFFER_OVERFLOW, Set.of("119", "120", "131", "676", "788"))
    .put(SLCategory.SQL_INJECTION, Set.of("89", "564", "943"))
    .put(SLCategory.COMMAND_INJECTION, Set.of("77", "78", "88", "214"))
    .put(SLCategory.PATH_TRAVERSAL_INJECTION, Set.of("22"))
    .put(SLCategory.LDAP_INJECTION, Set.of("90"))
    .put(SLCategory.XPATH_INJECTION, Set.of("643"))
    .put(SLCategory.RCE, Set.of("94", "95"))
    .put(SLCategory.DOS, Set.of("400", "624"))
    .put(SLCategory.SSRF, Set.of("918"))
    .put(SLCategory.CSRF, Set.of("352"))
    .put(SLCategory.XSS, Set.of("79", "80", "81", "82", "83", "84", "85", "86", "87"))
    .put(SLCategory.LOG_INJECTION, Set.of("117"))
    .put(SLCategory.HTTP_RESPONSE_SPLITTING, Set.of("113"))
    .put(SLCategory.OPEN_REDIRECT, Set.of("601"))
    .put(SLCategory.XXE, Set.of("611", "827"))
    .put(SLCategory.OBJECT_INJECTION, Set.of("134", "470", "502"))
    .put(SLCategory.WEAK_CRYPTOGRAPHY, Set.of("295", "297", "321", "322", "323", "324", "325", "326", "327", "328", "330", "780"))
    .put(SLCategory.AUTH, Set.of("798", "640", "620", "549", "522", "521", "263", "262", "261", "259", "308"))
    .put(SLCategory.INSECURE_CONF, Set.of("102", "215", "346", "614", "489", "942"))
    .put(SLCategory.FILE_MANIPULATION, Set.of("97", "73"))
    .put(SLCategory.ENCRYPTION_OF_SENSITIVE_DATA, Set.of("311", "315", "319"))
    .put(SLCategory.TRACEABILITY, Set.of("778"))
    .put(SLCategory.PERMISSION, Set.of("266", "269", "284", "668", "732"))
    .build();
  private static final Ordering<SLCategory> SL_CATEGORY_ORDERING = Ordering.explicit(stream(SLCategory.values()).collect(toList()));
  public static final Ordering<String> SL_CATEGORY_KEYS_ORDERING = Ordering.explicit(stream(SLCategory.values()).map(SLCategory::getKey).collect(toList()));

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
    List<SLCategory> sl = toSortedSLCategories(cwe);
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

  private static List<SLCategory> toSortedSLCategories(Collection<String> cwe) {
    List<SLCategory> result = CWES_BY_SL_CATEGORY
      .keySet()
      .stream()
      .filter(k -> cwe.stream().anyMatch(CWES_BY_SL_CATEGORY.get(k)::contains))
      .sorted(SL_CATEGORY_ORDERING)
      .collect(toList());
    return result.isEmpty() ? singletonList(SLCategory.OTHERS) : result;
  }

}
