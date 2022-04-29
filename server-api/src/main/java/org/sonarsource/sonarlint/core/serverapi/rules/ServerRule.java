/*
 * SonarLint Server API
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
package org.sonarsource.sonarlint.core.serverapi.rules;

import org.sonarsource.sonarlint.core.commons.Language;

public class ServerRule {
  private final String name;
  private final String htmlDesc;
  private final String htmlNote;
  private final String severity;
  private final String type;
  private final Language language;

  public ServerRule(String name, String severity, String type, String language, String htmlDesc, String htmlNote) {
    this.name = name;
    this.severity = severity;
    this.type = type;
    this.language = Language.forKey(language).orElseThrow(() -> new IllegalArgumentException("Unknown language with key: " + language));
    this.htmlDesc = htmlDesc;
    this.htmlNote = htmlNote;
  }

  public String getName() {
    return name;
  }

  public String getHtmlDesc() {
    return htmlDesc;
  }

  public String getHtmlNote() {
    return htmlNote;
  }

  public String getSeverity() {
    return severity;
  }

  public String getType() {
    return type;
  }

  public Language getLanguage() {
    return language;
  }
}
