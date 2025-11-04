/*
 * SonarLint Core - Telemetry
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.telemetry.gessie.event;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.sonarsource.sonarlint.core.telemetry.gessie.event.GessieMetadata.SonarLintDomain;

class GessieMetadataTests {

  @ParameterizedTest
  @MethodSource
  void should_map_product_key_to_domain(String productKey, SonarLintDomain expected) {
    var actual = SonarLintDomain.fromProductKey(productKey);

    assertThat(actual).isEqualTo(expected);
  }

  public static Stream<Arguments> should_map_product_key_to_domain() {
    return Stream.of(
      Arguments.of("idea", SonarLintDomain.INTELLIJ),
      Arguments.of("eclipse", SonarLintDomain.ECLIPSE),
      Arguments.of("visualstudio", SonarLintDomain.VISUAL_STUDIO),
      Arguments.of("vscode", SonarLintDomain.VS_CODE),
      Arguments.of("cursor", SonarLintDomain.VS_CODE),
      Arguments.of("windsurf", SonarLintDomain.VS_CODE),
      Arguments.of("", SonarLintDomain.SLCORE),
      Arguments.of("test", SonarLintDomain.SLCORE)
    );
  }
}
