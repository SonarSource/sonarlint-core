/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;

public class ConfigurationScopeDto {

  private final String id;
  private final String parentId;
  private final boolean bindable;
  /**
   * The name of this configuration scope. Used for auto-binding.
   */
  private final String name;
  private final BindingConfigurationDto binding;

  public ConfigurationScopeDto(String id, @Nullable String parentId, boolean bindable, String name, @Nullable BindingConfigurationDto binding) {
    this.id = id;
    this.parentId = parentId;
    this.bindable = bindable;
    this.name = name;
    this.binding = binding;
  }

  public String getId() {
    return id;
  }

  @CheckForNull
  public String getParentId() {
    return parentId;
  }

  public boolean isBindable() {
    return bindable;
  }

  public String getName() {
    return name;
  }

  @CheckForNull
  public BindingConfigurationDto getBinding() {
    return binding;
  }
}
