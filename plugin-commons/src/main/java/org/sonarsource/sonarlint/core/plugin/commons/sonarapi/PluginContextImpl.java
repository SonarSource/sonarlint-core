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
package org.sonarsource.sonarlint.core.plugin.commons.sonarapi;

import java.util.Objects;
import org.sonar.api.Plugin;
import org.sonar.api.config.Configuration;
import org.sonarsource.sonarlint.plugin.api.SonarLintRuntime;

public class PluginContextImpl extends Plugin.Context {

  private final Configuration bootConfiguration;

  private PluginContextImpl(Builder builder) {
    super(builder.sonarRuntime);
    this.bootConfiguration = builder.bootConfiguration;
  }

  @Override
  public Configuration getBootConfiguration() {
    return bootConfiguration;
  }

  @Override
  public SonarLintRuntime getRuntime() {
    return (SonarLintRuntime) super.getRuntime();
  }

  public static class Builder {
    private SonarLintRuntime sonarRuntime;
    private Configuration bootConfiguration;

    /**
     * Required.
     * @see SonarLintRuntimeImpl
     * @return this
     */
    public Builder setSonarRuntime(SonarLintRuntime r) {
      this.sonarRuntime = r;
      return this;
    }

    /**
     * Required.
     * @return this
     */
    public Builder setBootConfiguration(Configuration c) {
      this.bootConfiguration = c;
      return this;
    }

    public Plugin.Context build() {
      Objects.requireNonNull(bootConfiguration, "bootConfiguration is mandatory");
      return new PluginContextImpl(this);
    }
  }
}
