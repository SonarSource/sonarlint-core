/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2024 SonarSource SA
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
package mediumtest.dogfooding;

import java.util.concurrent.ExecutionException;
import mediumtest.fixtures.SonarLintTestRpcServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static mediumtest.fixtures.SonarLintBackendFixture.newBackend;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sonarsource.sonarlint.core.commons.monitoring.DogfoodEnvironmentDetectionService.SONARSOURCE_DOGFOODING_ENV_VAR_KEY;

@ExtendWith(SystemStubsExtension.class)
class DogfoodingRpcServiceMediumTests {
  @SystemStub
  EnvironmentVariables environmentVariables;
  private SonarLintTestRpcServer backend;

  @AfterEach
  void stop() throws ExecutionException, InterruptedException {
    if (backend != null) {
      backend.shutdown().get();
    }
  }

  @Test
  void should_return_true_when_env_variable_is_set() {
    environmentVariables.set(SONARSOURCE_DOGFOODING_ENV_VAR_KEY, "1");

    backend = newBackend().build();
    var result = backend.getDogfoodingService().isDogfoodingEnvironment().join();
    assertTrue(result.isDogfoodingEnvironment());

    environmentVariables.remove(SONARSOURCE_DOGFOODING_ENV_VAR_KEY);
  }

  @Test
  void should_return_false_when_env_var_is_absent() {
    backend = newBackend().build();

    var result = backend.getDogfoodingService().isDogfoodingEnvironment().join();
    assertFalse(result.isDogfoodingEnvironment());
  }

  @Test
  void should_return_false_when_env_var_is_false() {
    backend = newBackend().build();

    environmentVariables.set(SONARSOURCE_DOGFOODING_ENV_VAR_KEY, "0");
    var result = backend.getDogfoodingService().isDogfoodingEnvironment().join();
    assertFalse(result.isDogfoodingEnvironment());

    environmentVariables.remove(SONARSOURCE_DOGFOODING_ENV_VAR_KEY);
  }
}
