/*
 * SonarLint Core - Medium Tests
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
package mediumtest.newcode;

import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestBackend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.assertj.core.api.Assertions.assertThat;

class NewCodeTelemetryMediumTests {

  private SonarLintTestBackend backend;

  @AfterEach
  void tearDown() throws ExecutionException, InterruptedException {
    backend.shutdown().get();
  }

  @Test
  void it_should_save_initial_value_when_focus_on_overall_code() {
    backend = newBackend().build();

    assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"isFocusOnNewCode\":false,\"codeFocusChangedCount\":0");
  }

  @Test
  void it_should_save_initial_value_when_focus_on_new_code() {
    backend = newBackend().withFocusOnNewCode().build();

    assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"isFocusOnNewCode\":true,\"codeFocusChangedCount\":0");
  }

  @Test
  void it_should_save_new_focus_and_increment_count_when_focusing_on_new_code() {
    backend = newBackend().build();

    backend.getNewCodeService().didToggleFocus();

    assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"isFocusOnNewCode\":true,\"codeFocusChangedCount\":1");
  }

  @Test
  void it_should_save_new_focus_and_increment_count_when_focusing_on_overall_code() {
    backend = newBackend().withFocusOnNewCode().build();

    backend.getNewCodeService().didToggleFocus();

    assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"isFocusOnNewCode\":false,\"codeFocusChangedCount\":1");
  }
}
