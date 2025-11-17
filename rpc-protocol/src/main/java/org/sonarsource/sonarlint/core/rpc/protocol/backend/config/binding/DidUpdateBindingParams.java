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

public class DidUpdateBindingParams {

  private final String configScopeId;
  private final BindingConfigurationDto updatedBinding;
  /**
   * Temporarily nullable till all the clients are migrated to use the new constructor.
   */
  @Nullable
  private final BindingMode bindingMode;
  /**
   * This value should only be set if the bindingMode is:
   * {@link org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingMode#FROM_SUGGESTION}
   */
  @Nullable
  private final BindingSuggestionOrigin origin;


  public DidUpdateBindingParams(String configScopeId, BindingConfigurationDto updatedBinding, BindingMode bindingMode, @Nullable BindingSuggestionOrigin origin) {
    this.configScopeId = configScopeId;
    this.updatedBinding = updatedBinding;
    this.bindingMode = bindingMode;
    this.origin = origin;
  }

  /**
   * @deprecated avoid calling this constructor if possible, since it will be removed once all the clients are migrated.
   * Rely on the constructor with origin and bindingMode params instead.
   */
  @Deprecated(forRemoval = true)
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
