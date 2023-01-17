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

import java.util.Optional;

public class SonarLintRuleDescriptionSection {
  private final String key;
  private final String htmlContent;
  private final Optional<Context> context;

  public SonarLintRuleDescriptionSection(String key, String htmlContent, Optional<Context> context) {
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
