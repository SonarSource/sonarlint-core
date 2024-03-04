/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.client.api.common;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleFileEvent;
import org.sonarsource.sonarlint.core.analysis.api.ClientModuleInfo;

/**
 * Entry point for SonarLint.
 */
public interface SonarLintEngine {

  /**
   * Get information about the analyzers that are currently loaded.
   * Should only be called when engine is started.
   */
  Collection<PluginDetails> getPluginDetails();

  CompletableFuture<Void> declareModule(ClientModuleInfo module);

  CompletableFuture<Void> stopModule(Object moduleKey);

  CompletableFuture<Void> fireModuleFileEvent(Object moduleKey, ClientModuleFileEvent event);
}
