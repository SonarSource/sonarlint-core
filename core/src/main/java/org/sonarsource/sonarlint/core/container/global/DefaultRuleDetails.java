/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.global;

import java.util.Set;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;

public class DefaultRuleDetails implements RuleDetails {

  private final String key;
  private final String language;
  private final Set<String> tags;
  private final String name;
  private final String htmlDescription;
  private final String severity;

  public DefaultRuleDetails(String key, String name, String htmlDescription, String severity, String language, Set<String> tags) {
    this.key = key;
    this.name = name;
    this.htmlDescription = htmlDescription;
    this.severity = severity;
    this.language = language;
    this.tags = tags;
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
  public String getLanguage() {
    return language;
  }

  @Override
  public String getSeverity() {
    return severity;
  }

  @Override
  public String[] getTags() {
    return tags.toArray(new String[0]);
  }

}
