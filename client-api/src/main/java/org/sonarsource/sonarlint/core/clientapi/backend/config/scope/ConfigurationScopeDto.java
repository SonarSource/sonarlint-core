/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.clientapi.backend.config.scope;

import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingConfigurationDto;

public class ConfigurationScopeDto {

  @NonNull
  private final String id;
  private final String parentId;
  private final boolean bindable;
  /**
   * The name of this configuration scope. Used for auto-binding.
   */
  @NonNull
  private final String name;
  private final BindingConfigurationDto binding;

  public ConfigurationScopeDto(@NonNull String id, @Nullable String parentId, boolean bindable, @NonNull String name, @NonNull BindingConfigurationDto binding) {
    this.id = id;
    this.parentId = parentId;
    this.bindable = bindable;
    this.name = name;
    this.binding = binding;
  }

  @NonNull
  public String getId() {
    return id;
  }

  public String getParentId() {
    return parentId;
  }

  public boolean isBindable() {
    return bindable;
  }

  @NonNull
  public String getName() {
    return name;
  }

  public BindingConfigurationDto getBinding() {
    return binding;
  }
}
