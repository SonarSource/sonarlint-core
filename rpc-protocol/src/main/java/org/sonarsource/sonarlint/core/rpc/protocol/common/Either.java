/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2025 SonarSource SA
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

import java.util.Objects;
import java.util.function.Function;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

/*
 * A class to use in place of {@link org.eclipse.lsp4j.jsonrpc.messages.Either} to stop depending on lsp4j types in API
 * and services.
 * See SLCORE-663 for details.
 */
public class Either<L, R> {

  private final org.eclipse.lsp4j.jsonrpc.messages.Either<L, R> lsp4jEither;

  public Either(org.eclipse.lsp4j.jsonrpc.messages.Either<L, R> lsp4jEither) {
    this.lsp4jEither = lsp4jEither;
  }

  public static <L, R> Either<L, R> forLeft(@NonNull L left) {
    return new Either<>(org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(left));
  }

  public static <L, R> Either<L, R> forRight(@NonNull R right) {
    return new Either<>(org.eclipse.lsp4j.jsonrpc.messages.Either.forRight(right));
  }

  public boolean isLeft() {
    return lsp4jEither.isLeft();
  }

  public boolean isRight() {
    return lsp4jEither.isRight();
  }

  public L getLeft() {
    return lsp4jEither.getLeft();
  }

  public R getRight() {
    return lsp4jEither.getRight();
  }

  public <T> T map(
    @NonNull Function<? super L, ? extends T> mapLeft,
    @NonNull Function<? super R, ? extends T> mapRight) {
    return lsp4jEither.map(mapLeft, mapRight);
  }

  public <R1, R2> Either<R1, R2> mapToEither(
    @NonNull Function<? super L, ? extends R1> mapLeft,
    @NonNull Function<? super R, ? extends R2> mapRight) {
    return isLeft() ? Either.forLeft(mapLeft.apply(getLeft())) : Either.forRight(mapRight.apply(getRight()));
  }

  public org.eclipse.lsp4j.jsonrpc.messages.Either<L, R> getLsp4jEither() {
    return lsp4jEither;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Either<?, ?> either = (Either<?, ?>) o;
    return Objects.equals(lsp4jEither, either.lsp4jEither);
  }

  @Override
  public int hashCode() {
    return Objects.hash(lsp4jEither);
  }

  @Override
  public String toString() {
    return lsp4jEither.toString();
  }
}
