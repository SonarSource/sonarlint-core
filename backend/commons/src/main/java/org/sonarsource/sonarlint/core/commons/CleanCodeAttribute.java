/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons;

import static org.sonarsource.sonarlint.core.commons.CleanCodeAttributeCategory.ADAPTABLE;
import static org.sonarsource.sonarlint.core.commons.CleanCodeAttributeCategory.CONSISTENT;
import static org.sonarsource.sonarlint.core.commons.CleanCodeAttributeCategory.INTENTIONAL;
import static org.sonarsource.sonarlint.core.commons.CleanCodeAttributeCategory.RESPONSIBLE;

public enum CleanCodeAttribute {

  CONVENTIONAL(CONSISTENT),
  FORMATTED(CONSISTENT),
  IDENTIFIABLE(CONSISTENT),

  CLEAR(INTENTIONAL),
  COMPLETE(INTENTIONAL),
  EFFICIENT(INTENTIONAL),
  LOGICAL(INTENTIONAL),

  DISTINCT(ADAPTABLE),
  FOCUSED(ADAPTABLE),
  MODULAR(ADAPTABLE),
  TESTED(ADAPTABLE),

  LAWFUL(RESPONSIBLE),
  RESPECTFUL(RESPONSIBLE),
  TRUSTWORTHY(RESPONSIBLE);

  private final CleanCodeAttributeCategory attributeCategory;


  CleanCodeAttribute(CleanCodeAttributeCategory attributeCategory) {
    this.attributeCategory = attributeCategory;
  }


  public CleanCodeAttributeCategory getAttributeCategory() {
    return attributeCategory;
  }

  public static CleanCodeAttribute defaultCleanCodeAttribute() {
    return CONVENTIONAL;
  }
}
