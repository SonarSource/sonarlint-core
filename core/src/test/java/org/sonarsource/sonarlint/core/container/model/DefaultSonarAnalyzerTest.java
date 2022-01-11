/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class DefaultSonarAnalyzerTest {
  @Test
  public void testRoundTrip() {
    DefaultSonarAnalyzer analyzer = new DefaultSonarAnalyzer("key", "file", "hash", "version", false);

    assertThat(analyzer.key()).isEqualTo("key");
    assertThat(analyzer.filename()).isEqualTo("file");
    assertThat(analyzer.hash()).isEqualTo("hash");
    assertThat(analyzer.version()).isEqualTo("version");
    assertThat(analyzer.sonarlintCompatible()).isFalse();
    assertThat(analyzer.minimumVersion()).isNull();
  }

  @Test
  public void testSetters() {
    DefaultSonarAnalyzer analyzer = new DefaultSonarAnalyzer("key", "file", "hash", "version", false);

    
    analyzer.key("key2");
    analyzer.filename("file2");
    analyzer.hash("hash2");
    analyzer.version("version2");
    analyzer.sonarlintCompatible(true);
    analyzer.minimumVersion("minimumVersion2");

    assertThat(analyzer.key()).isEqualTo("key2");
    assertThat(analyzer.filename()).isEqualTo("file2");
    assertThat(analyzer.hash()).isEqualTo("hash2");
    assertThat(analyzer.version()).isEqualTo("version2");
    assertThat(analyzer.sonarlintCompatible()).isTrue();
    assertThat(analyzer.minimumVersion()).isEqualTo("minimumVersion2");
  }
}
