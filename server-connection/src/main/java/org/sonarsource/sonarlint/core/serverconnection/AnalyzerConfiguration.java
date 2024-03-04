/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.Map;

public class AnalyzerConfiguration {
  public static final int CURRENT_SCHEMA_VERSION = 1;
  private final Settings settings;
  private final Map<String, RuleSet> ruleSetByLanguageKey;

  private final int schemaVersion;

  public AnalyzerConfiguration(Settings settings, Map<String, RuleSet> ruleSetByLanguageKey, int schemaVersion) {
    this.settings = settings;
    this.ruleSetByLanguageKey = ruleSetByLanguageKey;
    this.schemaVersion = schemaVersion;
  }

  public Settings getSettings() {
    return settings;
  }

  public Map<String, RuleSet> getRuleSetByLanguageKey() {
    return ruleSetByLanguageKey;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }
}
