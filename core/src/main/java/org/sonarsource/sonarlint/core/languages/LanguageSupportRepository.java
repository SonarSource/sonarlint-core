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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.sonarsource.sonarlint.core.clientapi.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.commons.Language;

@Named
@Singleton
public class LanguageSupportRepository {
  private static final EnumSet<Language> LANGUAGES_RAISING_TAINT_VULNERABILITIES = EnumSet.of(Language.CS, Language.JAVA, Language.JS, Language.TS, Language.PHP, Language.PYTHON);
  private final EnumSet<Language> enabledLanguagesInStandaloneMode;
  private final EnumSet<Language> enabledLanguagesInConnectedMode;

  public LanguageSupportRepository(InitializeParams params) {
    this.enabledLanguagesInStandaloneMode = toEnumSet(
      adaptLanguage(params.getEnabledLanguagesInStandaloneMode()), Language.class);
    this.enabledLanguagesInConnectedMode = EnumSet.copyOf(this.enabledLanguagesInStandaloneMode);
    this.enabledLanguagesInConnectedMode.addAll(adaptLanguage(params.getExtraEnabledLanguagesInConnectedMode()));
  }

  @NotNull
  private static List<Language> adaptLanguage(Set<org.sonarsource.sonarlint.core.clientapi.common.Language> languagesDto) {
    return languagesDto.stream().map(e -> Language.valueOf(e.name())).collect(Collectors.toList());
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
