/*
 * SonarLint Language Server
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
package org.sonarlint.languageserver;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.sonarsource.sonarlint.core.client.api.common.RuleDetails;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class RuleDescription {

  @SerializedName("key")
  @Expose
  private String key;

  @SerializedName("name")
  @Expose
  private String name;

  @SerializedName("htmlDescription")
  @Expose
  private String htmlDescription;

  @SerializedName("type")
  @Expose
  private String type;

  @SerializedName("severity")
  @Expose
  private String severity;

  @SerializedName("activeByDefault")
  @Expose
  private boolean activeByDefault;

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  @CheckForNull
  public String getHtmlDescription() {
    return htmlDescription;
  }

  @CheckForNull
  public String getType() {
    return type;
  }

  public String getSeverity() {
    return severity;
  }

  public boolean isActiveByDefault() {
    return activeByDefault;
  }

  public RuleDescription setKey(String key) {
    this.key = key;
    return this;
  }

  public RuleDescription setName(String name) {
    this.name = name;
    return this;
  }

  public RuleDescription setHtmlDescription(@Nullable String htmlDescription) {
    this.htmlDescription = htmlDescription;
    return this;
  }

  public RuleDescription setType(@Nullable String type) {
    this.type = type;
    return this;
  }

  public RuleDescription setSeverity(String severity) {
    this.severity = severity;
    return this;
  }

  public RuleDescription setActiveByDefault(boolean activeByDefault) {
    this.activeByDefault = activeByDefault;
    return this;
  }

  @Override
  public String toString() {
    return MessageJsonHandler.toString(this);
  }

  public static RuleDescription of(String key, String name, @Nullable String htmlDescription, @Nullable String type, String severity, boolean activeByDefault) {
    return new RuleDescription()
      .setKey(key)
      .setName(name)
      .setHtmlDescription(htmlDescription)
      .setType(type)
      .setSeverity(severity)
      .setActiveByDefault(activeByDefault);
  }

  public static RuleDescription of(RuleDetails d) {
    return of(d.getKey(), d.getName(), d.getHtmlDescription(), d.getType(), d.getSeverity(), d.isActiveByDefault());
  }
}
