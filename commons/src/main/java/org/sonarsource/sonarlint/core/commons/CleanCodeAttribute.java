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
package org.sonarsource.sonarlint.core.commons;

import static org.sonarsource.sonarlint.core.commons.CleanCodeAttributeCategory.ADAPTABLE;
import static org.sonarsource.sonarlint.core.commons.CleanCodeAttributeCategory.CONSISTENT;
import static org.sonarsource.sonarlint.core.commons.CleanCodeAttributeCategory.INTENTIONAL;
import static org.sonarsource.sonarlint.core.commons.CleanCodeAttributeCategory.RESPONSIBLE;

public enum CleanCodeAttribute {

  CONVENTIONAL("Not conventional", CONSISTENT),
  FORMATTED("Not formatted", CONSISTENT),
  IDENTIFIABLE("Not identifiable", CONSISTENT),

  CLEAR("Not clear", INTENTIONAL),
  COMPLETE("Not complete", INTENTIONAL),
  EFFICIENT("Not efficient", INTENTIONAL),
  LOGICAL("Not logical", INTENTIONAL),

  DISTINCT("Not distinct", ADAPTABLE),
  FOCUSED("Not focused", ADAPTABLE),
  MODULAR("Not modular", ADAPTABLE),
  TESTED("Not tested", ADAPTABLE),

  LAWFUL("Not lawful", RESPONSIBLE),
  RESPECTFUL("Not respectful", RESPONSIBLE),
  TRUSTWORTHY("Not trustworthy", RESPONSIBLE);

  private final CleanCodeAttributeCategory attributeCategory;

  private final String issueLabel;

  CleanCodeAttribute(String issueLabel, CleanCodeAttributeCategory attributeCategory) {
    this.issueLabel = issueLabel;
    this.attributeCategory = attributeCategory;
  }

  public String getIssueLabel() {
    return issueLabel;
  }

  public CleanCodeAttributeCategory getAttributeCategory() {
    return attributeCategory;
  }

  public static CleanCodeAttribute defaultCleanCodeAttribute() {
    return CONVENTIONAL;
  }
}
