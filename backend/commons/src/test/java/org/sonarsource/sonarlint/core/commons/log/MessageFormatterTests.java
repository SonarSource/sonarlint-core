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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Ceki Gulcu
 */
class MessageFormatterTests {

  Integer i1 = 1;
  Integer i2 = 2;
  Integer i3 = 3;
  Integer[] ia0 = new Integer[] {i1, i2, i3};
  Integer[] ia1 = new Integer[] {10, 20, 30};

  String result;

  @Test
  void testNull() {
    result = MessageFormatter.format(null, i1).getMessage();
    assertEquals(null, result);
  }

  @Test
  void testParamaterContainingAnAnchor() {
    result = MessageFormatter.format("Value is {}.", "[{}]").getMessage();
    assertEquals("Value is [{}].", result);

    result = MessageFormatter.format("Values are {} and {}.", i1, "[{}]").getMessage();
    assertEquals("Values are 1 and [{}].", result);
  }

  @Test
  void nullParametersShouldBeHandledWithoutBarfing() {
    result = MessageFormatter.format("Value is {}.", null).getMessage();
    assertEquals("Value is null.", result);

    result = MessageFormatter.format("Val1 is {}, val2 is {}.", null, null).getMessage();
    assertEquals("Val1 is null, val2 is null.", result);

    result = MessageFormatter.format("Val1 is {}, val2 is {}.", i1, null).getMessage();
    assertEquals("Val1 is 1, val2 is null.", result);

    result = MessageFormatter.format("Val1 is {}, val2 is {}.", null, i2).getMessage();
    assertEquals("Val1 is null, val2 is 2.", result);

    result = MessageFormatter.arrayFormat("Val1 is {}, val2 is {}, val3 is {}", new Integer[] {null, null, null}).getMessage();
    assertEquals("Val1 is null, val2 is null, val3 is null", result);

    result = MessageFormatter.arrayFormat("Val1 is {}, val2 is {}, val3 is {}", new Integer[] {null, i2, i3}).getMessage();
    assertEquals("Val1 is null, val2 is 2, val3 is 3", result);

    result = MessageFormatter.arrayFormat("Val1 is {}, val2 is {}, val3 is {}", new Integer[] {null, null, i3}).getMessage();
    assertEquals("Val1 is null, val2 is null, val3 is 3", result);
  }

  @Test
  void verifyOneParameterIsHandledCorrectly() {
    result = MessageFormatter.format("Value is {}.", i3).getMessage();
    assertEquals("Value is 3.", result);

    result = MessageFormatter.format("Value is {", i3).getMessage();
    assertEquals("Value is {", result);

    result = MessageFormatter.format("{} is larger than 2.", i3).getMessage();
    assertEquals("3 is larger than 2.", result);

    result = MessageFormatter.format("No subst", i3).getMessage();
    assertEquals("No subst", result);

    result = MessageFormatter.format("Incorrect {subst", i3).getMessage();
    assertEquals("Incorrect {subst", result);

    result = MessageFormatter.format("Value is {bla} {}", i3).getMessage();
    assertEquals("Value is {bla} 3", result);

    result = MessageFormatter.format("Escaped \\{} subst", i3).getMessage();
    assertEquals("Escaped {} subst", result);

    result = MessageFormatter.format("{Escaped", i3).getMessage();
    assertEquals("{Escaped", result);

    result = MessageFormatter.format("\\{}Escaped", i3).getMessage();
    assertEquals("{}Escaped", result);

    result = MessageFormatter.format("File name is {{}}.", "App folder.zip").getMessage();
    assertEquals("File name is {App folder.zip}.", result);

    // escaping the escape character
    result = MessageFormatter.format("File name is C:\\\\{}.", "App folder.zip").getMessage();
    assertEquals("File name is C:\\App folder.zip.", result);
  }

