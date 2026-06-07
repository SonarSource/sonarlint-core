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
package org.sonarsource.sonarlint.core.serverapi.qualityprofile;

public class QualityProfile {
  private final boolean isDefault;
  private final String key;
  private final String name;
  private final String language;
  private final String languageName;
  private final long activeRuleCount;
  private final String rulesUpdatedAt;
  private final String userUpdatedAt;

  private QualityProfile(Builder builder) {
    this.isDefault = builder.isDefault;
    this.key = builder.key;
    this.name = builder.name;
    this.language = builder.language;
    this.languageName = builder.languageName;
    this.activeRuleCount = builder.activeRuleCount;
    this.rulesUpdatedAt = builder.rulesUpdatedAt;
    this.userUpdatedAt = builder.userUpdatedAt;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isDefault() {
    return isDefault;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public String getLanguage() {
    return language;
  }

  public String getLanguageName() {
    return languageName;
  }

  public long getActiveRuleCount() {
    return activeRuleCount;
  }

  public String getRulesUpdatedAt() {
    return rulesUpdatedAt;
  }

  public String getUserUpdatedAt() {
    return userUpdatedAt;
  }

  public static class Builder {
    private boolean isDefault;
    private String key;
    private String name;
    private String language;
    private String languageName;
    private long activeRuleCount;
    private String rulesUpdatedAt;
    private String userUpdatedAt;

    private Builder() {
    }

    public Builder setIsDefault(boolean isDefault) {
      this.isDefault = isDefault;
      return this;
    }

    public Builder setKey(String key) {
      this.key = key;
      return this;
    }

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    public Builder setLanguage(String language) {
      this.language = language;
      return this;
    }

    public Builder setLanguageName(String languageName) {
      this.languageName = languageName;
      return this;
    }

    public Builder setActiveRuleCount(long activeRuleCount) {
      this.activeRuleCount = activeRuleCount;
      return this;
    }

    public Builder setRulesUpdatedAt(String rulesUpdatedAt) {
      this.rulesUpdatedAt = rulesUpdatedAt;
      return this;
    }

    public Builder setUserUpdatedAt(String userUpdatedAt) {
      this.userUpdatedAt = userUpdatedAt;
      return this;
    }

    public QualityProfile build() {
      return new QualityProfile(this);
    }
  }
}
