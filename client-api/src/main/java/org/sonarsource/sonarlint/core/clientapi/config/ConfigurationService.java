/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.config;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.sonarsource.sonarlint.core.clientapi.config.binding.DidUpdateBindingParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidAddConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.DidRemoveConfigurationScopeParams;
import org.sonarsource.sonarlint.core.clientapi.config.scope.InitializeParams;

/**
 * The client is the source of truth for the configuration, but the backend needs to be kept in sync.
 */
public interface ConfigurationService {

  /**
   * Called by client in order to initialize the configuration service at startup.
   */
  @JsonRequest
  CompletableFuture<Void> initialize(InitializeParams params);

  /**
   * Called by the client when a configuration scope has been added.
   */
  @JsonNotification
  void didAddConfigurationScope(DidAddConfigurationScopeParams params);

  /**
   * Called by the client when a configuration scope has been removed.
   */
  @JsonNotification
  void didRemoveConfigurationScope(DidRemoveConfigurationScopeParams params);


  /**
   * Called by the client when the binding configuration has been updated on an existing configuration scope.
   */
  @JsonNotification
  void didUpdateBinding(DidUpdateBindingParams params);
}
