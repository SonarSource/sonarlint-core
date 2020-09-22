/*
 * Example Plugin with global extension
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
package org.sonarsource.plugins.example;

import org.sonar.api.resources.AbstractLanguage;

public class GlobalLanguage extends AbstractLanguage {

  // Need to use a key that is available in the Language enum
  static final String LANGUAGE_KEY = "xoo";

  public GlobalLanguage() {
    super(LANGUAGE_KEY, "Global");
  }

  @Override
  public String[] getFileSuffixes() {
    return new String[] {"glob"};
  }

}
