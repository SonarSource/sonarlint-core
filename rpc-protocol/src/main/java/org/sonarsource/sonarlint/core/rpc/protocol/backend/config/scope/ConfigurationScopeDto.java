/*
 * SonarLint Core - RPC Protocol
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
  private final boolean setFocusOnNewCode;

  /**
   * @deprecated use {@link #builder()} instead
   */
  @Deprecated(since="10.3", forRemoval=true)
  public ConfigurationScopeDto(String id, @Nullable String parentId, boolean bindable, String name, @Nullable BindingConfigurationDto binding) {
    this(id, parentId, bindable, name, binding, false);
  }

  private ConfigurationScopeDto(String id, @Nullable String parentId, boolean bindable, String name, @Nullable BindingConfigurationDto binding, boolean setFocusOnNewCode) {
    this.id = id;
    this.parentId = parentId;
    this.bindable = bindable;
    this.name = name;
    this.binding = binding;
    this.setFocusOnNewCode = setFocusOnNewCode;
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

  public boolean isSetFocusOnNewCode() {
    return setFocusOnNewCode;
  }

  public static ConfigurationScopeDtoBuilder builder() {
    return new ConfigurationScopeDtoBuilder();
  }

  public static class ConfigurationScopeDtoBuilder {
    private String id;
    private String parentId;
    private boolean bindable;
    private String name;
    private BindingConfigurationDto binding;
    private boolean setFocusOnNewCode = false;

    private ConfigurationScopeDtoBuilder() {
      // restricted initialization
    }

    public ConfigurationScopeDtoBuilder setId(String id) {
      this.id = id;
      return this;
    }

    public ConfigurationScopeDtoBuilder setParentId(String parentId) {
      this.parentId = parentId;
      return this;
    }

    public ConfigurationScopeDtoBuilder setBindable(boolean bindable) {
      this.bindable = bindable;
      return this;
    }

    public ConfigurationScopeDtoBuilder setName(String name) {
      this.name = name;
      return this;
    }

    public ConfigurationScopeDtoBuilder setBinding(BindingConfigurationDto binding) {
      this.binding = binding;
      return this;
    }

    public ConfigurationScopeDtoBuilder setSetFocusOnNewCode(boolean setFocusOnNewCode) {
      this.setFocusOnNewCode = setFocusOnNewCode;
      return this;
    }

    public ConfigurationScopeDto build() {
      return new ConfigurationScopeDto(id, parentId, bindable, name, binding, setFocusOnNewCode);
    }
  }
}