  @Test
  void testTwoParameters() {
    result = MessageFormatter.format("Value {} is smaller than {}.", i1, i2).getMessage();
    assertEquals("Value 1 is smaller than 2.", result);

    result = MessageFormatter.format("Value {} is smaller than {}", i1, i2).getMessage();
    assertEquals("Value 1 is smaller than 2", result);

    result = MessageFormatter.format("{}{}", i1, i2).getMessage();
    assertEquals("12", result);

    result = MessageFormatter.format("Val1={}, Val2={", i1, i2).getMessage();
    assertEquals("Val1=1, Val2={", result);

    result = MessageFormatter.format("Value {} is smaller than \\{}", i1, i2).getMessage();
    assertEquals("Value 1 is smaller than {}", result);

    result = MessageFormatter.format("Value {} is smaller than \\{} tail", i1, i2).getMessage();
    assertEquals("Value 1 is smaller than {} tail", result);

    result = MessageFormatter.format("Value {} is smaller than \\{", i1, i2).getMessage();
    assertEquals("Value 1 is smaller than \\{", result);

    result = MessageFormatter.format("Value {} is smaller than {tail", i1, i2).getMessage();
    assertEquals("Value 1 is smaller than {tail", result);

    result = MessageFormatter.format("Value \\{} is smaller than {}", i1, i2).getMessage();
    assertEquals("Value {} is smaller than 1", result);
  }

  @Test
  void testExceptionIn_toString() {
    Object o = new Object() {
      @Override
      public String toString() {
        throw new IllegalStateException("a");
      }
    };
    result = MessageFormatter.format("Troublesome object {}", o).getMessage();
    assertEquals("Troublesome object [FAILED toString()]", result);

  }

  @Test
  void testNullArray() {
    var msg0 = "msg0";
    var msg1 = "msg1 {}";
    var msg2 = "msg2 {} {}";
    var msg3 = "msg3 {} {} {}";

    Object[] args = null;

    result = MessageFormatter.arrayFormat(msg0, args).getMessage();
    assertEquals(msg0, result);

    result = MessageFormatter.arrayFormat(msg1, args).getMessage();
    assertEquals(msg1, result);

    result = MessageFormatter.arrayFormat(msg2, args).getMessage();
    assertEquals(msg2, result);

    result = MessageFormatter.arrayFormat(msg3, args).getMessage();
    assertEquals(msg3, result);
  }

  // tests the case when the parameters are supplied in a single array
  @Test
  void testArrayFormat() {
    result = MessageFormatter.arrayFormat("Value {} is smaller than {} and {}.", ia0).getMessage();
    assertEquals("Value 1 is smaller than 2 and 3.", result);

    result = MessageFormatter.arrayFormat("{}{}{}", ia0).getMessage();
    assertEquals("123", result);

    result = MessageFormatter.arrayFormat("Value {} is smaller than {}.", ia0).getMessage();
    assertEquals("Value 1 is smaller than 2.", result);

    result = MessageFormatter.arrayFormat("Value {} is smaller than {}", ia0).getMessage();
    assertEquals("Value 1 is smaller than 2", result);

    result = MessageFormatter.arrayFormat("Val={}, {, Val={}", ia0).getMessage();
    assertEquals("Val=1, {, Val=2", result);

    result = MessageFormatter.arrayFormat("Val={}, {, Val={}", ia0).getMessage();
    assertEquals("Val=1, {, Val=2", result);

    result = MessageFormatter.arrayFormat("Val1={}, Val2={", ia0).getMessage();
    assertEquals("Val1=1, Val2={", result);
  }

  @Test
  void testArrayValues() {
    var p0 = i1;
    var p1 = new Integer[] {i2, i3};

    result = MessageFormatter.format("{}{}", p0, p1).getMessage();
    assertEquals("1[2, 3]", result);

    // Integer[]
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", p1}).getMessage();
    assertEquals("a[2, 3]", result);

    // byte[]
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", new byte[] {1, 2}}).getMessage();
    assertEquals("a[1, 2]", result);

    // int[]
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", new int[] {1, 2}}).getMessage();
    assertEquals("a[1, 2]", result);

    // float[]
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", new float[] {1, 2}}).getMessage();
    assertEquals("a[1.0, 2.0]", result);

    // double[]
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", new double[] {1, 2}}).getMessage();
    assertEquals("a[1.0, 2.0]", result);

    // boolean[]
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", new boolean[] {true, false}}).getMessage();
    assertEquals("a[true, false]", result);

    // short[]
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", new short[] {1, 2}}).getMessage();
    assertEquals("a[1, 2]", result);

    // char[]
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", new char[] {'a', 'b'}}).getMessage();
    assertEquals("a[a, b]", result);

    // long[]
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", new long[] {1, 2}}).getMessage();
    assertEquals("a[1, 2]", result);

  }

