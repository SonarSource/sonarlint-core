/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin;

/**
 * Represents the current state of an analyzer plugin as observed by the backend.
 */
public enum PluginStateDto {

  /** The plugin is loaded and ready for analysis. */
  ACTIVE("Active"),

  /** The plugin was downloaded from a SonarQube Server or SonarQube Cloud connection. */
  SYNCED("Synced"),

  /** The plugin is currently being downloaded. */
  DOWNLOADING("Downloading…"),

  /** The plugin failed to load or is otherwise unavailable. */
  FAILED("Failed"),

  /** The plugin is available only in connected mode (premium feature). */
  PREMIUM("Premium"),

  /** The plugin is not supported in the current IDE or platform. */
  UNSUPPORTED("Unsupported");

  private final String label;

  PluginStateDto(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

}
