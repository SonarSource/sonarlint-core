/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection;

import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import testutils.MockWebServerExtensionWithProtobuf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ServerVersionAndStatusCheckerTests {

  @RegisterExtension
  static MockWebServerExtensionWithProtobuf mockServer = new MockWebServerExtensionWithProtobuf();
  private ServerVersionAndStatusChecker underTest;

  @BeforeEach
  void setUp() {
    underTest = new ServerVersionAndStatusChecker(new ServerApi(mockServer.serverApiHelper()));
  }

  @Test
  void serverNotReady() throws ExecutionException, InterruptedException {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.5-SNAPSHOT\",\"status\": \"DOWN\"}");

    var futureResult = underTest.validateStatusAndVersion();

    var validationResult = futureResult.get();
    assertThat(validationResult.success()).isFalse();
    assertThat(validationResult.message()).isEqualTo("Server not ready (DOWN)");
  }

  @Test
  void failWhenServerNotReady() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.5-SNAPSHOT\",\"status\": \"DOWN\"}");

    var throwable = catchThrowable(() -> underTest.checkVersionAndStatus());

    assertThat(throwable).hasMessage("Server not ready (DOWN)");
  }

  @Test
  void incompatibleVersion() throws ExecutionException, InterruptedException {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"6.7\",\"status\": \"UP\"}");

    var futureResult = underTest.validateStatusAndVersion();

    var validationResult = futureResult.get();
    assertThat(validationResult.success()).isFalse();
    assertThat(validationResult.message()).isEqualTo("SonarQube server has version 6.7. Version should be greater or equal to 7.9");
  }

  @Test
  void failWhenIncompatibleVersion() {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"6.7\",\"status\": \"UP\"}");

    var throwable = catchThrowable(() -> underTest.checkVersionAndStatus());

    assertThat(throwable).hasMessage("SonarQube server has version 6.7. Version should be greater or equal to 7.9");
  }

  @Test
  void responseParsingError() {
    mockServer.addStringResponse("/api/system/status", "bla bla");

    var throwable = catchThrowable(() -> underTest.checkVersionAndStatus());

    assertThat(throwable).hasMessage("Unable to parse server infos from: bla bla");
  }

}
