/*
 * SonarLint Core - Test Utils
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
package org.sonarsource.sonarlint.core.test.utils.junit5;

import java.util.ArrayList;
import java.util.List;
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
    backends.forEach(backend -> failsafe(() -> backend.shutdown().join()));
    backends.clear();
    servers.forEach(server -> failsafe(server::shutdown));
    servers.clear();
  }

  private static void failsafe(Runnable runnable) {
    try {
      runnable.run();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public SonarLintBackendFixture.SonarLintBackendBuilder newBackend() {
    return SonarLintBackendFixture.newBackend(backends::add);
  }

  public SonarLintBackendFixture.SonarLintClientBuilder newFakeClient() {
    return SonarLintBackendFixture.newFakeClient();
  }

  public ServerFixture.ServerBuilder newFakeSonarQubeServer() {
    return ServerFixture.newSonarQubeServer(servers::add);
  }

  public ServerFixture.ServerBuilder newFakeSonarQubeServer(String version) {
    return ServerFixture.newSonarQubeServer(servers::add, version);
  }

  public ServerFixture.ServerBuilder newFakeSonarCloudServer() {
    return ServerFixture.newSonarCloudServer(servers::add);
  }

  public ServerFixture.ServerBuilder newFakeSonarCloudServer(String organizationKey) {
    return ServerFixture.newSonarCloudServer(servers::add, organizationKey);
  }
}
