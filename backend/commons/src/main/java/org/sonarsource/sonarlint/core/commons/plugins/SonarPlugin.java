/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.plugins;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

public enum SonarPlugin {
  ABAP("abap"),
  APEX("sonarapex"),
  C_FAMILY("cpp"),
  // CSHARP_ENTERPRISE must be declared before CS_OSS so it can be referenced in the constructor
  CSHARP_ENTERPRISE("csharpenterprise"),
  CS_OSS("csharp", CSHARP_ENTERPRISE),
  COBOL("cobol"),
  GO("go", new EnterpriseReplacement(true, Version.create("2025.2"))),
  IAC("iac", new EnterpriseReplacement(true, Version.create("2025.1"))),
  JAVA("java"),
  JCL("jcl"),
  JS("javascript"),
  KOTLIN("kotlin"),
  PHP("php"),
  PLI("pli"),
  PLSQL("plsql"),
  PYTHON("python"),
  RPG("rpg"),
  RUBY("ruby"),
  SCALA("sonarscala"),
  SWIFT("swift"),
  TEXT("text", new EnterpriseReplacement(true, Version.create("10.4"))),
  TSQL("tsql"),
  // VBNET_ENTERPRISE must be declared before VBNET_OSS
  VBNET_ENTERPRISE("vbnetenterprise"),
  VBNET_OSS("vbnet", VBNET_ENTERPRISE),
  WEB("web"),
  XML("xml");

  public static Optional<SonarPlugin> findByKey(String key) {
    return Arrays.stream(values()).filter(p -> p.key.equals(key)).findFirst();
  }

  /**
   * Returns {@code true} if the given key is a known enterprise variant with a <em>different</em>
   * plugin key than its base plugin (e.g. {@code "csharpenterprise"} for CS,
   * {@code "vbnetenterprise"} for VB.NET).
   *
   * <p>Enterprise editions that share the base plugin key (GO, IAC, TEXT) are <em>not</em>
   * considered enterprise variants in this sense — they are represented by
   * {@link #getEnterpriseReplacement()} on the base plugin entry.</p>
   */
  public static boolean isEnterpriseVariant(String key) {
    return Arrays.stream(values())
      .map(p -> p.enterpriseVariant)
      .filter(Objects::nonNull)
      .anyMatch(ev -> ev.getKey().equals(key));
  }

  /**
   * Returns the base plugin key for a different-key enterprise variant
   * (e.g. {@code "csharp"} for {@code "csharpenterprise"}), or empty if the key is not a
   * known enterprise variant.
   */
  public static Optional<SonarPlugin> basePluginFor(String enterpriseKey) {
    return Arrays.stream(values())
      .filter(p -> p.enterpriseVariant != null && p.enterpriseVariant.getKey().equals(enterpriseKey))
      .findFirst();
  }

  /**
   * Returns the base plugin key for a different-key enterprise variant
   * (e.g. {@code "csharp"} for {@code "csharpenterprise"}), or empty if the key is not a
   * known enterprise variant.
   */
  public static Optional<String> baseKeyFor(String enterpriseKey) {
    return basePluginFor(enterpriseKey)
      .map(SonarPlugin::getKey);
  }

  private final String key;
  /**
   * Non-null for plugins that have an enterprise variant plugin that uses a
   * different server key (e.g. {@code CSHARP_ENTERPRISE}).
   */
  @Nullable
  private final SonarPlugin enterpriseVariant;
  /**
   * Non-null for plugins whose enterprise edition is a drop-in replacement served under the
   * <em>same</em> plugin key (GO, IAC, TEXT).
   */
  @Nullable
  private final EnterpriseReplacement enterpriseReplacement;

  SonarPlugin(String key) {
    this.key = key;
    this.enterpriseVariant = null;
    this.enterpriseReplacement = null;
  }

  /** Constructor for plugins with a different-key enterprise variant (CS, VBNET). */
  SonarPlugin(String key, SonarPlugin enterpriseVariant) {
    this.key = key;
    this.enterpriseVariant = enterpriseVariant;
    this.enterpriseReplacement = null;
  }

  /** Constructor for same-key enterprise plugins (GO, IAC, TEXT). */
  SonarPlugin(String key, EnterpriseReplacement enterpriseReplacement) {
    this.key = key;
    this.enterpriseVariant = null;
    this.enterpriseReplacement = enterpriseReplacement;
  }

  public String getKey() {
    return key;
  }

  /**
   * Returns the enterprise variant plugin (with a different key) for this plugin, if any.
   */
  public Optional<SonarPlugin> getEnterpriseVariant() {
    return Optional.ofNullable(enterpriseVariant);
  }

  /**
   * Returns the enterprise replacement metadata for same-key enterprise plugins (GO, IAC, TEXT),
   * or empty for all other plugins.
   */
  public Optional<EnterpriseReplacement> getEnterpriseReplacement() {
    return Optional.ofNullable(enterpriseReplacement);
  }

  public Set<SonarLanguage> getLanguages() {
    var sonarLanguages = EnumSet.noneOf(SonarLanguage.class);
    sonarLanguages.addAll(Arrays.stream(SonarLanguage.values()).filter(l -> l.getPlugin().getKey().equals(key)).collect(Collectors.toSet()));
    basePluginFor(key).ifPresent(sonarPlugin -> sonarLanguages.addAll(sonarPlugin.getLanguages()));
    return sonarLanguages;
  }
}
