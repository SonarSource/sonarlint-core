/*
 * SonarLint Core - Test Utils
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.test.utils.junit5;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.support.TypeBasedParameterResolver;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;

public class SonarLintTestHarness extends TypeBasedParameterResolver<SonarLintTestHarness>
  implements BeforeAllCallback, AfterTestExecutionCallback, AfterEachCallback, AfterAllCallback {
  private static final Logger LOG = Logger.getLogger(SonarLintTestHarness.class.getName());
  private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

  private final List<SonarLintTestRpcServer> backends = new ArrayList<>();
  private final List<ServerFixture.Server> servers = new ArrayList<>();
  private boolean isStatic;

  @Override
  public SonarLintTestHarness resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
    return this;
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    isStatic = true;
  }

  @Override
  public void afterAll(ExtensionContext context) {
    if (isStatic) {
      shutdownAll();
    }
  }

  @Override
  public void afterTestExecution(ExtensionContext context) {
    // Runs before afterEach (which shuts the backends down), so the captured client logs are still available.
    // On failure, dump them to stdout so they are retained in the Surefire report instead of being discarded.
    if (context.getExecutionException().isPresent()) {
      dumpBackendLogs(context);
    }
  }

  // Stdout is intentional here: on failure the captured backend logs must be retained in the Surefire
  // report (and the uploaded CI artifact). The test logback configuration defines no console appender,
  // so routing through a logger would silently drop them - hence the deliberate System.out use.
  @SuppressWarnings("java:S106")
  private void dumpBackendLogs(ExtensionContext context) {
    var testName = context.getDisplayName();
    for (var i = 0; i < backends.size(); i++) {
      var client = backends.get(i).getClient();
      if (client instanceof SonarLintBackendFixture.FakeSonarLintRpcClient fakeClient) {
        var dump = new StringBuilder("===== BACKEND LOGS (backend #" + i + ") for failed test: " + testName + " =====\n");
        fakeClient.getLogs().forEach(log -> dump.append(log).append('\n'));
        dump.append("===== END BACKEND LOGS (backend #").append(i).append(") =====");
        System.out.println(dump);
      }
    }
  }

  @Override
  public void afterEach(ExtensionContext context) {
    if (!isStatic) {
      shutdownAll();
    }
  }

  private void shutdownAll() {
    // Shutdown backends with timeout and exception handling
    for (SonarLintTestRpcServer backend : backends) {
      doShutdown(backend);
    }
    backends.clear();
    // Shutdown servers with exception handling
    for (ServerFixture.Server server : servers) {
      try {
        server.shutdown();
      } catch (Exception e) {
        // Log and continue with next server
        LOG.log(Level.WARNING, "Failed to shutdown server", e);
      }
    }
    servers.clear();
  }

  private static void doShutdown(SonarLintTestRpcServer backend) {
    try {
      CompletableFuture<Void> future = backend.shutdown();
      future.orTimeout(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .exceptionally(ex -> {
          LOG.log(Level.WARNING, "Error shutting down backend", ex);
          return null;
        })
        .join();
    } catch (CompletionException | IllegalStateException e) {
      // Log and continue with next backend
      LOG.log(Level.WARNING, "Failed to shutdown backend", e);
    }
  }

  public SonarLintBackendFixture.SonarLintBackendBuilder newBackend() {
    return SonarLintBackendFixture.newBackend(backends::add);
  }

  public SonarLintBackendFixture.SonarLintClientBuilder newFakeClient() {
    return SonarLintBackendFixture.newFakeClient();
  }

  public ServerFixture.SonarQubeServerBuilder newFakeSonarQubeServer() {
    return ServerFixture.newSonarQubeServer(servers::add);
  }

  public ServerFixture.SonarQubeServerBuilder newFakeSonarQubeServer(String version) {
    return ServerFixture.newSonarQubeServer(servers::add, version);
  }

  public ServerFixture.SonarQubeCloudBuilder newFakeSonarCloudServer() {
    return ServerFixture.newSonarCloudServer(servers::add);
  }

  public void addBackend(SonarLintTestRpcServer backend) {
    backends.add(backend);
  }

  public void addServer(ServerFixture.Server server) {
    servers.add(server);
  }

  public List<SonarLintTestRpcServer> getBackends() {
    return backends;
  }

  public List<ServerFixture.Server> getServers() {
    return servers;
  }

  public void shutdown(SonarLintTestRpcServer backend) {
    if (backends.remove(backend)) {
      doShutdown(backend);
    }
  }
}
