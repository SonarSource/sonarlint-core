/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2020 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.util.Optional;
import java.util.stream.Stream;

public enum Language {

  ABAP("abap", "abap"),
  COBOL("cobol", "cobol"),
  C("c", "cpp"),
  CPP("cpp", "cpp"),
  OBJC("objc", "cpp"),
  JAVA("java", "java"),
  JS("js", "javascript"),
  KOTLIN("kotlin", "kotlin"),
  PHP("php", "php"),
  PLI("pli", "pli"),
  PLSQL("plsql", "plsql"),
  PYTHON("py", "python"),
  RPG("rpg", "rpg"),
  RUBY("ruby", "ruby"),
  SWIFT("swift", "swift"),
  APEX("apex", "sonarapex"),
  SCALA("scala", "sonarscala"),
  TSQL("tsql", "tsql"),
  TS("ts", "typescript"),
  HTML("web", "web"),
  XML("xml", "xml"),
  // For ITs
  XOO("xoo", "xoo");

  private String languageKey;
  private String pluginKey;

  Language(String languageKey, String pluginKey) {
    this.languageKey = languageKey;
    this.pluginKey = pluginKey;
  }

  public String getLanguageKey() {
    return languageKey;
  }

  public String getPluginKey() {
    return pluginKey;
  }

  public static Optional<String> getPluginKeyByLanguageKey(String languageKey) {
    return Stream.of(values()).filter(l -> l.getLanguageKey().equals(languageKey)).map(Language::getPluginKey).findFirst();
  }

  public static boolean containsPlugin(String pluginKey) {
    return Stream.of(values()).anyMatch(l -> l.getPluginKey().equals(pluginKey));
  }

}
