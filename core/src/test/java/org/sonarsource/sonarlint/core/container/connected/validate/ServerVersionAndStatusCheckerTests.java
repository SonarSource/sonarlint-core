/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected.validate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ServerVersionAndStatusCheckerTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();
  private ServerVersionAndStatusChecker underTest;

  @BeforeEach
  public void setUp() {
    underTest = new ServerVersionAndStatusChecker(mockServer.serverApiHelper());
  }

  @Test
  void serverNotReady() throws ExecutionException, InterruptedException {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.5-SNAPSHOT\",\"status\": \"DOWN\"}");

    CompletableFuture<ValidationResult> futureResult = underTest.validateStatusAndVersion();

    ValidationResult validationResult = futureResult.get();
    assertThat(validationResult.success()).isFalse();
    assertThat(validationResult.message()).isEqualTo("Server not ready (DOWN)");
  }

  @Test
  void failWhenServerNotReady() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.5-SNAPSHOT\",\"status\": \"DOWN\"}");

    Throwable throwable = catchThrowable(() -> underTest.checkVersionAndStatus());

    assertThat(throwable).hasMessage("Server not ready (DOWN)");
  }

  @Test
  void incompatibleVersion() throws ExecutionException, InterruptedException {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"6.7\",\"status\": \"UP\"}");

    CompletableFuture<ValidationResult> futureResult = underTest.validateStatusAndVersion();

    ValidationResult validationResult = futureResult.get();
    assertThat(validationResult.success()).isFalse();
    assertThat(validationResult.message()).isEqualTo("SonarQube server has version 6.7. Version should be greater or equal to 7.9");
  }

  @Test
  void failWhenIncompatibleVersion() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"6.7\",\"status\": \"UP\"}");

    Throwable throwable = catchThrowable(() -> underTest.checkVersionAndStatus());

    assertThat(throwable).hasMessage("SonarQube server has version 6.7. Version should be greater or equal to 7.9");
  }

  @Test
  void responseParsingError() {
    mockServer.addStringResponse("/api/system/status", "bla bla");

    Throwable throwable = catchThrowable(() -> underTest.checkVersionAndStatus());

    assertThat(throwable).hasMessage("Unable to parse server infos from: bla bla");
  }

}
