/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package mediumtest;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTest;
import org.sonarsource.sonarlint.core.test.utils.junit5.SonarLintTestHarness;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

class InitializationMediumTests {

  @SonarLintTest
  void it_should_fail_to_initialize_the_backend_twice(SonarLintTestHarness harness) {
    var backend = harness.newBackend()
      .start();
    var telemetryInitDto = new TelemetryClientConstantAttributesDto("mediumTests", "mediumTests", "1.2.3", "4.5.6", emptyMap());
    var future = backend
      .initialize(new InitializeParams(new ClientConstantInfoDto("name", "productKey"), telemetryInitDto,
        HttpConfigurationDto.defaultConfig(), null, Set.of(),
        Path.of("unused"), Path.of("unused"),
        emptySet(), emptyMap(), emptySet(), emptySet(), emptySet(),
        emptyList(), emptyList(), "home", emptyMap(), false, null, false, null));

    assertThat(future)
      .failsWithin(Duration.ofSeconds(1))
      .withThrowableOfType(ExecutionException.class)
      .havingCause()
      .isInstanceOf(ResponseErrorException.class)
      .withMessage("Backend already initialized");
  }

}
