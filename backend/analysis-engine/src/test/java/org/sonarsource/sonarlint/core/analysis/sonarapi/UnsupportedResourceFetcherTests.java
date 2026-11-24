/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.sonarapi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnsupportedResourceFetcherTests {

  private final UnsupportedResourceFetcher underTest = new UnsupportedResourceFetcher();

  @Test
  void fetch_is_not_supported() {
    assertThatThrownBy(() -> underTest.fetch("javascript", "linux-x64/native-tool"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Cannot fetch 'linux-x64/native-tool' for plugin 'javascript' because resource fetching is not supported in SonarLint");
  }

  @Test
  void fetch_and_extract_is_not_supported() {
    assertThatThrownBy(() -> underTest.fetchAndExtract("javascript", "linux-x64/native-tool.tar.gz"))
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("Cannot fetch 'linux-x64/native-tool.tar.gz' for plugin 'javascript' because resource fetching is not supported in SonarLint");
  }

}
