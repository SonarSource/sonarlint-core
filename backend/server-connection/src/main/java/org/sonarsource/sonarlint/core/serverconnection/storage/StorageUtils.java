/*
 * SonarLint Core - Server Connection
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

public class StorageUtils {

  public static Set<SonarLanguage> deserializeLanguages(Optional<String> lastEnabledLanguages) {
    Set<String> lastIssueEnabledLanguagesStringSet = Collections.emptySet();
    Set<SonarLanguage> lastIssueEnabledLanguagesSet = new HashSet<>();

    if (lastEnabledLanguages.isPresent()) {
      lastIssueEnabledLanguagesStringSet = Stream.of(lastEnabledLanguages.get().split(",", -1))
        .collect(Collectors.toSet());
    }

    for(String languageString : lastIssueEnabledLanguagesStringSet){
      var language = SonarLanguage.getLanguageByLanguageKey(languageString);
      if(language.isPresent()){
        lastIssueEnabledLanguagesSet.add(language.get());
      }
    }

    return lastIssueEnabledLanguagesSet;
  }

}
