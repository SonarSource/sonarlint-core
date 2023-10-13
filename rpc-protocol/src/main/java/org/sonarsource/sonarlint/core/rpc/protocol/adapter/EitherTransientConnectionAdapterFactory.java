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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import java.util.function.Predicate;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarQubeConnectionDto;

public class EitherTransientConnectionAdapterFactory implements TypeAdapterFactory {

  private static final TypeToken<Either<TransientSonarQubeConnectionDto, TransientSonarCloudConnectionDto>> ELEMENT_TYPE = new TypeToken<>() {
  };

  @SuppressWarnings("unchecked")
  @Override
  public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    Predicate<JsonElement> leftChecker = new EitherTypeAdapter.PropertyChecker("serverUrl");
    Predicate<JsonElement> rightChecker = new EitherTypeAdapter.PropertyChecker("organization");
    return (TypeAdapter<T>) new EitherTypeAdapter<>(gson, ELEMENT_TYPE, leftChecker, rightChecker);
  }
}
