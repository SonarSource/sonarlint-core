/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarlint.core.clientapi.SonarLintBackend;
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient;
import org.sonarsource.sonarlint.core.clientapi.config.ConfigurationService;
import org.sonarsource.sonarlint.core.clientapi.connection.ConnectionService;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.repository.connection.ConnectionConfigurationRepository;

public class SonarLintBackendImpl implements SonarLintBackend {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final ConfigurationServiceImpl configurationService;
  private final ConnectionServiceImpl connectionService;

  private final EventBus clientEventBus;
  private final ExecutorService clientEventsExecutorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "SonarLint Client Events Processor"));
  private final ConnectionConfigurationRepository connectionConfigurationRepository = new ConnectionConfigurationRepository();
  private final ConfigurationRepository configurationRepository = new ConfigurationRepository();
  private final SonarLintClient client;

  public SonarLintBackendImpl(SonarLintClient client) {
    this.client = client;
    this.clientEventBus = new AsyncEventBus("clientEvents", clientEventsExecutorService);
    this.configurationService = new ConfigurationServiceImpl(clientEventBus, configurationRepository);
    this.connectionService = new ConnectionServiceImpl(clientEventBus, connectionConfigurationRepository);
    var autoBinding = new BindingSuggestionProvider(configurationRepository, connectionConfigurationRepository, client);
    clientEventBus.register(autoBinding);
  }

  @Override
  public ConnectionService getConnectionService() {
    return connectionService;
  }

  @Override
  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  @Override
  public CompletableFuture<Void> shutdown() {
    return CompletableFuture.runAsync(() -> {
      clientEventsExecutorService.shutdown();
      try {
        boolean success = clientEventsExecutorService.awaitTermination(10, TimeUnit.SECONDS);
        if (!success) {
          LOG.error("Unable to terminate clientEventsExecutorService in time");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    });
  }
}
