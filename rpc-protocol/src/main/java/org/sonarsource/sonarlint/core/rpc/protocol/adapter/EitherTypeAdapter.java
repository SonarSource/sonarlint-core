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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

import static org.sonarsource.sonarlint.core.rpc.protocol.Lsp4jUtils.isEither;

public class EitherTypeAdapter<L, R> extends TypeAdapter<Either<L, R>> {

  private final org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter<L, R> lsp4jEitherAdapter;

  public EitherTypeAdapter(Gson gson, TypeToken<? extends Either<L, R>> typeToken, @Nullable Predicate<JsonElement> leftChecker,
    @Nullable Predicate<JsonElement> rightChecker) {
    var eitherType = (ParameterizedType) typeToken.getType();
    var lsp4jEitherType = new ParameterizedTypeImpl(org.eclipse.lsp4j.jsonrpc.messages.Either.class, eitherType.getActualTypeArguments());
    var lsp4jTypeToken = (TypeToken<? extends org.eclipse.lsp4j.jsonrpc.messages.Either<L, R>>) TypeToken.get(lsp4jEitherType);
    this.lsp4jEitherAdapter = new org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter<>(gson, lsp4jTypeToken, leftChecker,
      rightChecker, null, null);
  }

  public EitherTypeAdapter(Gson gson, TypeToken<? extends Either<L, R>> typeToken) {
    this(gson, typeToken, null, null);
  }

  public static class Factory implements TypeAdapterFactory {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
      if (!isEither(typeToken.getType())) {
        return null;
      }
      return new EitherTypeAdapter(gson, typeToken);
    }
  }

  @Override
  public void write(JsonWriter out, Either<L, R> value) throws IOException {
    this.lsp4jEitherAdapter.write(out, value.getLsp4jEither());
  }

  @Override
  public Either<L, R> read(JsonReader in) throws IOException {
    return new Either<>(this.lsp4jEitherAdapter.read(in));
  }

  private static class ParameterizedTypeImpl implements ParameterizedType {

    private final Type rawType;
    private final Type[] actualTypeArguments;

    ParameterizedTypeImpl(Type rawType, Type[] typeArguments) {
      this.rawType = rawType;
      this.actualTypeArguments = typeArguments;
    }

    @Override
    public Type getOwnerType() {
      return null;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }
  }

}
