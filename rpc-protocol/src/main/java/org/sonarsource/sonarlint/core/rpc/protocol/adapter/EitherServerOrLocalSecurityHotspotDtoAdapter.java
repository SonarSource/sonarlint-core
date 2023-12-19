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
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.LocalOnlySecurityHotspotDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.MatchWithServerSecurityHotspotsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.ServerMatchedSecurityHotspotDto;

import static java.util.function.Predicate.not;

public class EitherServerOrLocalSecurityHotspotDtoAdapter extends TypeAdapter<MatchWithServerSecurityHotspotsResponse.ServerOrLocalSecurityHotspotDto> {


  private static final TypeToken<Either<ServerMatchedSecurityHotspotDto, LocalOnlySecurityHotspotDto>> ELEMENT_TYPE = new TypeToken<>() {
  };

  private final EitherTypeAdapter<ServerMatchedSecurityHotspotDto, LocalOnlySecurityHotspotDto> wrappedAdapter;

  public static class Factory implements TypeAdapterFactory {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <T> TypeAdapter<T> create(Gson gson, @Nonnull TypeToken<T> typeToken) {
      if (!MatchWithServerSecurityHotspotsResponse.ServerOrLocalSecurityHotspotDto.class.isAssignableFrom(typeToken.getRawType())) {
        return null;
      }
      return (TypeAdapter<T>) new EitherServerOrLocalSecurityHotspotDtoAdapter(gson, typeToken);
    }

  }

  public EitherServerOrLocalSecurityHotspotDtoAdapter(Gson gson, TypeToken<?> typeToken) {
    Predicate<JsonElement> leftChecker = new EitherTypeAdapter.PropertyChecker("serverKey");
    Predicate<JsonElement> rightChecker = not(leftChecker);
    wrappedAdapter = new EitherTypeAdapter<>(gson, ELEMENT_TYPE, leftChecker, rightChecker);
  }

  @Override
  public void write(JsonWriter out, @Nonnull MatchWithServerSecurityHotspotsResponse.ServerOrLocalSecurityHotspotDto value) throws IOException {
    wrappedAdapter.write(out, value.getWrapped());
  }

  @Override
  public MatchWithServerSecurityHotspotsResponse.ServerOrLocalSecurityHotspotDto read(JsonReader in) throws IOException {
    var wrapped = wrappedAdapter.read(in);
    return new MatchWithServerSecurityHotspotsResponse.ServerOrLocalSecurityHotspotDto(wrapped);
  }

}
