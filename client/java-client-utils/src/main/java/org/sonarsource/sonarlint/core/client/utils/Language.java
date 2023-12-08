/*
 * SonarLint Core - Java Client Utils
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
package org.sonarsource.sonarlint.core.client.utils;

public enum Language {

  ABAP("ABAP"),
  APEX("Apex"),
  C("C"),
  CPP("C++"),
  CS("C#"),
  CSS("CSS"),
  OBJC("Objective-C"),
  COBOL("COBOL"),
  HTML("HTML"),
  IPYTHON("IPython Notebooks"),
  JAVA("Java"),
  JS("JavaScript"),
  KOTLIN("Kotlin"),
  PHP("PHP"),
  PLI("PL/I"),
  PLSQL("PL/SQL"),
  PYTHON("Python"),
  RPG("RPG"),
  RUBY("Ruby"),
  SCALA("Scala"),
  SECRETS("Secrets"),
  SWIFT("Swift"),
  TSQL("T-SQL"),
  TS("TypeScript"),
  JSP("JSP"),
  VBNET("VB.NET"),
  XML("XML"),
  YAML("YAML"),
  JSON("JSON"),
  GO("Go"),
  CLOUDFORMATION("CloudFormation"),
  DOCKER("Docker"),
  KUBERNETES("Kubernetes"),
  TERRAFORM("Terraform"),
  AZURERESOURCEMANAGER("AzureResourceManager");
  private String label;

  Language(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  public static Language fromDto(org.sonarsource.sonarlint.core.rpc.protocol.common.Language rpcEnum) {
    switch (rpcEnum) {
      case ABAP:
        return ABAP;
      case APEX:
        return APEX;
      case C:
        return C;
      case CPP:
        return CPP;
      case CS:
        return CS;
      case CSS:
        return CSS;
      case OBJC:
        return OBJC;
      case COBOL:
        return COBOL;
      case HTML:
        return HTML;
      case IPYTHON:
        return IPYTHON;
      case JAVA:
        return JAVA;
      case JS:
        return JS;
      case KOTLIN:
        return KOTLIN;
      case PHP:
        return PHP;
      case PLI:
        return PLI;
      case PLSQL:
        return PLSQL;
      case PYTHON:
        return PYTHON;
      case RPG:
        return RPG;
      case RUBY:
        return RUBY;
      case SCALA:
        return SCALA;
      case SECRETS:
        return SECRETS;
      case SWIFT:
        return SWIFT;
      case TSQL:
        return TSQL;
      case TS:
        return TS;
      case JSP:
        return JSP;
      case VBNET:
        return VBNET;
      case XML:
        return XML;
      case YAML:
        return YAML;
      case JSON:
        return JSON;
      case GO:
        return GO;
      case CLOUDFORMATION:
        return CLOUDFORMATION;
      case DOCKER:
        return DOCKER;
      case KUBERNETES:
        return KUBERNETES;
      case TERRAFORM:
        return TERRAFORM;
      case AZURERESOURCEMANAGER:
        return AZURERESOURCEMANAGER;
      default:
        throw new IllegalArgumentException("Unknown language: " + rpcEnum);
    }
  }

}
