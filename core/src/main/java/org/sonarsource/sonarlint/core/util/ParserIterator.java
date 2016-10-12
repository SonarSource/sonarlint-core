/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;

public class ParserIterator<T> implements Iterator<T> {
  private T next = null;
  private boolean finished = false;
  private InputStream input;
  private Parser<T> parser;

  public ParserIterator(InputStream input, Parser<T> parser) {
    this.input = input;
    this.parser = parser;
  }

  @Override
  public boolean hasNext() {
    if (finished) {
      return false;
    }
    if (next == null) {
      next = computeNext();
      if (next == null) {
        finished = true;
      }
    }

    return !finished;
  }

  private T computeNext() {
    T el;

    try {
      el = parser.parseDelimitedFrom(input);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("failed to parse protobuf message", e);
    }
    if (el == null) {
      try {
        input.close();
      } catch (IOException e) {
        throw new IllegalStateException("Error closing stream", e);
      }
    }
    return el;
  }

  @Override
  public T next() {
    if (!hasNext()) {
      throw new NoSuchElementException("No next element");
    }

    T n = next;
    next = null;
    return n;
  }

}
