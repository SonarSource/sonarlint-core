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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;

/*
 * A class to use in place of {@link org.eclipse.lsp4j.jsonrpc.json.adapters.TypeUtils} to stop depending on lsp4j types in API
 * and services.
 * See SLCORE-663 for details.
 */
public final class TypeUtils {

  private TypeUtils() {}

  /**
   * Determine the actual type arguments of the given type token with regard to the given target type.
   */
  public static Type[] getElementTypes(TypeToken<?> typeToken, Class<?> targetType) {
    return getElementTypes(typeToken.getType(), typeToken.getRawType(), targetType);
  }

  private static Type[] getElementTypes(Type type, Class<?> rawType, Class<?> targetType) {
    if (targetType.equals(rawType) && type instanceof ParameterizedType) {
      Type mappedType;
      if (type instanceof ParameterizedTypeImpl) {
        mappedType = type;
      } else {
        mappedType = getMappedType(type, Collections.emptyMap());
      }
      return ((ParameterizedType) mappedType).getActualTypeArguments();
    }
    // Map the parameters of the raw type to the actual type arguments
    Map<String, Type> varMapping = createVariableMapping(type, rawType);
    if (targetType.isInterface()) {
      // Look for superinterfaces that extend the target interface
      Class<?>[] interfaces = rawType.getInterfaces();
      for (var i = 0; i < interfaces.length; i++) {
        if (Collection.class.isAssignableFrom(interfaces[i])) {
          var genericInterface = rawType.getGenericInterfaces()[i];
          var mappedInterface = getMappedType(genericInterface, varMapping);
          return getElementTypes(mappedInterface, interfaces[i], targetType);
        }
      }
    }
    if (!rawType.isInterface()) {
      // Visit the superclass if it extends the target class / implements the target interface
      Class<?> rawSupertype = rawType.getSuperclass();
      if (targetType.isAssignableFrom(rawSupertype)) {
        var genericSuperclass = rawType.getGenericSuperclass();
        var mappedSuperclass = getMappedType(genericSuperclass, varMapping);
        return getElementTypes(mappedSuperclass, rawSupertype, targetType);
      }
    }
    // No luck, return an array of Object types
    var result = new Type[targetType.getTypeParameters().length];
    Arrays.fill(result, Object.class);
    return result;
  }

  private static <T> Map<String, Type> createVariableMapping(Type type, Class<T> rawType) {
    if (type instanceof ParameterizedType) {
      TypeVariable<Class<T>>[] vars = rawType.getTypeParameters();
      Type[] args = ((ParameterizedType) type).getActualTypeArguments();
      Map<String, Type> newVarMapping = new HashMap<>(capacity(vars.length));
      for (var i = 0; i < vars.length; i++) {
        Type actualType = Object.class;
        if (i < args.length) {
          actualType = args[i];
          if (actualType instanceof WildcardType) {
            actualType = ((WildcardType) actualType).getUpperBounds()[0];
          }
        }
        newVarMapping.put(vars[i].getName(), actualType);
      }
      return newVarMapping;
    }
    return Collections.emptyMap();
  }

  private static int capacity(int expectedSize) {
    if (expectedSize < 3) {
      return expectedSize + 1;
    } else {
      return expectedSize + expectedSize / 3;
    }
  }

  private static Type getMappedType(Type type, Map<String, Type> varMapping) {
    if (type instanceof TypeVariable) {
      String name = ((TypeVariable<?>) type).getName();
      if (varMapping.containsKey(name)) {
        return varMapping.get(name);
      }
    }
    if (type instanceof WildcardType) {
      return getMappedType(((WildcardType) type).getUpperBounds()[0], varMapping);
    }
    if (type instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) type;
      var origArgs = pt.getActualTypeArguments();
      var mappedArgs = new Type[origArgs.length];
      for (var i = 0; i < origArgs.length; i++) {
        mappedArgs[i] = getMappedType(origArgs[i], varMapping);
      }
      return new ParameterizedTypeImpl(pt, mappedArgs);
    }
    return type;
  }

  private static class ParameterizedTypeImpl implements ParameterizedType {

    private final Type ownerType;
    private final Type rawType;
    private final Type[] actualTypeArguments;

    ParameterizedTypeImpl(ParameterizedType original, Type[] typeArguments) {
      this(original.getOwnerType(), original.getRawType(), typeArguments);
    }

    ParameterizedTypeImpl(Type ownerType, Type rawType, Type[] typeArguments) {
      this.ownerType = ownerType;
      this.rawType = rawType;
      this.actualTypeArguments = typeArguments;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }

    @Override
    public String toString() {
      var result = new StringBuilder();
      if (ownerType != null) {
        result.append(toString(ownerType));
        result.append('$');
      }
      result.append(toString(rawType));
      result.append('<');
      for (var i = 0; i < actualTypeArguments.length; i++) {
        if (i > 0) {
          result.append(", ");
        }
        result.append(toString(actualTypeArguments[i]));
      }
      result.append('>');
      return result.toString();
    }

    private static String toString(Type type) {
      if (type instanceof Class<?>) {
        return ((Class<?>) type).getName();
      } else {
        return String.valueOf(type);
      }
    }

  }

  /**
   * Return all possible types that can be expected when an element of the given type is parsed.
   * If the type satisfies {@link #isEither(Type)}, a list of the corresponding type arguments is returned,
   * otherwise a list containg the type itself is returned. Type parameters are <em>not</em> resolved
   * by this method (use {@link #getElementTypes(TypeToken, Class)} to get resolved parameters).
   */
  public static Collection<Type> getExpectedTypes(Type type) {
    Collection<Type> result = new ArrayList<>();
    collectExpectedTypes(type, result);
    return result;
  }

  private static void collectExpectedTypes(Type type, Collection<Type> types) {
    if (isEither(type)) {
      if (type instanceof ParameterizedType) {
        for (Type typeArgument : ((ParameterizedType) type).getActualTypeArguments()) {
          collectExpectedTypes(typeArgument, types);
        }
      }
      if (type instanceof Class) {
        for (Type typeParameter : ((Class<?>) type).getTypeParameters()) {
          collectExpectedTypes(typeParameter, types);
        }
      }
    } else {
      types.add(type);
    }
  }

  /**
   * Test whether the given type is Either.
   */
  public static boolean isEither(Type type) {
    if (type instanceof ParameterizedType) {
      return isEither(((ParameterizedType) type).getRawType());
    }
    if (type instanceof Class) {
      return Either.class.isAssignableFrom((Class<?>) type);
    }
    return false;
  }

  /**
   * Test whether the given type is a two-tuple (pair).
   */
  public static boolean isTwoTuple(Type type) {
    if (type instanceof ParameterizedType) {
      return isTwoTuple(((ParameterizedType) type).getRawType());
    }
    if (type instanceof Class) {
      return Tuple.Two.class.isAssignableFrom((Class<?>) type);
    }
    return false;
  }

}
