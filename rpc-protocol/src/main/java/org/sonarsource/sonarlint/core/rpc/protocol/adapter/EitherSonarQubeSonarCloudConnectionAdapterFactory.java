/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2024 SonarSource SA
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
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarCloudConnectionSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.SonarQubeConnectionSuggestionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

public class EitherSonarQubeSonarCloudConnectionAdapterFactory extends CustomEitherAdapterFactory<SonarQubeConnectionSuggestionDto, SonarCloudConnectionSuggestionDto> {

  private static final TypeToken<Either<SonarQubeConnectionSuggestionDto, SonarCloudConnectionSuggestionDto>> ELEMENT_TYPE = new TypeToken<>() {
  };

  public EitherSonarQubeSonarCloudConnectionAdapterFactory() {
    super(ELEMENT_TYPE, SonarQubeConnectionSuggestionDto.class, SonarCloudConnectionSuggestionDto.class, new EitherTypeAdapter.PropertyChecker("serverUrl"));
  }

}
