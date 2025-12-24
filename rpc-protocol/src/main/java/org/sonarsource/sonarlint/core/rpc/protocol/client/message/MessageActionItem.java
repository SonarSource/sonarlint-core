/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.client.message;

/**
 * An option to be presented to the user.
 */
public class MessageActionItem {

  /**
   * A unique key that identifies the option.
   */
  private final String key;
  /**
   * A text to display to the user while presenting choices.
   */
  private final String displayText;
  /**
   * Describes if the action should be somehow additionally highlighted (for example with color).
   * There can be multiple primary actions. It doesn't mean that this option should be a default or first one.
   */
  private final boolean isPrimaryAction;

  public MessageActionItem(String key, String displayText, boolean isPrimaryAction) {
    this.key = key;
    this.displayText = displayText;
    this.isPrimaryAction = isPrimaryAction;
  }

  public String getKey() {
    return key;
  }

  public String getDisplayText() {
    return displayText;
  }

  public boolean isPrimaryAction() {
    return isPrimaryAction;
  }
}
