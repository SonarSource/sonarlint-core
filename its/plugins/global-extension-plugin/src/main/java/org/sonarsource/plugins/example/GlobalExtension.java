/*
 * Example Plugin with global extension
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarsource.plugins.example;

import org.sonar.api.Startable;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.api.sonarlint.SonarLintSide;

import static org.sonarsource.api.sonarlint.SonarLintSide.MULTIPLE_ANALYSES;

@SonarLintSide(lifespan = MULTIPLE_ANALYSES)
public class GlobalExtension implements Startable {

  private static final Logger LOG = Loggers.get(GlobalExtension.class);

  private int counter;

  private final Configuration config;

  public GlobalExtension(Configuration config) {
    this.config = config;
  }

  @Override
  public void start() {
    LOG.info("Start Global Extension " + config.get("sonar.global.label").orElse("MISSING"));
  }

  @Override
  public void stop() {
    LOG.info("Stop Global Extension");
  }

  public int getAndInc() {
    return counter++;
  }

}
