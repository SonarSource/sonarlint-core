/*
 * SonarLint Core - Client API
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.clientapi.backend.rules;

import java.util.Collection;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class ActiveRuleDescriptionTabDto {
  private final String title;
  private final Either<ActiveRuleNonContextualSectionDto, Collection<ActiveRuleContextualSectionDto>> content;

  public ActiveRuleDescriptionTabDto(String title, Either<ActiveRuleNonContextualSectionDto, Collection<ActiveRuleContextualSectionDto>> content) {
    this.title = title;
    this.content = content;
  }

  public String getTitle() {
    return title;
  }

  public Either<ActiveRuleNonContextualSectionDto, Collection<ActiveRuleContextualSectionDto>> getContent() {
    return content;
  }
}
