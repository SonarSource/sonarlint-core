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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DateUtilsTests {

  private static final ZoneId ZONE = ZoneId.of("UTC");
  private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZONE);

  @Test
  void should_format_age_relative_to_clock() {
    assertThat(toAge(NOW.minusMillis(100))).isEqualTo("few seconds ago");
    assertThat(toAge(NOW.minusMillis(65_000))).isEqualTo("1 minute ago");
    assertThat(toAge(NOW.minusMillis(3_600_000 + 100_000))).isEqualTo("1 hour ago");
    assertThat(toAge(NOW.minusMillis(2 * 3_600_000 + 100_000))).isEqualTo("2 hours ago");
    assertThat(toAge(NOW.minusMillis(24 * 3_600_000 + 100_000))).isEqualTo("1 day ago");
    assertThat(toAge(now().minusMonths(5).toInstant())).isEqualTo("5 months ago");
    assertThat(toAge(now().minusMonths(15).toInstant())).isEqualTo("1 year ago");
  }

  private static ZonedDateTime now() {
    return ZonedDateTime.ofInstant(NOW, ZONE);
  }

  private static String toAge(Instant creation) {
    return DateUtils.toAge(creation.toEpochMilli(), CLOCK);
  }
}
