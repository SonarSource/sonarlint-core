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

/*
 * A class to use in place of {@link org.eclipse.lsp4j.jsonrpc.messages.Either} to stop depending on lsp4j types in API
 * and services.
 * See SLCORE-663 for details.
 */
public class Either<L, R> {

  public static <L, R> Either<L, R> forLeft(L left) {
    return new Either<>(left, null);
  }

  public static <L, R> Either<L, R> forRight(R right) {
    return new Either<>(null, right);
  }

  private final L left;
  private final R right;

  protected Either(L left, R right) {
    super();
    this.left = left;
    this.right = right;
  }

  public L getLeft() {
    return left;
  }

  public R getRight() {
    return right;
  }

  public Object get() {
    if (left != null) {
      return left;
    }
    if (right != null) {
      return right;
    }
    return null;
  }

  public boolean isLeft() {
    return left != null;
  }

  public boolean isRight() {
    return right != null;
  }

  public <T> T map(
    Function<? super L, ? extends T> mapLeft,
    Function<? super R, ? extends T> mapRight) {
    if (isLeft()) {
      return mapLeft.apply(getLeft());
    }
    if (isRight()) {
      return mapRight.apply(getRight());
    }
    return null;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Either<?, ?>) {
      Either<?, ?> other = (Either<?, ?>) obj;
      return (this.left == other.left && this.right == other.right)
        || (this.left != null && other.left != null && this.left.equals(other.left))
        || (this.right != null && other.right != null && this.right.equals(other.right));
    }
    return false;
  }

  @Override
  public int hashCode() {
    if (this.left != null) {
      return this.left.hashCode();
    }
    if (this.right != null) {
      return this.right.hashCode();
    }
    return 0;
  }

  public String toString() {
    StringBuilder builder = new StringBuilder("Either [").append(System.lineSeparator());
    builder.append("  left = ").append(left).append(System.lineSeparator());
    builder.append("  right = ").append(right).append(System.lineSeparator());
    return builder.append("]").toString();
  }
}
