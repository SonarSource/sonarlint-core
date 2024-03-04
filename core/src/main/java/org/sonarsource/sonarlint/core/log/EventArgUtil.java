/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.log;

import javax.annotation.Nullable;

/**
 * Inspired from logback
 *
 */
public class EventArgUtil {

  public static final Throwable extractThrowable(@Nullable Object[] argArray) {
    if (argArray == null || argArray.length == 0) {
      return null;
    }

    final Object lastEntry = argArray[argArray.length - 1];
    if (lastEntry instanceof Throwable) {
      return (Throwable) lastEntry;
    }
    return null;
  }

  /**
   * This method should be called only if {@link #successfulExtraction(Throwable)} returns true.
   *
   * @param argArray
   * @return
   */
  public static Object[] trimmedCopy(Object[] argArray) {
    final int trimemdLen = argArray.length - 1;
    Object[] trimmed = new Object[trimemdLen];
    System.arraycopy(argArray, 0, trimmed, 0, trimemdLen);
    return trimmed;
  }

  public static boolean successfulExtraction(Throwable throwable) {
    return throwable != null;
  }
}
