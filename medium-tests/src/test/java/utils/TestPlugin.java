/*
 * SonarLint Core - Medium Tests
 * Copyright (C) 2016-2025 SonarSource SA
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
package utils;

import java.nio.file.Path;
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.test.utils.plugins.Plugin;

public class TestPlugin {
  public static final Plugin JAVA = new Plugin(Language.JAVA, PluginLocator.getJavaPluginPath(), PluginLocator.SONAR_JAVA_PLUGIN_VERSION, PluginLocator.SONAR_JAVA_PLUGIN_JAR_HASH);
  public static final Plugin PHP = new Plugin(Language.PHP, PluginLocator.getPhpPluginPath(), PluginLocator.SONAR_PHP_PLUGIN_VERSION, PluginLocator.SONAR_PHP_PLUGIN_JAR_HASH);
  public static final Plugin PYTHON = new Plugin(Language.PYTHON, PluginLocator.getPythonPluginPath(), PluginLocator.SONAR_PYTHON_PLUGIN_VERSION,
    PluginLocator.SONAR_PYTHON_PLUGIN_JAR_HASH);
  public static final Plugin JAVASCRIPT = new Plugin(Set.of(Language.JS, Language.TS), PluginLocator.getJavaScriptPluginPath(), PluginLocator.SONAR_JAVASCRIPT_PLUGIN_VERSION,
    PluginLocator.SONAR_JAVASCRIPT_PLUGIN_JAR_HASH);
  public static final Plugin TEXT = new Plugin(Language.SECRETS, PluginLocator.getTextPluginPath(), PluginLocator.SONAR_TEXT_PLUGIN_VERSION,
    PluginLocator.SONAR_TEXT_PLUGIN_JAR_HASH);
  public static final Plugin XML = new Plugin(Language.XML, PluginLocator.getXmlPluginPath(), PluginLocator.SONAR_XML_PLUGIN_VERSION, PluginLocator.SONAR_XML_PLUGIN_JAR_HASH);
  public static final Plugin CFAMILY = new Plugin(Set.of(Language.C, Language.CPP, Language.OBJC), PluginLocator.getCppPluginPath(), PluginLocator.SONAR_CFAMILY_PLUGIN_VERSION,
    PluginLocator.SONAR_CFAMILY_PLUGIN_JAR_HASH);
  public static final Plugin KOTLIN = new Plugin(Set.of(Language.KOTLIN), PluginLocator.getKotlinPluginPath(), PluginLocator.SONAR_KOTLIN_PLUGIN_JAR,
    PluginLocator.SONAR_KOTLIN_PLUGIN_JAR_HASH);

  private final Set<Language> languages;
  private final Path path;
  private final String version;
  private final String hash;

  TestPlugin(Language language, Path path, String version, String hash) {
    this(Set.of(language), path, version, hash);
  }

  TestPlugin(Set<Language> languages, Path path, String version, String hash) {
    this.languages = languages;
    this.path = path;
    this.version = version;
    this.hash = hash;
  }

  public Set<Language> getLanguages() {
    return languages;
  }

  public String getPluginKey() {
    return SonarLanguage.valueOf(languages.iterator().next().name()).getPluginKey();
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
