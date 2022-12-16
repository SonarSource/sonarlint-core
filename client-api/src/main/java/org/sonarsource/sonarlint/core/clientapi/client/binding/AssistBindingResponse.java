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
package org.sonarsource.sonarlint.core.clientapi.client.binding;

import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.clientapi.backend.connection.config.SonarQubeConnectionConfigurationDto;

public class AssistBindingResponse {
  private final Either<SonarQubeConnectionConfigurationDto, SonarCloudConnectionConfigurationDto> newConnection;
  private final BindingConfigurationDto bindingConfiguration;
  private final String configurationScopeId;

  public AssistBindingResponse(@Nullable Either<SonarQubeConnectionConfigurationDto, SonarCloudConnectionConfigurationDto> newConnection,
    @NonNull BindingConfigurationDto bindingConfiguration, @NonNull String configurationScopeId) {
    this.newConnection = newConnection;
    this.bindingConfiguration = bindingConfiguration;
    this.configurationScopeId = configurationScopeId;
  }

  @Nullable
  public Either<SonarQubeConnectionConfigurationDto, SonarCloudConnectionConfigurationDto> getNewConnection() {
    return newConnection;
  }

  @NonNull
  public BindingConfigurationDto getBindingConfiguration() {
    return bindingConfiguration;
  }

  @NonNull
  public String getConfigurationScopeId() {
    return configurationScopeId;
  }
}
