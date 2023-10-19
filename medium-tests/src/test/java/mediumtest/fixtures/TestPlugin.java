/*
 * SonarLint Core - Medium Tests
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
package mediumtest.fixtures;

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.commons.Language;
import testutils.PluginLocator;

public enum TestPlugin {
  JAVA(Language.JAVA, PluginLocator.getJavaPluginPath()),
  PHP(Language.PHP, PluginLocator.getPhpPluginPath()),
  PYTHON(Language.PYTHON, PluginLocator.getPythonPluginPath());

  private final Language language;
  private final Path path;

  TestPlugin(Language language, Path path) {
    this.language = language;
    this.path = path;
  }

  public Language getLanguage() {
    return language;
  }

  public Path getPath() {
    return path;
  }
}
