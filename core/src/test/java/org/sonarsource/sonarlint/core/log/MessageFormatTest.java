/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2020 SonarSource SA
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

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageFormatTest {
  @Test
  public void testFormat() {
    assertThat(MessageFormat.format("test {} msg {}", new Object[] {"a", 3})).isEqualTo("test a msg 3");
  }

  @Test
  public void testTooManyPlaceholders() {
    assertThat(MessageFormat.format("test {} msg {} {}", new Object[] {"a", 3})).isEqualTo("test a msg 3 {}");
  }

  @Test
  public void testNotEnoughPlaceholders() {
    assertThat(MessageFormat.format("test {} msg", new Object[] {"a", 3})).isEqualTo("test a msg");
  }

  @Test
  public void writeRecursiveArgumentArray() {
    assertThat(MessageFormat.format("test {} msg", new Object[] {new String[] {"s1", "s2"}})).isEqualTo("test [s1, s2] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {new double[] {1.0, 2.0}})).isEqualTo("test [1.0, 2.0] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {new float[] {1.0f, 2.0f}})).isEqualTo("test [1.0, 2.0] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {new boolean[] {true, false}})).isEqualTo("test [true, false] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {new char[] {'1', '2'}})).isEqualTo("test [1, 2] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {new byte[] {1, 2}})).isEqualTo("test [1, 2] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {new short[] {1, 2}})).isEqualTo("test [1, 2] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {new int[] {1, 2}})).isEqualTo("test [1, 2] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {new long[] {1, 2}})).isEqualTo("test [1, 2] msg");
  }

  @Test
  public void writeRecursiveArgumentList() {

    assertThat(MessageFormat.format("test {} msg", new Object[] {Arrays.asList(new String[] {"s1", "s2"})})).isEqualTo("test [s1, s2] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {Arrays.asList(new Double[] {1.0, 2.0})})).isEqualTo("test [1.0, 2.0] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {Arrays.asList(new Float[] {1.0f, 2.0f})})).isEqualTo("test [1.0, 2.0] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {Arrays.asList(new Boolean[] {true, false})})).isEqualTo("test [true, false] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {Arrays.asList(new Character[] {'1', '2'})})).isEqualTo("test [1, 2] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {Arrays.asList(new Byte[] {1, 2})})).isEqualTo("test [1, 2] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {Arrays.asList(new Short[] {1, 2})})).isEqualTo("test [1, 2] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {Arrays.asList(new Integer[] {1, 2})})).isEqualTo("test [1, 2] msg");
    assertThat(MessageFormat.format("test {} msg", new Object[] {Arrays.asList(new Long[] {1L, 2L})})).isEqualTo("test [1, 2] msg");
  }

  @Test
  public void writeRecursiveArgumentMap() {
    Map<String, String> map = new HashMap<>();
    map.put("k1", "v1");
    map.put("k2", "v2");
    assertThat(MessageFormat.format("test {} msg", new Object[] {map})).isEqualTo("test {k1=v1, k2=v2} msg");
  }

  @Test
  public void writeDate() {
    Date d = new Date();
    String expected = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(d);
    assertThat(MessageFormat.format("test {} msg", new Object[] {d})).isEqualTo("test " + expected + " msg");
  }

  @Test
  public void writeNull() {
    assertThat(MessageFormat.format(null, new Object[] {"asd"})).isEqualTo("null");
  }

  @Test
  public void errorToString() {
    Object o = new Object() {
      @Override
      public String toString() {
        throw new IllegalStateException("error");
      }
    };
    assertThat(MessageFormat.format("test {} msg", new Object[] {o})).endsWith("java.lang.IllegalStateException:error!!!] msg");
  }

}
