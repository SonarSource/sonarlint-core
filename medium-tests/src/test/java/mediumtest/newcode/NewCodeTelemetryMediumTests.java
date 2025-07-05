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

import org.sonarsource.sonarlint.core.telemetry.TelemetryLocalStorage;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class NewCodeTelemetryMediumTests {

  @SonarLintTest
  void it_should_save_initial_value_when_focus_on_overall_code(SonarLintTestHarness harness) {
    var backend = harness.newBackend().withTelemetryEnabled().start();

    assertThat(backend.telemetryFileContent())
      .extracting(TelemetryLocalStorage::isFocusOnNewCode, TelemetryLocalStorage::getCodeFocusChangedCount)
      .containsExactly(false, 0);
  }

  @SonarLintTest
  void it_should_save_initial_value_when_focus_on_new_code(SonarLintTestHarness harness) {
    var backend = harness.newBackend().withTelemetryEnabled().withFocusOnNewCode().start();

    assertThat(backend.telemetryFileContent())
      .extracting(TelemetryLocalStorage::isFocusOnNewCode, TelemetryLocalStorage::getCodeFocusChangedCount)
      .containsExactly(true, 0);
  }

  @SonarLintTest
  void it_should_save_new_focus_and_increment_count_when_focusing_on_new_code(SonarLintTestHarness harness) {
    var backend = harness.newBackend().withTelemetryEnabled().start();

    backend.getNewCodeService().didToggleFocus();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent())
      .extracting(TelemetryLocalStorage::isFocusOnNewCode, TelemetryLocalStorage::getCodeFocusChangedCount)
      .containsExactly(true, 1));
  }

  @SonarLintTest
  void it_should_save_new_focus_and_increment_count_when_focusing_on_overall_code(SonarLintTestHarness harness) {
    var backend = harness.newBackend().withTelemetryEnabled().withFocusOnNewCode().start();

    backend.getNewCodeService().didToggleFocus();

    await().untilAsserted(() -> assertThat(backend.telemetryFileContent())
      .extracting(TelemetryLocalStorage::isFocusOnNewCode, TelemetryLocalStorage::getCodeFocusChangedCount)
      .containsExactly(false, 1));
  }
}
