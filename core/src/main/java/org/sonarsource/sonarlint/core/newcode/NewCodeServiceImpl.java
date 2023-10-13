/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.newcode;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.GetNewCodeDefinitionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.GetNewCodeDefinitionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.newcode.NewCodeService;
import org.sonarsource.sonarlint.core.commons.NewCodeDefinition;
import org.sonarsource.sonarlint.core.repository.config.ConfigurationRepository;
import org.sonarsource.sonarlint.core.storage.StorageService;
import org.sonarsource.sonarlint.core.telemetry.TelemetryServiceImpl;

@Named
@Singleton
public class NewCodeServiceImpl implements NewCodeService {
  private final ConfigurationRepository configurationRepository;
  private final StorageService storageService;
  private final TelemetryServiceImpl telemetryService;

  public NewCodeServiceImpl(ConfigurationRepository configurationRepository, StorageService storageService, TelemetryServiceImpl telemetryService) {
    this.configurationRepository = configurationRepository;
    this.storageService = storageService;
    this.telemetryService = telemetryService;
  }

  @Override
  public CompletableFuture<GetNewCodeDefinitionResponse> getNewCodeDefinition(GetNewCodeDefinitionParams params) {
    return CompletableFutures.computeAsync(cancelChecker -> {
      var configScopeId = params.getConfigScopeId();
      return getFullNewCodeDefinition(configScopeId)
        .map(newCodeDefinition -> new GetNewCodeDefinitionResponse(newCodeDefinition.toString(), newCodeDefinition.isSupported()))
        .orElse(new GetNewCodeDefinitionResponse("No new code definition found", false));
    });
  }

  public Optional<NewCodeDefinition> getFullNewCodeDefinition(String configScopeId) {
    var effectiveBinding = configurationRepository.getEffectiveBinding(configScopeId);
    if (effectiveBinding.isEmpty()) return Optional.empty();
    var binding = effectiveBinding.get();
    var sonarProjectStorage = storageService.binding(binding);
    return sonarProjectStorage.newCodeDefinition().read();
  }

  @Override
  public void didToggleFocus() {
    telemetryService.newCodeFocusChanged();
  }
}
