/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.SpringApplicationContextInitializer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.validate.ValidateConnectionParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class SonarLintBackendImplTests {

  @Test
  void it_should_fail_to_use_services_if_the_backend_is_not_initialized() {
    var in = new ByteArrayInputStream(new byte[0]);
    var out = new ByteArrayOutputStream();
    var backend = new SonarLintBackendImpl(in, out, Executors.newSingleThreadExecutor(), Executors.newSingleThreadExecutor());

    var error = catchThrowable(() -> backend.getTelemetryService().getStatus().get());

    assertThat(error)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Backend is not initialized");
  }

  @Test
  void it_should_silently_shutdown_the_backend_if_it_was_not_initialized() {
    var in = new ByteArrayInputStream(new byte[0]);
    var out = new ByteArrayOutputStream();
    var backend = new SonarLintBackendImpl(in, out, Executors.newSingleThreadExecutor(), Executors.newSingleThreadExecutor());

    var future = backend.shutdown();

    assertThat(future)
      .succeedsWithin(Duration.ofSeconds(1));
  }

}