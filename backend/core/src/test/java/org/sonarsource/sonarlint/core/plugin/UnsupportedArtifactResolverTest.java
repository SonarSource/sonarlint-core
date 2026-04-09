/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import java.util.EnumSet;
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

  @ParameterizedTest
  @MethodSource("supportedConfigurations")
  void should_return_empty_when_supported(boolean enabledInStandalone, boolean enabledInConnected, SonarLanguage language, String connectionId) {
    var languageSupport = mockLanguageSupport(enabledInStandalone, enabledInConnected);
    var resolver = new UnsupportedArtifactResolver(languageSupport);

    var result = resolver.resolve(language, connectionId);

    assertThat(result).isEmpty();
  }

  static Stream<Arguments> supportedConfigurations() {
    return Stream.of(
      Arguments.of(true, true, SonarLanguage.JAVA, null), // plugin key disabled standalone (legacy name context)
      Arguments.of(true, true, SonarLanguage.PYTHON, "conn"), // plugin key disabled connected
      Arguments.of(true, false, SonarLanguage.JAVA, null), // language enabled standalone
      Arguments.of(false, true, SonarLanguage.JAVA, "conn") // language enabled connected
    );
  }

  @ParameterizedTest
  @MethodSource("unsupportedConfigurations")
  void should_return_unsupported_when_not_supported(boolean enabledInStandalone, boolean enabledInConnected, SonarLanguage language, String connectionId) {
    var languageSupport = mockLanguageSupport(enabledInStandalone, enabledInConnected);
    var resolver = new UnsupportedArtifactResolver(languageSupport);

    var result = resolver.resolve(language, connectionId);

    assertThat(result).contains(UNSUPPORTED_ARTIFACT);
  }

  static Stream<Arguments> unsupportedConfigurations() {
    return Stream.of(
      // not enabled standalone
      Arguments.of(false, false, SonarLanguage.JAVA, null),
      // not enabled connected
      Arguments.of(false, false, SonarLanguage.JAVA, "conn")
    );
  }

  private static LanguageSupportRepository mockLanguageSupport(boolean enabledInStandalone, boolean enabledInConnected) {
    var repo = mock(LanguageSupportRepository.class);
    var standalone = enabledInStandalone ? EnumSet.of(SonarLanguage.JAVA, SonarLanguage.PYTHON) : EnumSet.noneOf(SonarLanguage.class);
    var connected = enabledInConnected ? EnumSet.of(SonarLanguage.JAVA, SonarLanguage.PYTHON) : EnumSet.noneOf(SonarLanguage.class);
    when(repo.getEnabledLanguagesInStandaloneMode()).thenReturn(standalone);
    when(repo.getEnabledLanguagesInConnectedMode()).thenReturn(connected);
    return repo;
  }

}
