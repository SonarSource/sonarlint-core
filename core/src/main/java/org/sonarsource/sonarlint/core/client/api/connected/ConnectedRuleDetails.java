/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.client.api.connected;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;

public class ConnectedRuleDetails implements RuleDetails {

  private final String key;
  private final Language language;
  private final String name;
  private final String htmlDescription;
  private final IssueSeverity defaultSeverity;
  private final RuleType type;
  private final String extendedDescription;

  public ConnectedRuleDetails(String key, String name, @Nullable String htmlDescription, IssueSeverity defaultSeverity, RuleType type, Language language,
    String extendedDescription) {
    this.key = key;
    this.name = name;
    this.htmlDescription = htmlDescription;
    this.defaultSeverity = defaultSeverity;
    this.type = type;
    this.language = language;
    this.extendedDescription = extendedDescription;
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getHtmlDescription() {
    return htmlDescription;
  }

  @Override
  public Language getLanguage() {
    return language;
  }

  @Override
  public IssueSeverity getDefaultSeverity() {
    return defaultSeverity;
  }

  @Override
  public RuleType getType() {
    return type;
  }

  public String getExtendedDescription() {
    return extendedDescription;
  }

}
