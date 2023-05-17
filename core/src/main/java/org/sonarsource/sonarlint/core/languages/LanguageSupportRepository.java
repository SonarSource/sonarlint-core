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
package org.sonarsource.sonarlint.core.languages;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.Language;

public class LanguageSupportRepository {
  private static final EnumSet<Language> LANGUAGES_RAISING_TAINT_VULNERABILITIES = EnumSet.of(Language.CS, Language.JAVA, Language.JS, Language.TS, Language.PHP, Language.PYTHON);
  private EnumSet<Language> enabledLanguagesInStandaloneMode;
  private EnumSet<Language> enabledLanguagesInConnectedMode;

  public void initialize(Set<Language> enabledLanguagesInStandaloneMode, Set<Language> extraEnabledLanguagesInConnectedMode) {
    this.enabledLanguagesInStandaloneMode = toEnumSet(enabledLanguagesInStandaloneMode, Language.class);
    this.enabledLanguagesInConnectedMode = EnumSet.copyOf(this.enabledLanguagesInStandaloneMode);
    this.enabledLanguagesInConnectedMode.addAll(extraEnabledLanguagesInConnectedMode);
  }

  private static <T extends Enum<T>> EnumSet<T> toEnumSet(Collection<T> collection, Class<T> clazz) {
    return collection.isEmpty() ? EnumSet.noneOf(clazz) : EnumSet.copyOf(collection);
  }

  public Set<Language> getEnabledLanguagesInStandaloneMode() {
    return enabledLanguagesInStandaloneMode;
  }

  public Set<Language> getEnabledLanguagesInConnectedMode() {
    return enabledLanguagesInConnectedMode;
  }

  public boolean areTaintVulnerabilitiesSupported() {
    var intersection = EnumSet.copyOf(LANGUAGES_RAISING_TAINT_VULNERABILITIES);
    intersection.retainAll(enabledLanguagesInConnectedMode);
    return !intersection.isEmpty();
  }
}
