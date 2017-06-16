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

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class TelemetryStorageTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private LocalDate today = LocalDate.now();
  private Path filePath;

  @Before
  public void setUp() throws IOException {
    filePath = temp.newFolder().toPath().resolve("usage");
  }

  @Test
  public void test_default_data() {
    TelemetryStorage storage = new TelemetryStorage(filePath);

    TelemetryData data = storage.tryLoad();
    assertThat(filePath).doesNotExist();

    assertThat(data.installDate()).isEqualTo(today);
    assertThat(data.lastUseDate()).isNull();
    assertThat(data.numUseDays()).isEqualTo(0);
    assertThat(data.enabled()).isTrue();
  }

  @Test
  public void should_update_data() {
    TelemetryStorage storage = new TelemetryStorage(filePath);

    TelemetryData data = storage.tryLoad();
    assertThat(filePath).doesNotExist();

    data.usedAnalysis();
    data.usedConnectedMode();

    storage.trySave(data);
    assertThat(filePath).exists();

    TelemetryData data2 = storage.tryLoad();

    assertThat(data2.lastUseDate()).isEqualTo(today);
    assertThat(data2.numUseDays()).isEqualTo(1);
  }

  @Test
  public void should_fix_invalid_installDate() {
    TelemetryStorage storage = new TelemetryStorage(filePath);
    TelemetryData data = storage.tryLoad();
    data.setInstallDate(null);
    data.setNumUseDays(100);
    storage.trySave(data);

    TelemetryData data2 = storage.tryLoad();
    assertThat(data2.installDate()).isEqualTo(today);
    assertThat(data2.lastUseDate()).isNull();
    assertThat(data2.numUseDays()).isEqualTo(0);
  }

  @Test
  public void should_fix_invalid_numDays() {
    TelemetryStorage storage = new TelemetryStorage(filePath);
    TelemetryData data = storage.tryLoad();
    LocalDate tenDaysAgo = today.minusDays(10);
    data.setInstallDate(tenDaysAgo);
    data.setLastUseDate(today);
    data.setNumUseDays(100);
    storage.trySave(data);

    TelemetryData data2 = storage.tryLoad();
    assertThat(data2.installDate()).isEqualTo(tenDaysAgo);
    assertThat(data2.lastUseDate()).isEqualTo(today);
    assertThat(data2.numUseDays()).isEqualTo(11);
  }

  @Test
  public void should_fix_dates_in_future() {
    TelemetryStorage storage = new TelemetryStorage(filePath);
    TelemetryData data = storage.tryLoad();
    data.setInstallDate(today.plusDays(5));
    data.setLastUseDate(today.plusDays(7));
    data.setNumUseDays(100);
    storage.trySave(data);

    TelemetryData data2 = storage.tryLoad();
    assertThat(data2.installDate()).isEqualTo(today);
    assertThat(data2.lastUseDate()).isEqualTo(today);
    assertThat(data2.numUseDays()).isEqualTo(1);
  }

  @Test
  public void should_not_crash_when_cannot_read_storage() {
    // TODO
  }

  @Test
  public void should_not_crash_when_cannot_write_storage() {
    // TODO
  }
}
