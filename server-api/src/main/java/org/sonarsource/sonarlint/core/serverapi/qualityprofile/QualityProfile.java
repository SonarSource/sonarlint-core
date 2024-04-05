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

  public QualityProfile(boolean isDefault, String key, String name, String language, String languageName,
    long activeRuleCount, String rulesUpdatedAt, String userUpdatedAt) {
    this.isDefault = isDefault;
    this.key = key;
    this.name = name;
    this.language = language;
    this.languageName = languageName;
    this.activeRuleCount = activeRuleCount;
    this.rulesUpdatedAt = rulesUpdatedAt;
    this.userUpdatedAt = userUpdatedAt;
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
}
