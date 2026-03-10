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
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.ArtifactState;
import org.sonarsource.sonarlint.core.plugin.ResolvedArtifact;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;

public class UnsupportedArtifactResolver implements ArtifactResolver {

  private final LanguageSupportRepository languageSupportRepository;
  private final Set<String> disabledPluginKeys;

  public UnsupportedArtifactResolver(
    LanguageSupportRepository languageSupportRepository,
    InitializeParams initializeParams) {
    this.languageSupportRepository = languageSupportRepository;
    this.disabledPluginKeys = Set.copyOf(initializeParams.getDisabledPluginKeysForAnalysis());
  }

  @Override
  public Optional<ResolvedArtifact> resolve(SonarLanguage language, @Nullable String connectionId) {
    if (!isSupported(language, connectionId) || disabledPluginKeys.contains(language.getPluginKey())) {
      return Optional.of(new ResolvedArtifact(ArtifactState.UNSUPPORTED, null, null, null));
    }
    return Optional.empty();
  }

  private boolean isSupported(SonarLanguage language, @Nullable String connectionId) {
    return (languageSupportRepository.isEnabledInStandaloneMode(language) && connectionId == null)
      || languageSupportRepository.isEnabledInConnectedMode(language);
  }
}
