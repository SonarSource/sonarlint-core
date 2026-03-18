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
package org.sonarsource.sonarlint.core.plugin;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.resolvers.PremiumArtifactResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PremiumArtifactResolverTest {

  private static final ResolvedArtifact PREMIUM = new ResolvedArtifact(ArtifactState.PREMIUM, null, null, null);

  @Test
  void should_return_premium_when_language_is_connected_mode_only() {
    var repo = mockRepo(Set.of(SonarLanguage.COBOL), Set.of());
    var resolver = new PremiumArtifactResolver(repo);

    var result = resolver.resolve(SonarLanguage.COBOL, "conn");

    assertThat(result).contains(PREMIUM);
  }

  @Test
  void should_return_empty_when_language_is_in_both_modes() {
    var repo = mockRepo(Set.of(SonarLanguage.JAVA), Set.of(SonarLanguage.JAVA));
    var resolver = new PremiumArtifactResolver(repo);

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_empty_when_language_is_standalone_only() {
    var repo = mockRepo(Set.of(), Set.of(SonarLanguage.JAVA));
    var resolver = new PremiumArtifactResolver(repo);

    var result = resolver.resolve(SonarLanguage.JAVA, null);

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_empty_when_language_is_in_neither_mode() {
    var repo = mockRepo(Set.of(), Set.of());
    var resolver = new PremiumArtifactResolver(repo);

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_premium_regardless_of_connection_id_for_connected_mode_only_language() {
    var repo = mockRepo(Set.of(SonarLanguage.COBOL), Set.of());
    var resolver = new PremiumArtifactResolver(repo);

    var resultWithNullConnection = resolver.resolve(SonarLanguage.COBOL, null);
    var resultWithConnection = resolver.resolve(SonarLanguage.COBOL, "anyConnection");

    assertThat(resultWithNullConnection).contains(PREMIUM);
    assertThat(resultWithConnection).contains(PREMIUM);
  }

  private static LanguageSupportRepository mockRepo(Set<SonarLanguage> connected, Set<SonarLanguage> standalone) {
    var repo = mock(LanguageSupportRepository.class);
    connected.forEach(lang -> when(repo.isEnabledInConnectedMode(lang)).thenReturn(true));
    standalone.forEach(lang -> when(repo.isEnabledInStandaloneMode(lang)).thenReturn(true));
    return repo;
  }
}
