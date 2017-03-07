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
import java.time.LocalDateTime;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TelemetryClientTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private TelemetryClient client;
  private TelemetryStorage storage;
  private Path file;

  @Before
  public void setUp() {
    storage = new TelemetryStorage();
    file = temp.getRoot().toPath().resolve("file");
    client = new TelemetryClient("product", "version", storage, file);
  }

  @Test
  public void getters() {
    assertThat(client.version()).isEqualTo("version");
    assertThat(client.product()).isEqualTo("product");
  }

  @Test
  public void should_upload() {
    LocalDateTime fourHoursAgo = LocalDateTime.now().minusHours(4);
    storage.setLastUploadTime(fourHoursAgo);
    assertThat(client.shouldUpload()).isFalse();

    LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(24);
    storage.setLastUploadTime(oneDayAgo);
    assertThat(client.shouldUpload()).isTrue();

    storage.setEnabled(false);
    assertThat(client.shouldUpload()).isFalse();
  }

}
