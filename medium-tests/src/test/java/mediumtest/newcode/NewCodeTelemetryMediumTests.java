/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource SA
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

import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class NewCodeTelemetryMediumTests {

  @SonarLintTest
  void it_should_save_initial_value_when_focus_on_overall_code(SonarLintTestHarness harness) {
    var backend = harness.newBackend().withTelemetryEnabled().build();

    assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"isFocusOnNewCode\":false,\"codeFocusChangedCount\":0");
  }

  @SonarLintTest
  void it_should_save_initial_value_when_focus_on_new_code(SonarLintTestHarness harness) {
    var backend = harness.newBackend().withTelemetryEnabled().withFocusOnNewCode().build();

    assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"isFocusOnNewCode\":true,\"codeFocusChangedCount\":0");
  }

  @SonarLintTest
  void it_should_save_new_focus_and_increment_count_when_focusing_on_new_code(SonarLintTestHarness harness) {
    var backend = harness.newBackend().withTelemetryEnabled().build();

    backend.getNewCodeService().didToggleFocus();

    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"isFocusOnNewCode\":true,\"codeFocusChangedCount\":1"));
  }

  @SonarLintTest
  void it_should_save_new_focus_and_increment_count_when_focusing_on_overall_code(SonarLintTestHarness harness) {
    var backend = harness.newBackend().withTelemetryEnabled().withFocusOnNewCode().build();

    backend.getNewCodeService().didToggleFocus();

    await().untilAsserted(() -> assertThat(backend.telemetryFilePath()).content().asBase64Decoded().asString().contains("\"isFocusOnNewCode\":false,\"codeFocusChangedCount\":1"));
  }
}
