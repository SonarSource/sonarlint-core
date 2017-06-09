/*
 * SonarLint Core - Client API
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.common;

import org.junit.Test;
import org.sonarsource.sonarlint.core.client.api.exceptions.MessageException;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarLintWrappedExceptionTest {

  @Test
  public void wrap() {
    try {
      throw SonarLintWrappedException.wrap(new MyCustomException("Foo"));
    } catch (Exception e) {
      assertThat(e).hasMessage("Foo").hasNoCause().isInstanceOf(SonarLintWrappedException.class);
    }

    try {
      throw SonarLintWrappedException.wrap(new MyCustomException("Foo", new MyCustomException("Cause")));
    } catch (Exception e) {
      assertThat(e).hasMessage("Foo").isInstanceOf(SonarLintWrappedException.class).hasCauseInstanceOf(SonarLintWrappedException.class);
    }
  }

  private static class MyCustomException extends RuntimeException {

    public MyCustomException(String message) {
      super(message);
    }

    public MyCustomException(String message, Throwable cause) {
      super(message, cause);
    }

  }

  @Test
  public void extractMessageException() {
    MessageException e = new MessageException("a");
    Exception a = new IllegalStateException("a", new IllegalStateException("b", e));
    assertThat(SonarLintWrappedException.wrap(a)).isEqualTo(e);
  }
}
