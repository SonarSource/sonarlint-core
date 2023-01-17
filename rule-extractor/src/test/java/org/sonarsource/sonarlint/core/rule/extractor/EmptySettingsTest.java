/*
 * SonarLint Core - Rule Extractor
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
package org.sonarsource.sonarlint.core.rule.extractor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmptySettingsTest {
  @Test
  void should_be_empty() {
    var emptySettings = new EmptySettings();

    assertThat(emptySettings.hasKey("")).isFalse();
    assertThat(emptySettings.getString("")).isNull();
    assertThat(emptySettings.getBoolean("")).isFalse();
    assertThat(emptySettings.getInt("")).isZero();
    assertThat(emptySettings.getLong("")).isZero();
    assertThat(emptySettings.getDate("")).isNull();
    assertThat(emptySettings.getDateTime("")).isNull();
    assertThat(emptySettings.getFloat("")).isNull();
    assertThat(emptySettings.getDouble("")).isNull();
    assertThat(emptySettings.getStringArray("")).isEmpty();
    assertThat(emptySettings.getStringLines("")).isEmpty();
    assertThat(emptySettings.getStringArrayBySeparator("", "")).isEmpty();
    assertThat(emptySettings.getKeysStartingWith("")).isEmpty();
  }
}
