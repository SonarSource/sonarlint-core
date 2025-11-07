/*
 * SonarLint Core - Test Utils
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.test.utils.plugins;

import java.nio.file.Path;
import java.util.Set;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

public class Plugin {
  private final String key;
  private final Set<Language> languages;
  private final Path path;
  private final String version;
  private final String hash;

  private static String getPluginKeyFromLanguage(Language language) {
    return SonarLanguage.valueOf(language.name()).getPluginKey();
  }

  public Plugin(Language language, Path path, String version, String hash) {
    this(Set.of(language), path, version, hash);
  }

  public Plugin(String key, Language language, Path path, String version, String hash) {
    this(key, Set.of(language), path, version, hash);
  }

  public Plugin(Set<Language> languages, Path path, String version, String hash) {
    this(getPluginKeyFromLanguage(languages.iterator().next()), languages, path, version, hash);
  }

  public Plugin(String key, Set<Language> languages, Path path, String version, String hash) {
    this.key = key;
    this.languages = languages;
    this.path = path;
    this.version = version;
    this.hash = hash;
  }

  public Set<Language> getLanguages() {
    return languages;
  }

  public String getPluginKey() {
    return key;
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
