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
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import testutils.PluginLocator;

public enum TestPlugin {
  JAVA(Language.JAVA, PluginLocator.getJavaPluginPath(), PluginLocator.SONAR_JAVA_PLUGIN_VERSION, PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH),
  PHP(Language.PHP, PluginLocator.getPhpPluginPath(), PluginLocator.SONAR_PHP_PLUGIN_VERSION, PluginLocator.SONAR_PHP_PLUGIN_JAR_HASH),
  PYTHON(Language.PYTHON, PluginLocator.getPythonPluginPath(), PluginLocator.SONAR_PYTHON_PLUGIN_VERSION, PluginLocator.SONAR_PYTHON_PLUGIN_JAR_HASH),
  JAVASCRIPT(Language.JS, PluginLocator.getJavaScriptPluginPath(), PluginLocator.SONAR_JAVASCRIPT_PLUGIN_VERSION, PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH),
  TEXT(Language.SECRETS, PluginLocator.getTextPluginPath(), PluginLocator.SONAR_TEXT_PLUGIN_VERSION, PluginLocator.SONAR_TEXT_PLUGIN_JAR_HASH);

  private final Language language;
  private final Path path;
  private final String version;
  private final String hash;

  TestPlugin(Language language, Path path, String version, String hash) {
    this.language = language;
    this.path = path;
    this.version = version;
    this.hash = hash;
  }

  public Language getLanguage() {
    return language;
  }

  public String getPluginKey() {
    return org.sonarsource.sonarlint.core.commons.Language.valueOf(getLanguage().name()).getPluginKey();
  }

  public Path getPath() {
    return path;
  }

  public String getVersion() {
    return version;
  }

  public String getHash() {
    return hash;
  }
}
