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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Predicate;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

/*
 * A class to use in place of {@link org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter} to stop depending on lsp4j types in API
 * and services.
 * See SLCORE-663 for details.
 */
public class EitherTypeAdapter<L, R> extends TypeAdapter<Either<L, R>> {

  public static class Factory implements TypeAdapterFactory {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
      if (!TypeUtils.isEither(typeToken.getType())) {
        return null;
      }
      return new org.eclipse.lsp4j.jsonrpc.json.adapters.EitherTypeAdapter(gson, typeToken);
    }

  }

  /**
   * A predicate that is useful for checking alternatives in case both the left and the right type
   * are JSON object types.
   */
  public static class PropertyChecker implements Predicate<JsonElement> {

    private final String propertyName;
    private final String expectedValue;
    private final Class<? extends JsonElement> expectedType;

    public PropertyChecker(String propertyName) {
      this.propertyName = propertyName;
      this.expectedValue = null;
      this.expectedType = null;
    }

    public PropertyChecker(String propertyName, String expectedValue) {
      this.propertyName = propertyName;
      this.expectedValue = expectedValue;
      this.expectedType = null;
    }

    public PropertyChecker(String propertyName, Class<? extends JsonElement> expectedType) {
      this.propertyName = propertyName;
      this.expectedType = expectedType;
      this.expectedValue = null;
    }

    @Override
    public boolean test(JsonElement element) {
      if (element.isJsonObject()) {
        JsonObject object = element.getAsJsonObject();
        JsonElement value = object.get(propertyName);
        if (expectedValue != null)
          return value != null && value.isJsonPrimitive() && expectedValue.equals(value.getAsString());
        else if (expectedType != null)
          return expectedType.isInstance(value);
        else
          return value != null;
      }
      return false;
    }

  }

  /**
   * A predicate for the case that a type alternative is a list.
   */
  public static class ListChecker implements Predicate<JsonElement> {

    private final Predicate<JsonElement> elementChecker;
    private final boolean resultIfEmpty;

    public ListChecker(Predicate<JsonElement> elementChecker) {
      this(elementChecker, false);
    }

    public ListChecker(Predicate<JsonElement> elementChecker, boolean resultIfEmpty) {
      this.elementChecker = elementChecker;
      this.resultIfEmpty = resultIfEmpty;
    }

    @Override
    public boolean test(JsonElement t) {
      if (elementChecker.test(t))
        return true;
      if (t.isJsonArray()) {
        JsonArray array = t.getAsJsonArray();
        if (array.isEmpty())
          return resultIfEmpty;
        for (JsonElement e : array) {
          if (elementChecker.test(e))
            return true;
        }
      }
      return false;
    }

  }

  protected final TypeToken<? extends Either<L, R>> typeToken;
  protected final EitherTypeArgument<L> left;
  protected final EitherTypeArgument<R> right;
  protected final Predicate<JsonElement> leftChecker;
  protected final Predicate<JsonElement> rightChecker;

  public EitherTypeAdapter(Gson gson, TypeToken<? extends Either<L, R>> typeToken) {
    this(gson, typeToken, null, null);
  }

  public EitherTypeAdapter(Gson gson, TypeToken<? extends Either<L, R>> typeToken, Predicate<JsonElement> leftChecker, Predicate<JsonElement> rightChecker) {
    this(gson, typeToken, leftChecker, rightChecker, null, null);
  }

  public EitherTypeAdapter(Gson gson, TypeToken<? extends Either<L, R>> typeToken, Predicate<JsonElement> leftChecker, Predicate<JsonElement> rightChecker,
    TypeAdapter<L> leftAdapter, TypeAdapter<R> rightAdapter) {
    this.typeToken = typeToken;
    Type[] elementTypes = TypeUtils.getElementTypes(typeToken, Either.class);
    this.left = new EitherTypeArgument<L>(gson, elementTypes[0], leftAdapter);
    this.right = new EitherTypeArgument<R>(gson, elementTypes[1], rightAdapter);
    this.leftChecker = leftChecker;
    this.rightChecker = rightChecker;
  }

  @Override
  public void write(JsonWriter out, Either<L, R> value) throws IOException {
    if (value == null) {
      out.nullValue();
    } else if (value.isLeft()) {
      left.write(out, value.getLeft());
    } else {
      right.write(out, value.getRight());
    }
  }

  @Override
  public Either<L, R> read(JsonReader in) throws IOException {
    JsonToken next = in.peek();
    if (next == JsonToken.NULL) {
      in.nextNull();
      return null;
    }
    return create(next, in);
  }

  protected Either<L, R> create(JsonToken nextToken, JsonReader in) throws IOException {
    boolean matchesLeft = left.isAssignable(nextToken);
    boolean matchesRight = right.isAssignable(nextToken);
    if (matchesLeft && matchesRight) {
      if (leftChecker != null || rightChecker != null) {
        JsonElement element = JsonParser.parseReader(in);
        if (leftChecker != null && leftChecker.test(element))
          // Parse the left alternative from the JSON element tree
          return createLeft(left.read(element));
        if (rightChecker != null && rightChecker.test(element))
          // Parse the right alternative from the JSON element tree
          return createRight(right.read(element));
      }
      throw new JsonParseException("Ambiguous Either type: token " + nextToken + " matches both alternatives.");
    } else if (matchesLeft) {
      // Parse the left alternative from the JSON stream
      return createLeft(left.read(in));
    } else if (matchesRight) {
      // Parse the right alternative from the JSON stream
      return createRight(right.read(in));
    } else if (leftChecker != null || rightChecker != null) {
      // If result is not the list but directly the only item in the list
      JsonElement element = JsonParser.parseReader(in);
      if (leftChecker != null && leftChecker.test(element))
        // Parse the left alternative from the JSON element tree
        return createLeft(left.read(element));
      if (rightChecker != null && rightChecker.test(element))
        // Parse the right alternative from the JSON element tree
        return createRight(right.read(element));
    }
    throw new JsonParseException("Unexpected token " + nextToken + ": expected " + left + " | " + right + " tokens.");
  }

  protected Either<L, R> createLeft(L obj) {
    if (Either.class.isAssignableFrom(typeToken.getRawType()))
      return Either.forLeft(obj);
    return Either.forLeft(obj);
  }

  protected Either<L, R> createRight(R obj) {
    if (Either.class.isAssignableFrom(typeToken.getRawType()))
      return (Either<L, R>) Either.forRight((Either<?, ?>) obj);
    return Either.forRight(obj);
  }

  protected static class EitherTypeArgument<T> {

    protected final TypeToken<T> typeToken;
    protected final TypeAdapter<T> adapter;
    protected final Collection<JsonToken> expectedTokens;

    public EitherTypeArgument(Gson gson, Type type) {
      this(gson, type, null);
    }

    @SuppressWarnings("unchecked")
    public EitherTypeArgument(Gson gson, Type type, TypeAdapter<T> adapter) {
      this.typeToken = (TypeToken<T>) TypeToken.get(type);
      this.adapter = (adapter != null) ? adapter :
        ((type == Object.class) ? (TypeAdapter<T>) new JsonElementTypeAdapter(gson) : gson.getAdapter(this.typeToken));
      this.expectedTokens = new HashSet<>();
      for (Type expectedType : TypeUtils.getExpectedTypes(type)) {
        Class<?> rawType = TypeToken.get(expectedType).getRawType();
        JsonToken expectedToken = getExpectedToken(rawType);
        expectedTokens.add(expectedToken);
      }
    }

    protected JsonToken getExpectedToken(Class<?> rawType) {
      if (rawType.isArray() || Collection.class.isAssignableFrom(rawType) || Tuple.class.isAssignableFrom(rawType)) {
        return JsonToken.BEGIN_ARRAY;
      }
      if (Boolean.class.isAssignableFrom(rawType)) {
        return JsonToken.BOOLEAN;
      }
      if (Number.class.isAssignableFrom(rawType) || Enum.class.isAssignableFrom(rawType)) {
        return JsonToken.NUMBER;
      }
      if (Character.class.isAssignableFrom(rawType) || String.class.isAssignableFrom(rawType)) {
        return JsonToken.STRING;
      }
      return JsonToken.BEGIN_OBJECT;
    }

    public boolean isAssignable(JsonToken jsonToken) {
      return this.expectedTokens.contains(jsonToken);
    }

    public void write(JsonWriter out, T value) throws IOException {
      this.adapter.write(out, value);
    }

    public T read(JsonReader in) throws IOException {
      return this.adapter.read(in);
    }

    public T read(JsonElement element) throws IOException {
      return this.adapter.fromJsonTree(element);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      for (JsonToken expectedToken : expectedTokens) {
        if (builder.length() != 0) {
          builder.append(" | ");
        }
        builder.append(expectedToken);
      }
      return builder.toString();
    }

  }

}

