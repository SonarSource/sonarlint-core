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

/*
 * A class to use in place of {@link org.eclipse.lsp4j.jsonrpc.messages.Tuple} to stop depending on lsp4j types in API
 * and services.
 * See SLCORE-663 for details.
 */
public interface Tuple {

  static <F, S> Tuple.Two<F, S> two(F first, S second) {
    return new Tuple.Two<>(first, second);
  }

  /**
   * A two-tuple, i.e. a pair.
   */
  class Two<F, S> implements Tuple {

    private final F first;
    private final S second;

    public Two(F first, S second) {
      this.first = first;
      this.second = second;
    }

    public F getFirst() {
      return this.first;
    }

    public S getSecond() {
      return this.second;
    }

    @Override
    public boolean equals(final Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Tuple.Two<?, ?> other = (Tuple.Two<?, ?>) obj;
      if (this.first == null) {
        if (other.first != null) {
          return false;
        }
      } else if (!this.first.equals(other.first)) {
        return false;
      }
      if (this.second == null) {
        if (other.second != null) {
          return false;
        }
      } else if (!this.second.equals(other.second)) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      final var prime = 31;
      var result = 1;
      result = prime * result + ((this.first == null) ? 0 : this.first.hashCode());
      return prime * result + ((this.second == null) ? 0 : this.second.hashCode());
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder("Tuples.Two [").append(System.lineSeparator());
      builder.append("  first = ").append(first).append(System.lineSeparator());
      builder.append("  second = ").append(second).append(System.lineSeparator());
      return builder.append("]").toString();
    }
  }
}
