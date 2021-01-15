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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.MockWebServerExtension;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerVersionAndStatusCheckerTests {

  @RegisterExtension
  static MockWebServerExtension mockServer = new MockWebServerExtension();
  private ServerVersionAndStatusChecker underTest;

  @BeforeEach
  public void setUp() {
    underTest = new ServerVersionAndStatusChecker(mockServer.slClient());
  }

  @Test
  void serverNotReady() throws Exception {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.5-SNAPSHOT\",\"status\": \"DOWN\"}");

    ValidationResult validateStatusAndVersion = underTest.validateStatusAndVersion();

    assertThat(validateStatusAndVersion.success()).isFalse();
    assertThat(validateStatusAndVersion.message()).isEqualTo("Server not ready (DOWN)");
  }

  @Test
  void failWhenServerNotReady() throws Exception {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.5-SNAPSHOT\",\"status\": \"DOWN\"}");

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.checkVersionAndStatus());
    assertThat(thrown).hasMessage("Server not ready (DOWN)");
  }

  @Test
  void incompatibleVersion() throws Exception {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"4.5\",\"status\": \"UP\"}");

    ValidationResult validateStatusAndVersion = underTest.validateStatusAndVersion();

    assertThat(validateStatusAndVersion.success()).isFalse();
    assertThat(validateStatusAndVersion.message()).isEqualTo("SonarQube server has version 4.5. Version should be greater or equal to 6.7");
  }

  @Test
  void failWhenIncompatibleVersion() throws Exception {
    mockServer.addStringResponse("/api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.6\",\"status\": \"UP\"}");

    UnsupportedServerException thrown = assertThrows(UnsupportedServerException.class, () -> underTest.checkVersionAndStatus());
    assertThat(thrown).hasMessage("SonarQube server has version 5.6. Version should be greater or equal to 6.7");
  }

  @Test
  void responseParsingError() throws Exception {
    mockServer.addStringResponse("/api/system/status", "bla bla");

    IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> underTest.checkVersionAndStatus());
    assertThat(thrown).hasMessage("Unable to parse server infos from: bla bla");
  }

}
