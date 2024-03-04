/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.standalone;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.concurrent.Immutable;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;

/**
 * To use in standalone mode please provide list of plugin URLs.  
 */
@Immutable
public class StandaloneGlobalConfiguration extends AbstractGlobalConfiguration {

  private final List<URL> pluginUrls;

  private StandaloneGlobalConfiguration(Builder builder) {
    super(builder);
    this.pluginUrls = builder.pluginUrls;
  }

  public static Builder builder() {
    return new Builder();
  }

  public List<URL> getPluginUrls() {
    return Collections.unmodifiableList(pluginUrls);
  }

  public static final class Builder extends AbstractBuilder<Builder> {
    private final List<URL> pluginUrls = new ArrayList<>();

    private Builder() {
    }

    public Builder addPlugins(URL... pluginUrls) {
      Collections.addAll(this.pluginUrls, pluginUrls);
      return this;
    }

    public Builder addPlugin(URL pluginUrl) {
      this.pluginUrls.add(pluginUrl);
      return this;
    }

    public StandaloneGlobalConfiguration build() {
      return new StandaloneGlobalConfiguration(this);
    }
  }

}
