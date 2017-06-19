/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.telemetry;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.sonarsource.sonarlint.core.telemetry.TelemetryUtils.dayChanged;

public class TelemetryUtilsTest {
  @Test
  public void dayChanged_should_return_true_for_null() {
    assertThat(dayChanged(null)).isTrue();
  }

  @Test
  public void dayChanged_should_return_true_if_older() {
    assertThat(dayChanged(LocalDate.now().minusDays(1))).isTrue();
  }

  @Test
  public void dayChanged_should_return_false_if_same() {
    assertThat(dayChanged(LocalDate.now())).isFalse();
  }

  @Test
  public void dayChanged_with_hours_should_return_true_for_null() {
    assertThat(dayChanged(null, 1)).isTrue();
  }

  @Test
  public void dayChanged_with_hours_should_return_false_if_day_same() {
    assertThat(dayChanged(LocalDateTime.now(), 100)).isFalse();
  }

  @Test
  public void dayChanged_with_hours_should_return_false_if_different_day_but_within_hours() {
    LocalDateTime date = LocalDateTime.now().minusDays(1);
    long hours = date.until(LocalDateTime.now(), ChronoUnit.HOURS);
    assertThat(dayChanged(date, hours + 1)).isFalse();
  }

  @Test
  public void dayChanged_with_hours_should_return_true_if_different_day_and_beyond_hours() {
    LocalDateTime date = LocalDateTime.now().minusDays(1);
    long hours = date.until(LocalDateTime.now(), ChronoUnit.HOURS);
    assertThat(dayChanged(date, hours)).isTrue();
  }
}
