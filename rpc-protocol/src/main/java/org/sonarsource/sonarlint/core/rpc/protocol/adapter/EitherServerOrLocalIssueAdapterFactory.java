/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.adapter;

import com.google.gson.reflect.TypeToken;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LocalOnlyIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ServerMatchedIssueDto;

public class EitherServerOrLocalIssueAdapterFactory extends CustomEitherAdapterFactory<ServerMatchedIssueDto, LocalOnlyIssueDto> {

  private static final TypeToken<Either<ServerMatchedIssueDto, LocalOnlyIssueDto>> ELEMENT_TYPE = new TypeToken<>() {
  };

  public EitherServerOrLocalIssueAdapterFactory() {
    super(ELEMENT_TYPE, ServerMatchedIssueDto.class, LocalOnlyIssueDto.class, new EitherTypeAdapter.PropertyChecker("serverKey"));
  }

}
