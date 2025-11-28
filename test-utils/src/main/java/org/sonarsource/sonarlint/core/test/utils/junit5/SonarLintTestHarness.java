/*
 * SonarLint Core - Test Utils
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
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.support.TypeBasedParameterResolver;
import org.sonarsource.sonarlint.core.test.utils.SonarLintBackendFixture;
import org.sonarsource.sonarlint.core.test.utils.SonarLintTestRpcServer;
import org.sonarsource.sonarlint.core.test.utils.server.ServerFixture;

public class SonarLintTestHarness extends TypeBasedParameterResolver<SonarLintTestHarness> implements BeforeAllCallback, AfterEachCallback, AfterAllCallback {
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
