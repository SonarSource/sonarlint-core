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
package org.sonarsource.sonarlint.core.rpc.protocol.common;

import java.util.function.Function;

public class Either3<T1, T2, T3> extends Either<T1, Either<T2, T3>> {

  public static <T1, T2, T3> Either3<T1, T2, T3> forLeft3(T1 first) {
    return new Either3<>(first, null);
  }

  public static <T1, T2, T3> Either3<T1, T2, T3> forRight3(Either<T2, T3> right) {
    return new Either3<>(null, right);
  }

  protected Either3(T1 left, Either<T2, T3> right) {
    super(left, right);
  }

  public T1 getFirst() {
    return getLeft();
  }

  public T2 getSecond() {
    Either<T2, T3> right = getRight();
    if (right == null) {
      return null;
    } else {
      return right.getLeft();
    }
  }

  public T3 getThird() {
    Either<T2, T3> right = getRight();
    if (right == null) {
      return null;
    } else {
      return right.getRight();
    }
  }

  @Override
  public Object get() {
    if (isRight()) {
      return getRight().get();
    }
    return super.get();
  }

  public boolean isFirst() {
    return isLeft();
  }

  public boolean isSecond() {
    return isRight() && getRight().isLeft();
  }

  public boolean isThird() {
    return isRight() && getRight().isRight();
  }

  public <T> T map(
    Function<? super T1, ? extends T> mapFirst,
    Function<? super T2, ? extends T> mapSecond,
    Function<? super T3, ? extends T> mapThird) {
    if (isFirst()) {
      return mapFirst.apply(getFirst());
    }
    if (isSecond()) {
      return mapSecond.apply(getSecond());
    }
    if (isThird()) {
      return mapThird.apply(getThird());
    }
    return null;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("Either3 [").append(System.lineSeparator());
    builder.append("  first = ").append(getFirst()).append(System.lineSeparator());
    builder.append("  second = ").append(getSecond()).append(System.lineSeparator());
    builder.append("  third = ").append(getThird()).append(System.lineSeparator());
    return builder.append("]").toString();
  }

}
