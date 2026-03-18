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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.languages.LanguageSupportRepository;
import org.sonarsource.sonarlint.core.plugin.resolvers.UnsupportedArtifactResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnsupportedArtifactResolverTest {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  private static final ResolvedArtifact UNSUPPORTED_ARTIFACT = new ResolvedArtifact(ArtifactState.UNSUPPORTED, null, null, null);

  // --- Disabled-keys behaviour ---

  @Test
  void should_return_empty_when_plugin_key_is_disabled_for_analysis_in_standalone() {
    var languageSupport = mockLanguageSupport(true, true);
    var resolver = new UnsupportedArtifactResolver(languageSupport);

    var result = resolver.resolve(SonarLanguage.JAVA, null);

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_empty_when_plugin_key_is_disabled_for_analysis_in_connected_mode() {
    var languageSupport = mockLanguageSupport(true, true);
    var resolver = new UnsupportedArtifactResolver(languageSupport);

    var result = resolver.resolve(SonarLanguage.PYTHON, "conn");

    assertThat(result).isEmpty();
  }

  // --- Language-support mode behaviour ---

  @Test
  void should_return_unsupported_when_language_not_enabled_in_standalone() {
    var languageSupport = mockLanguageSupport(false, false);
    var resolver = new UnsupportedArtifactResolver(languageSupport);

    var result = resolver.resolve(SonarLanguage.JAVA, null);

    assertThat(result).contains(UNSUPPORTED_ARTIFACT);
  }

  @Test
  void should_return_unsupported_when_language_not_enabled_in_connected_mode() {
    var languageSupport = mockLanguageSupport(false, false);
    var resolver = new UnsupportedArtifactResolver(languageSupport);

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).contains(UNSUPPORTED_ARTIFACT);
  }

  @Test
  void should_return_empty_when_language_is_enabled_in_standalone() {
    var languageSupport = mockLanguageSupport(true, false);
    var resolver = new UnsupportedArtifactResolver(languageSupport);

    var result = resolver.resolve(SonarLanguage.JAVA, null);

    assertThat(result).isEmpty();
  }

  @Test
  void should_return_empty_when_language_is_enabled_in_connected_mode() {
    var languageSupport = mockLanguageSupport(false, true);
    var resolver = new UnsupportedArtifactResolver(languageSupport);

    var result = resolver.resolve(SonarLanguage.JAVA, "conn");

    assertThat(result).isEmpty();
  }

  private static LanguageSupportRepository mockLanguageSupport(boolean enabledInStandalone, boolean enabledInConnected) {
    var repo = mock(LanguageSupportRepository.class);
    when(repo.isEnabledInStandaloneMode(SonarLanguage.JAVA)).thenReturn(enabledInStandalone);
    when(repo.isEnabledInConnectedMode(SonarLanguage.JAVA)).thenReturn(enabledInConnected);
    when(repo.isEnabledInStandaloneMode(SonarLanguage.PYTHON)).thenReturn(enabledInStandalone);
    when(repo.isEnabledInConnectedMode(SonarLanguage.PYTHON)).thenReturn(enabledInConnected);
    return repo;
  }
}
