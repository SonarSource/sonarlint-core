/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.common;

import java.util.Objects;
import javax.annotation.concurrent.Immutable;

@Immutable
public class RuleKey {

  private final String repository;
  private final String rule;

  public RuleKey(String repository, String rule) {
    this.repository = repository;
    this.rule = rule;
  }

  public String repository() {
    return repository;
  }

  public String rule() {
    return rule;
  }

  @Override
  public String toString() {
    return repository + ":" + rule;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RuleKey ruleKey = (RuleKey) o;
    return Objects.equals(repository, ruleKey.repository) &&
      Objects.equals(rule, ruleKey.rule);
  }

  @Override
  public int hashCode() {
    return Objects.hash(repository, rule);
  }
}
