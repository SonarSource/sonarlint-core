/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.clientapi.common;

public enum Language {

  ABAP(1, "abap"),
  APEX(2, "apex"),
  C(3, "c"),
  CPP(4, "cpp"),
  CS(5, "cs"),
  CSS(6, "css"),
  OBJC(7, "objc"),
  COBOL(8, "cobol"),
  HTML(9, "web"),
  IPYTHON(10, "ipynb"),
  JAVA(11, "java"),
  JS(12, "js"),
  KOTLIN(13, "kotlin"),
  PHP(14, "php"),
  PLI(15, "pli"),
  PLSQL(16, "plsql"),
  PYTHON(17, "py"),
  RPG(18, "rpg"),
  RUBY(19, "ruby"),
  SCALA(20, "scala"),
  SECRETS(21, "secrets"),
  SWIFT(22, "swift"),
  TSQL(23, "tsql"),
  TS(24, "ts"),
  JSP(25, "jsp"),
  VBNET(26, "vbnet"),
  XML(27, "xml"),
  YAML(28, "yaml"),
  GO(29, "go"),
  CLOUDFORMATION(30, "cloudformation"),
  DOCKER(31, "docker"),
  KUBERNETES(32, "kubernetes"),
  TERRAFORM(33, "terraform");

  private int value;
  private String languageKey;

  Language(int value, String languageKey) {
    this.value = value;
    this.languageKey = languageKey;
  }

  public int getValue() {
    return value;
  }

  public String getLanguageKey() {
    return languageKey;
  }

}
