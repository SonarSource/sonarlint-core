/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.container.connected.rule;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import java.util.List;
import org.sonarsource.sonarlint.core.plugin.PluginIndexProvider;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences;

/**
 * List of plugins is in local storage
 */
public class TemporaryPluginIndexProvider implements PluginIndexProvider {

  private final PluginReferences pluginReferences;

  public TemporaryPluginIndexProvider(PluginReferences pluginReferences) {
    this.pluginReferences = pluginReferences;
  }

  @Override
  public List<PluginReference> references() {
    return Lists.transform(pluginReferences.getReferenceList(), new Function<org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference, PluginReference>() {
      @Override
      public PluginReference apply(org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference input) {
        return new PluginReference().setHash(input.getHash()).setFilename(input.getFilename());
      }
    });
  }
}
