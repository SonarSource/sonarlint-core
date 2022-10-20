/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core;

import com.google.common.eventbus.Subscribe;
import org.sonarsource.sonarlint.core.event.BindingConfigChangedEvent;
import org.sonarsource.sonarlint.core.event.ConfigurationScopeAddedEvent;
import org.sonarsource.sonarlint.core.event.ConnectionAddedEvent;

public class AutoBinding {

  public AutoBinding() {
  }

  @Subscribe
  public void bindingConfigChanged(BindingConfigChangedEvent event) {
    // Check if autobind is switched on
    if (event.getNewConfig().isAutoBindEnabled() && !event.getPreviousConfig().isAutoBindEnabled()) {
      autoBindConfigScope(event.getNewConfig().getConfigScopeId());
    }
  }

  @Subscribe
  public void configurationScopeAdded(ConfigurationScopeAddedEvent event) {

  }

  @Subscribe
  public void connectionAdded(ConnectionAddedEvent event) {

  }

  private void autoBindConfigScope(String configScopeId) {
  }

}
