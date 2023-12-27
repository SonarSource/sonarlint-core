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

public enum CleanCodeAttribute {

  CONVENTIONAL("Not conventional", CleanCodeAttributeCategory.CONSISTENT),
  FORMATTED("Not formatted", CleanCodeAttributeCategory.CONSISTENT),
  IDENTIFIABLE("Not identifiable", CleanCodeAttributeCategory.CONSISTENT),

  CLEAR("Not clear", CleanCodeAttributeCategory.INTENTIONAL),
  COMPLETE("Not complete", CleanCodeAttributeCategory.INTENTIONAL),
  EFFICIENT("Not efficient", CleanCodeAttributeCategory.INTENTIONAL),
  LOGICAL("Not logical", CleanCodeAttributeCategory.INTENTIONAL),

  DISTINCT("Not distinct", CleanCodeAttributeCategory.ADAPTABLE),
  FOCUSED("Not focused", CleanCodeAttributeCategory.ADAPTABLE),
  MODULAR("Not modular", CleanCodeAttributeCategory.ADAPTABLE),
  TESTED("Not tested", CleanCodeAttributeCategory.ADAPTABLE),

  LAWFUL("Not lawful", CleanCodeAttributeCategory.RESPONSIBLE),
  RESPECTFUL("Not respectful", CleanCodeAttributeCategory.RESPONSIBLE),
  TRUSTWORTHY("Not trustworthy", CleanCodeAttributeCategory.RESPONSIBLE);


  private final String label;
  private final CleanCodeAttributeCategory category;

  CleanCodeAttribute(String label, CleanCodeAttributeCategory category) {
    this.label = label;
    this.category = category;
  }

  public String getLabel() {
    return label;
  }

  public CleanCodeAttributeCategory getCategory() {
    return category;
  }

  public static CleanCodeAttribute fromDto(org.sonarsource.sonarlint.core.rpc.protocol.common.CleanCodeAttribute rpcEnum) {
    switch (rpcEnum) {
      case CONVENTIONAL:
        return CONVENTIONAL;
      case FORMATTED:
        return FORMATTED;
      case IDENTIFIABLE:
        return IDENTIFIABLE;
      case CLEAR:
        return CLEAR;
      case COMPLETE:
        return COMPLETE;
      case EFFICIENT:
        return EFFICIENT;
      case LOGICAL:
        return LOGICAL;
      case DISTINCT:
        return DISTINCT;
      case FOCUSED:
        return FOCUSED;
      case MODULAR:
        return MODULAR;
      case TESTED:
        return TESTED;
      case LAWFUL:
        return LAWFUL;
      case RESPECTFUL:
        return RESPECTFUL;
      case TRUSTWORTHY:
        return TRUSTWORTHY;
      default:
        throw new IllegalArgumentException("Unknown attribute: " + rpcEnum);
    }
  }

}
