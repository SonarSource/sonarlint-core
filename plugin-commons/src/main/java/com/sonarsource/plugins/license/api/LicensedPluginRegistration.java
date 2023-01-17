/*
 * SonarLint Core - Plugin Commons
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
package com.sonarsource.plugins.license.api;

public class LicensedPluginRegistration {

  private final String pluginKey;

  private LicensedPluginRegistration(Builder builder) {
    this.pluginKey = builder.pluginKey;
  }

  public String getPluginKey() {
    return pluginKey;
  }

  public static LicensedPluginRegistration forPlugin(String pluginKey) {
    return new Builder().setPluginKey(pluginKey).build();
  }

  public static final class Builder {
    private String pluginKey;

    public Builder setPluginKey(String s) {
      this.pluginKey = s;
      return this;
    }

    public LicensedPluginRegistration build() {
      return new LicensedPluginRegistration(this);
    }
  }
}
