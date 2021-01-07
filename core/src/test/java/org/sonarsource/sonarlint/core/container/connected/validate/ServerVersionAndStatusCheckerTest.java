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

import org.junit.Test;
import org.sonarsource.sonarlint.core.WsClientTestUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ServerVersionAndStatusCheckerTest {

  @Test
  public void serverNotReady() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.5-SNAPSHOT\",\"status\": \"DOWN\"}");

    ServerVersionAndStatusChecker checker = new ServerVersionAndStatusChecker(wsClient);

    ValidationResult validateStatusAndVersion = checker.validateStatusAndVersion();
    assertThat(validateStatusAndVersion.success()).isFalse();
    assertThat(validateStatusAndVersion.message()).isEqualTo("Server not ready (DOWN)");
  }

  @Test
  public void failWhenServerNotReady() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.5-SNAPSHOT\",\"status\": \"DOWN\"}");

    ServerVersionAndStatusChecker checker = new ServerVersionAndStatusChecker(wsClient);

    try {
      checker.checkVersionAndStatus();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("Server not ready (DOWN)");
    }
  }

  @Test
  public void incompatibleVersion() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("api/system/status", "{\"id\": \"20160308094653\",\"version\": \"4.5\",\"status\": \"UP\"}");

    ServerVersionAndStatusChecker checker = new ServerVersionAndStatusChecker(wsClient);

    ValidationResult validateStatusAndVersion = checker.validateStatusAndVersion();
    assertThat(validateStatusAndVersion.success()).isFalse();
    assertThat(validateStatusAndVersion.message()).isEqualTo("SonarQube server has version 4.5. Version should be greater or equal to 6.7");
  }

  @Test
  public void failWhenIncompatibleVersion() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("api/system/status", "{\"id\": \"20160308094653\",\"version\": \"5.6\",\"status\": \"UP\"}");

    ServerVersionAndStatusChecker checker = new ServerVersionAndStatusChecker(wsClient);

    try {
      checker.checkVersionAndStatus();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).isExactlyInstanceOf(UnsupportedServerException.class).hasMessage("SonarQube server has version 5.6. Version should be greater or equal to 6.7");
    }
  }

  @Test
  public void responseParsingError() throws Exception {
    SonarLintWsClient wsClient = WsClientTestUtils.createMockWithResponse("api/system/status", "bla bla");

    ServerVersionAndStatusChecker checker = new ServerVersionAndStatusChecker(wsClient);

    try {
      checker.checkVersionAndStatus();
      fail("Expected exception");
    } catch (Exception e) {
      assertThat(e).hasMessage("Unable to parse server infos from: bla bla");
    }
  }

}