  @Test
  void testMultiDimensionalArrayValues() {
    var multiIntegerA = new Integer[][] {ia0, ia1};
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", multiIntegerA}).getMessage();
    assertEquals("a[[1, 2, 3], [10, 20, 30]]", result);

    var multiIntA = new int[][] {{1, 2}, {10, 20}};
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", multiIntA}).getMessage();
    assertEquals("a[[1, 2], [10, 20]]", result);

    var multiFloatA = new float[][] {{1, 2}, {10, 20}};
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", multiFloatA}).getMessage();
    assertEquals("a[[1.0, 2.0], [10.0, 20.0]]", result);

    var multiOA = new Object[][] {ia0, ia1};
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", multiOA}).getMessage();
    assertEquals("a[[1, 2, 3], [10, 20, 30]]", result);

    var _3DOA = new Object[][][] {multiOA, multiOA};
    result = MessageFormatter.arrayFormat("{}{}", new Object[] {"a", _3DOA}).getMessage();
    assertEquals("a[[[1, 2, 3], [10, 20, 30]], [[1, 2, 3], [10, 20, 30]]]", result);
  }

  @Test
  void testCyclicArrays() {
    {
      var cyclicA = new Object[1];
      cyclicA[0] = cyclicA;
      assertEquals("[[...]]", MessageFormatter.arrayFormat("{}", cyclicA).getMessage());
    }
    {
      var a = new Object[2];
      a[0] = i1;
      var c = new Object[] {i3, a};
      var b = new Object[] {i2, c};
      a[1] = b;
      assertEquals("1[2, [3, [1, [...]]]]", MessageFormatter.arrayFormat("{}{}", a).getMessage());
    }
  }

  @Test
  void testArrayThrowable() {
    FormattingTuple ft;
    var t = new Throwable();
    var ia = new Object[] {i1, i2, i3, t};

    ft = MessageFormatter.arrayFormat("Value {} is smaller than {} and {}.", ia);
    assertEquals("Value 1 is smaller than 2 and 3.", ft.getMessage());
    assertEquals(t, ft.getThrowable());

    ft = MessageFormatter.arrayFormat("{}{}{}", ia);
    assertEquals("123", ft.getMessage());
    assertEquals(t, ft.getThrowable());

    ft = MessageFormatter.arrayFormat("Value {} is smaller than {}.", ia);
    assertEquals("Value 1 is smaller than 2.", ft.getMessage());
    assertEquals(t, ft.getThrowable());

    ft = MessageFormatter.arrayFormat("Value {} is smaller than {}", ia);
    assertEquals("Value 1 is smaller than 2", ft.getMessage());
    assertEquals(t, ft.getThrowable());

    ft = MessageFormatter.arrayFormat("Val={}, {, Val={}", ia);
    assertEquals("Val=1, {, Val=2", ft.getMessage());
    assertEquals(t, ft.getThrowable());

    ft = MessageFormatter.arrayFormat("Val={}, \\{, Val={}", ia);
    assertEquals("Val=1, \\{, Val=2", ft.getMessage());
    assertEquals(t, ft.getThrowable());

    ft = MessageFormatter.arrayFormat("Val1={}, Val2={", ia);
    assertEquals("Val1=1, Val2={", ft.getMessage());
    assertEquals(t, ft.getThrowable());

    ft = MessageFormatter.arrayFormat("Value {} is smaller than {} and {}.", ia);
    assertEquals("Value 1 is smaller than 2 and 3.", ft.getMessage());
    assertEquals(t, ft.getThrowable());

    ft = MessageFormatter.arrayFormat("{}{}{}{}", ia);
    assertEquals("123{}", ft.getMessage());
    assertEquals(t, ft.getThrowable());

    ft = MessageFormatter.arrayFormat("1={}", new Object[] {i1}, t);
    assertEquals("1=1", ft.getMessage());
    assertEquals(t, ft.getThrowable());

  }
}
