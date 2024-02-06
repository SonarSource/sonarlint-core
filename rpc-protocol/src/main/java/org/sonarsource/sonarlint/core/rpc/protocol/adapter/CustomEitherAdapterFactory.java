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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Predicate;
import org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter;
import org.eclipse.lsp4j.jsonrpc.json.adapters.TypeUtils;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import static java.util.function.Predicate.not;

public abstract class CustomEitherAdapterFactory<L, R> implements TypeAdapterFactory {

  private final TypeToken<Either<L, R>> elementType;
  private final Class<L> leftClass;
  private final Class<R> rightClass;
  private final Predicate<JsonElement> leftChecker;

  protected CustomEitherAdapterFactory(TypeToken<Either<L, R>> elementType, Class<L> leftClass, Class<R> rightClass, Predicate<JsonElement> leftChecker) {
    this.elementType = elementType;
    this.leftClass = leftClass;
    this.rightClass = rightClass;
    this.leftChecker = leftChecker;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
    if (!TypeUtils.isEither(type.getType())) {
      return null;
    }
    Type[] typeParameters = ((ParameterizedType) type.getType()).getActualTypeArguments();
    var leftType = typeParameters[0];
    var rightType = typeParameters[1];
    if (!leftClass.isAssignableFrom((Class<?>) leftType) && !rightClass.isAssignableFrom((Class<?>) rightType)) {
      return null;
    }
    return (TypeAdapter<T>) new EitherTypeAdapter<>(gson, elementType, leftChecker, not(leftChecker));
  }
}
