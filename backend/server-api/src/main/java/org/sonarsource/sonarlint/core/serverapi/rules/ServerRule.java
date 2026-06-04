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

import java.util.Collections;
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

  private ServerRule(Builder builder) {
    this.name = builder.name;
    this.severity = builder.severity;
    this.type = builder.type;
    this.language = SonarLanguage.forKey(builder.language).orElseThrow(() -> new IllegalArgumentException("Unknown language with key: " + builder.language));
    this.htmlDesc = builder.htmlDesc;
    this.descriptionSections = builder.descriptionSections;
    this.htmlNote = builder.htmlNote;
    this.educationPrincipleKeys = builder.educationPrincipleKeys;
    this.cleanCodeAttribute = builder.cleanCodeAttribute;
    this.impacts = builder.impacts;
  }

  public static Builder builder() {
    return new Builder();
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

  public static class Builder {
    private String name;
    private String htmlDesc;
    private List<DescriptionSection> descriptionSections = Collections.emptyList();
    private String htmlNote;
    private IssueSeverity severity;
    private RuleType type;
    private String language;
    private Set<String> educationPrincipleKeys = Collections.emptySet();
    private CleanCodeAttribute cleanCodeAttribute;
    private Map<SoftwareQuality, ImpactSeverity> impacts = Collections.emptyMap();

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    public Builder setSeverity(IssueSeverity severity) {
      this.severity = severity;
      return this;
    }

    public Builder setType(RuleType type) {
      this.type = type;
      return this;
    }

    public Builder setLanguage(String language) {
      this.language = language;
      return this;
    }

    public Builder setHtmlDesc(String htmlDesc) {
      this.htmlDesc = htmlDesc;
      return this;
    }

    public Builder setDescriptionSections(List<DescriptionSection> descriptionSections) {
      this.descriptionSections = descriptionSections;
      return this;
    }

    public Builder setHtmlNote(String htmlNote) {
      this.htmlNote = htmlNote;
      return this;
    }

    public Builder setEducationPrincipleKeys(Set<String> educationPrincipleKeys) {
      this.educationPrincipleKeys = educationPrincipleKeys;
      return this;
    }

    public Builder setCleanCodeAttribute(@Nullable CleanCodeAttribute cleanCodeAttribute) {
      this.cleanCodeAttribute = cleanCodeAttribute;
      return this;
    }

    public Builder setImpacts(Map<SoftwareQuality, ImpactSeverity> impacts) {
      this.impacts = impacts;
      return this;
    }

    public ServerRule build() {
      return new ServerRule(this);
    }
  }
}
