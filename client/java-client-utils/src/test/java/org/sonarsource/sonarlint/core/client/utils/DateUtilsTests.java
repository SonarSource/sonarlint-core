/*
 * SonarLint Core - Java Client Utils
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
package org.sonarsource.sonarlint.core.client.utils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DateUtilsTests {

  private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-07T10:00:00Z"), ZoneId.systemDefault());
  private static final long FIXED_NOW_MILLIS = FIXED_CLOCK.millis();

  @Test
  void testAge() {
    assertThat(DateUtils.toAge(FIXED_NOW_MILLIS - 100, FIXED_CLOCK)).isEqualTo("few seconds ago");
    assertThat(DateUtils.toAge(FIXED_NOW_MILLIS - 65_000, FIXED_CLOCK)).isEqualTo("1 minute ago");
    assertThat(DateUtils.toAge(FIXED_NOW_MILLIS - 3_600_000 - 100_000, FIXED_CLOCK)).isEqualTo("1 hour ago");
    assertThat(DateUtils.toAge(FIXED_NOW_MILLIS - 2 * 3_600_000 - 100_000, FIXED_CLOCK)).isEqualTo("2 hours ago");
    assertThat(DateUtils.toAge(FIXED_NOW_MILLIS - 24 * 3_600_000 - 100_000, FIXED_CLOCK)).isEqualTo("1 day ago");
    assertThat(DateUtils.toAge(LocalDateTime.now(FIXED_CLOCK).minusMonths(5)
      .atZone(ZoneId.systemDefault())
      .toInstant()
      .toEpochMilli(), FIXED_CLOCK)).isEqualTo("5 months ago");
    assertThat(DateUtils.toAge(LocalDateTime.now(FIXED_CLOCK).minusMonths(15)
      .atZone(ZoneId.systemDefault())
      .toInstant()
      .toEpochMilli(), FIXED_CLOCK)).isEqualTo("1 year ago");
  }
}
