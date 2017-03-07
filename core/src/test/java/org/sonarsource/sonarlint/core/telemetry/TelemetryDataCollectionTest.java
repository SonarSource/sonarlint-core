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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TelemetryDataCollectionTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private TelemetryDataCollection dataCollection;
  private TelemetryStorage storage;
  private LocalDate today;
  private Path file;

  @Before
  public void setUp() {
    storage = new TelemetryStorage();
    file = temp.getRoot().toPath().resolve("file");
    dataCollection = new TelemetryDataCollection(storage, file);
    today = LocalDate.now();
  }

  @Test
  public void should_update_storage_and_save() {
    storage.setInstallDate(LocalDate.of(2000, 1, 1));
    storage.setLastUseDate(LocalDate.of(2000, 1, 1));

    dataCollection.analysisDone();

    assertThat(storage.installDate()).isEqualTo(LocalDate.of(2000, 1, 1));
    assertThat(storage.lastUseDate()).isEqualTo(today);
    assertThat(storage.numUseDays()).isEqualTo(1L);

    assertThat(file).exists();
  }

  @Test
  public void should_not_update_if_same_day() {
    storage.setInstallDate(LocalDate.of(2000, 1, 1));
    storage.setLastUseDate(today);

    dataCollection.analysisDone();

    assertThat(storage.installDate()).isEqualTo(LocalDate.of(2000, 1, 1));
    assertThat(storage.lastUseDate()).isEqualTo(today);
    assertThat(storage.numUseDays()).isEqualTo(0L);
  }

}
