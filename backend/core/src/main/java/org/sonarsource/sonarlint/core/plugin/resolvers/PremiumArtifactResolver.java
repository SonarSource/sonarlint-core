/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.plugin.resolvers;

import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.ResolvedArtifact;

/**
 * Special resolver handling specific languages that require a Commercial (Premium) license or
 * connected mode synchronization. It interrupts the resolution chain returning a PREMIUM state
 * when a user attempts to analyze premium languages in an unsupported capacity.
 */
public class PremiumArtifactResolver implements ArtifactResolver {

  private final LanguageSupportRepository languageSupportRepository;

  public PremiumArtifactResolver(LanguageSupportRepository languageSupportRepository) {
    this.languageSupportRepository = languageSupportRepository;
  }

  @Override
  public Optional<ResolvedArtifact> resolve(SonarLanguage language, @Nullable String connectionId) {
    if (languageSupportRepository.getEnabledLanguagesInConnectedMode().contains(language)
      && !languageSupportRepository.getEnabledLanguagesInStandaloneMode().contains(language)) {
      return Optional.of(new ResolvedArtifact(ArtifactState.PREMIUM, null, null, null));
    }
    return Optional.empty();
  }
}
