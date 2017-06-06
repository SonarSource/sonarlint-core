/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DateUtilsTest {

  @Test
  public void testAge() {
    assertThat(DateUtils.toAge(System.currentTimeMillis() - 100)).isEqualTo("few seconds ago");
    assertThat(DateUtils.toAge(System.currentTimeMillis() - 65_000)).isEqualTo("1 minute ago");
    assertThat(DateUtils.toAge(System.currentTimeMillis() - 3_600_000 - 100_000)).isEqualTo("1 hour ago");
    assertThat(DateUtils.toAge(System.currentTimeMillis() - 2 * 3_600_000 - 100_000)).isEqualTo("2 hours ago");
    assertThat(DateUtils.toAge(System.currentTimeMillis() - 24 * 3_600_000 - 100_000)).isEqualTo("1 day ago");
    assertThat(DateUtils.toAge(LocalDateTime.now()
      .minus(5, ChronoUnit.MONTHS)
      .atZone(ZoneId.systemDefault())
      .toInstant()
      .toEpochMilli())).isEqualTo("5 months ago");
    assertThat(DateUtils.toAge(LocalDateTime.now()
      .minus(15, ChronoUnit.MONTHS)
      .atZone(ZoneId.systemDefault())
      .toInstant()
      .toEpochMilli())).isEqualTo("1 year ago");
  }
}
