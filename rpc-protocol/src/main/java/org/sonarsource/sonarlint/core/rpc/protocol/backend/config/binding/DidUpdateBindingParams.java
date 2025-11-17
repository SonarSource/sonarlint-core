/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AcceptedBindingSuggestionParams;

public class DidUpdateBindingParams {

  private final String configScopeId;
  private final BindingConfigurationDto updatedBinding;
  /**
   * @deprecated As it's hard to obtain for some IDEs on this event.
   */
  @Deprecated(since = "10.37", forRemoval = true)
  @Nullable
  private final BindingMode bindingMode;
  /**
   * @deprecated As it's hard to obtain for some IDEs on this event.
   */
  @Deprecated(since = "10.37", forRemoval = true)
  @Nullable
  private final BindingSuggestionOrigin origin;


  /**
   * @deprecated avoid calling this constructor if possible, since it will be removed once all the clients are migrated.
   * Rely on the constructor without origin and bindingMode params instead as not IDEs can get this info at this point.
   * For manual addition use {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService#addedManualBindings()}
   * For accepted suggestion use {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService#acceptedBindingSuggestion(AcceptedBindingSuggestionParams)}
   */
  @Deprecated(since = "10.37", forRemoval = true)
  public DidUpdateBindingParams(String configScopeId, BindingConfigurationDto updatedBinding, BindingMode bindingMode, @Nullable BindingSuggestionOrigin origin) {
    this.configScopeId = configScopeId;
    this.updatedBinding = updatedBinding;
    this.bindingMode = bindingMode;
    this.origin = origin;
  }

  public DidUpdateBindingParams(String configScopeId, BindingConfigurationDto updatedBinding) {
    this.configScopeId = configScopeId;
    this.updatedBinding = updatedBinding;
    this.bindingMode = null;
    this.origin = null;
  }


  public BindingMode getBindingMode() {
    return bindingMode;
  }

  public BindingSuggestionOrigin getOrigin() {
    return origin;
  }

  public String getConfigScopeId() {
    return configScopeId;
  }

  public BindingConfigurationDto getUpdatedBinding() {
    return updatedBinding;
  }
}
