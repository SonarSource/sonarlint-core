/*
 * SonarLint Core - Commons
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
package org.sonarsource.sonarlint.core.commons.log;

import javax.annotation.Nullable;

/**
 * Holds normalized calling call parameters.
 *
 * Includes utility methods such as {@link #normalize(String, Object[], Throwable)} to help the normalization of parameters.
 *
 * @author ceki
 * @since 2.0
 */
class NormalizedParameters {

  private NormalizedParameters() {
  }

  /**
   * Helper method to determine if an {@link Object} array contains a
   * {@link Throwable} as last element
   *
   * @param argArray The arguments off which we want to know if it contains a
   *                 {@link Throwable} as last element
   * @return if the last {@link Object} in argArray is a {@link Throwable} this
   *         method will return it, otherwise it returns null
   */
  public static Throwable getThrowableCandidate(@Nullable final Object[] argArray) {
    if (argArray == null || argArray.length == 0) {
      return null;
    }

    final var lastEntry = argArray[argArray.length - 1];
    if (lastEntry instanceof Throwable) {
      return (Throwable) lastEntry;
    }

    return null;
  }

  /**
   * Helper method to get all but the last element of an array
   *
   * @param argArray The arguments from which we want to remove the last element
   *
   * @return a copy of the array without the last element
   */
  public static Object[] trimmedCopy(@Nullable final Object[] argArray) {
    if (argArray == null || argArray.length == 0) {
      throw new IllegalStateException("non-sensical empty or null argument array");
    }

    final var trimmedLen = argArray.length - 1;

    var trimmed = new Object[trimmedLen];

    if (trimmedLen > 0) {
      System.arraycopy(argArray, 0, trimmed, 0, trimmedLen);
    }

    return trimmed;
  }

}
