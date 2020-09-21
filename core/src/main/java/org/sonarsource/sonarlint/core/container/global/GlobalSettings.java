/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.global;

import java.nio.file.Path;
import org.sonar.api.Startable;
import org.sonar.api.config.PropertyDefinitions;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;

public class GlobalSettings extends MapSettings implements Startable {

  private static final String NODE_EXECUTABLE_PROPERTY = "sonar.nodejs.executable";
  private final NodeJsHelper nodeJsHelper;

  public GlobalSettings(AbstractGlobalConfiguration config, PropertyDefinitions propertyDefinitions, NodeJsHelper nodeJsHelper) {
    super(propertyDefinitions);
    this.nodeJsHelper = nodeJsHelper;
    addProperties(config.extraProperties());
  }

  @Override
  public void start() {
    Path nodejsPath = nodeJsHelper.getNodeJsPath();
    if (nodejsPath != null) {
      setProperty(NODE_EXECUTABLE_PROPERTY, nodejsPath.toString());
    }
  }

  @Override
  public void stop() {
    // Nothing to do
  }

}
