/*
 * SonarLint Core - ITs - Tests
 * Copyright (C) 2016-2022 SonarSource SA
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
package com.sonar.orchestrator;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.config.Configuration;
import com.sonar.orchestrator.container.SonarDistribution;
import com.sonar.orchestrator.server.StartupLogWatcher;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class OrchestratorExtension extends Orchestrator implements BeforeAllCallback, AfterAllCallback {

  private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(Orchestrator.class);

  OrchestratorExtension(Configuration config, SonarDistribution distribution, @Nullable StartupLogWatcher startupLogWatcher) {
    super(config, distribution, startupLogWatcher);
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    // This is to avoid multiple starts when using nested tests
    // See https://github.com/junit-team/junit5/issues/2421
    if (context.getStore(NAMESPACE).getOrComputeIfAbsent(AtomicInteger.class).getAndIncrement() == 0) {
      start();
    }
  }
  @Override
  public void afterAll(ExtensionContext context) {
    if (context.getStore(NAMESPACE).getOrComputeIfAbsent(AtomicInteger.class).decrementAndGet() == 0) {
      stop();
    }
  }

}
