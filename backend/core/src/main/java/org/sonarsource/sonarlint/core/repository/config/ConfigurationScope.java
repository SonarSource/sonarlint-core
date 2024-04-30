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
package org.sonarsource.sonarlint.core.repository.config;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class ConfigurationScope {

  private final String id;
  private final String parentId;
  private final boolean bindable;
  /**
   * The name of this configuration scope. Used for auto-binding.
   */
  private final String name;
  private boolean setFocusOnNewCode;

  /**
   * @deprecated use {@link #builder()} instead
   * it is going to be private
   */
  @Deprecated(since="10.3")
  public ConfigurationScope(String id, @Nullable String parentId, boolean bindable, String name, boolean setFocusOnNewCode) {
    this.id = id;
    this.parentId = parentId;
    this.bindable = bindable;
    this.name = name;
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

  public void setSetFocusOnNewCode(boolean setFocusOnNewCode) {
    this.setFocusOnNewCode = setFocusOnNewCode;
  }

  public boolean isSetFocusOnNewCode() {
    return setFocusOnNewCode;
  }

  public static ConfigurationScopeBuilder builder() {
    return new ConfigurationScopeBuilder();
  }

  public static class ConfigurationScopeBuilder {
    private String id;
    private String parentId;
    private boolean bindable;
    private String name;
    private boolean setFocusOnNewCode;

    private ConfigurationScopeBuilder() {
      // restricted initialization
    }

    public ConfigurationScopeBuilder setId(String id) {
      this.id = id;
      return this;
    }

    public ConfigurationScopeBuilder setParentId(@Nullable String parentId) {
      this.parentId = parentId;
      return this;
    }

    public ConfigurationScopeBuilder setBindable(boolean bindable) {
      this.bindable = bindable;
      return this;
    }

    public ConfigurationScopeBuilder setName(String name) {
      this.name = name;
      return this;
    }

    public ConfigurationScopeBuilder setSetFocusOnNewCode(boolean setFocusOnNewCode) {
      this.setFocusOnNewCode = setFocusOnNewCode;
      return this;
    }

    public ConfigurationScope build() {
      return new ConfigurationScope(id, parentId, bindable, name, setFocusOnNewCode);
    }
  }
}
