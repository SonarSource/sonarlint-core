/*
 * SonarLint Core - Commons
 * Copyright (C) SonarSource Sàrl
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
package org.sonarsource.sonarlint.core.commons.plugins;

import java.util.Arrays;
import java.util.Optional;

public enum SonarPlugin implements Plugin {
  ABAP("abap"),
  APEX("sonarapex"),
  C_FAMILY("cpp"),
  CS_OSS("csharp"),
  COBOL("cobol"),
  GO("go"),
  IAC("iac"),
  IAC_ENTERPRISE("iacenterprise"),
  JAVA("java"),
  JCL("jcl"),
  JS("javascript"),
  KOTLIN("kotlin"),
  PHP("php"),
  PLI("pli"),
  PLSQL("plsql"),
  PYTHON("python"),
  RPG("rpg"),
  RUBY("ruby"),
  SCALA("sonarscala"),
  SWIFT("swift"),
  TEXT("text"),
  TSQL("tsql"),
  VBNET_OSS("vbnet"),
  WEB("web"),
  XML("xml");

  public static Optional<SonarPlugin> findByKey(String key) {
    return Arrays.stream(values()).filter(p -> p.key.equals(key)).findFirst();
  }

  private final String key;

  SonarPlugin(String key) {
    this.key = key;
  }

  public String getKey() {
    return key;
  }
}
