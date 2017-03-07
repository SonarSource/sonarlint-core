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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import com.google.gson.JsonSyntaxException;

public class TelemetryTest {
  private Telemetry telemetry;
  private Path filePath;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    filePath = temp.getRoot().toPath().resolve("file");
    telemetry = new Telemetry(filePath);
  }

  @Test
  public void should_cache_objects() {
    assertThat(telemetry.getClient("product", "version")).isNotNull();
    assertThat(telemetry.getDataCollection()).isNotNull();

    assertThat(telemetry.getClient("product", "version") == telemetry.getClient("product", "version")).isTrue();
    assertThat(telemetry.getDataCollection() == telemetry.getDataCollection()).isTrue();
  }

  @Test
  public void should_save() throws Exception {
    telemetry.enable(false);
    telemetry.save();
    telemetry = new Telemetry(filePath);
    assertThat(telemetry.enabled()).isFalse();
  }

  @Test
  public void should_throw_exception_if_invalid_file() throws Exception {
    Files.write(filePath, "trash".getBytes(StandardCharsets.UTF_8));

    exception.expect(JsonSyntaxException.class);
    telemetry = new Telemetry(filePath);
  }
}
