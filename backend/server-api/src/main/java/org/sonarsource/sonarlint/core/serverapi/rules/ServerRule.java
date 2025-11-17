/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.rules;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.CleanCodeAttribute;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

public class ServerRule {
  private final String name;
  private final String htmlDesc;
  private final List<DescriptionSection> descriptionSections;
  private final String htmlNote;
  private final IssueSeverity severity;
  private final RuleType type;
  private final SonarLanguage language;
  private final Set<String> educationPrincipleKeys;
  private final CleanCodeAttribute cleanCodeAttribute;
  private final Map<SoftwareQuality, ImpactSeverity> impacts;


  public ServerRule(String name, IssueSeverity severity, RuleType type, String language, String htmlDesc, List<DescriptionSection> descriptionSections, String htmlNote,
    Set<String> educationPrincipleKeys, @Nullable CleanCodeAttribute cleanCodeAttribute, Map<SoftwareQuality, ImpactSeverity> impacts) {
    this.name = name;
    this.severity = severity;
    this.type = type;
    this.language = SonarLanguage.forKey(language).orElseThrow(() -> new IllegalArgumentException("Unknown language with key: " + language));
    this.htmlDesc = htmlDesc;
    this.descriptionSections = descriptionSections;
    this.htmlNote = htmlNote;
    this.educationPrincipleKeys = educationPrincipleKeys;
    this.cleanCodeAttribute = cleanCodeAttribute;
    this.impacts = impacts;
  }

  public String getName() {
    return name;
  }

  public String getHtmlDesc() {
    return htmlDesc;
  }

  public List<DescriptionSection> getDescriptionSections() {
    return descriptionSections;
  }

  public String getHtmlNote() {
    return htmlNote;
  }

  public IssueSeverity getSeverity() {
    return severity;
  }

  public RuleType getType() {
    return type;
  }

  public SonarLanguage getLanguage() {
    return language;
  }

  public Set<String> getEducationPrincipleKeys() {
    return educationPrincipleKeys;
  }

  @CheckForNull
  public CleanCodeAttribute getCleanCodeAttribute() {
    return cleanCodeAttribute;
  }

  public Map<SoftwareQuality, ImpactSeverity> getImpacts() {
    return impacts;
  }

  public static class DescriptionSection {
    private final String key;
    private final String htmlContent;
    private final Optional<Context> context;

    public DescriptionSection(String key, String htmlContent, Optional<Context> context) {
      this.key = key;
      this.htmlContent = htmlContent;
      this.context = context;
    }

    public String getKey() {
      return key;
    }

    public String getHtmlContent() {
      return htmlContent;
    }

    public Optional<Context> getContext() {
      return context;
    }

    public static class Context {
      private final String key;
      private final String displayName;

      public Context(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
      }

      public String getKey() {
        return key;
      }

      public String getDisplayName() {
        return displayName;
      }
    }
  }
}
