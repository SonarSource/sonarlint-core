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

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

// Inspired by https://github.com/qos-ch/slf4j/blob/98f1f2f46533eba4945dda995225cf3c4017a075/slf4j-api/src/main/java/org/slf4j/helpers/MessageFormatter.java
// contributors: lizongbo: proposed special treatment of array parameter values
// Joern Huxhorn: pointed out double[] omission, suggested deep array copy
/**
 * Formats messages according to very simple substitution rules. Substitutions
 * can be made 1, 2 or more arguments.
 *
 * <p>
 * For example,
 *
 * <pre>
 * MessageFormatter.format(&quot;Hi {}.&quot;, &quot;there&quot;)
 * </pre>
 *
 * will return the string "Hi there.".
 * <p>
 * The {} pair is called the <em>formatting anchor</em>. It serves to designate
 * the location where arguments need to be substituted within the message
 * pattern.
 * <p>
 * In case your message contains the '{' or the '}' character, you do not have
 * to do anything special unless the '}' character immediately follows '{'. For
 * example,
 *
 * <pre>
 * MessageFormatter.format(&quot;Set {1,2,3} is not equal to {}.&quot;, &quot;1,2&quot;);
 * </pre>
 *
 * will return the string "Set {1,2,3} is not equal to 1,2.".
 *
 * <p>
 * If for whatever reason you need to place the string "{}" in the message
 * without its <em>formatting anchor</em> meaning, then you need to escape the
 * '{' character with '\', that is the backslash character. Only the '{'
 * character should be escaped. There is no need to escape the '}' character.
 * For example,
 *
 * <pre>
 * MessageFormatter.format(&quot;Set \\{} is not equal to {}.&quot;, &quot;1,2&quot;);
 * </pre>
 *
 * will return the string "Set {} is not equal to 1,2.".
 *
 * <p>
 * The escaping behavior just described can be overridden by escaping the escape
 * character '\'. Calling
 *
 * <pre>
 * MessageFormatter.format(&quot;File name is C:\\\\{}.&quot;, &quot;file.zip&quot;);
 * </pre>
 *
 * will return the string "File name is C:\file.zip".
 *
 * <p>
 * The formatting conventions are different than those of {@link MessageFormat}
 * which ships with the Java platform. This is justified by the fact that
 * SLF4J's implementation is 10 times faster than that of {@link MessageFormat}.
 * This local performance difference is both measurable and significant in the
 * larger context of the complete logging processing chain.
 *
 * <p>
 * See also {@link #format(String, Object)},
 * {@link #format(String, Object, Object)} and
 * {@link #arrayFormat(String, Object[])} methods for more details.
 *
 * @author Ceki G&uuml;lc&uuml;
 * @author Joern Huxhorn
 */
final class MessageFormatter {
  static final char DELIM_START = '{';
  static final char DELIM_STOP = '}';
  static final String DELIM_STR = "{}";
  private static final char ESCAPE_CHAR = '\\';

  private MessageFormatter() {
  }

  /**
   * Performs single argument substitution for the 'messagePattern' passed as
   * parameter.
   * <p>
   * For example,
   *
   * <pre>
   * MessageFormatter.format(&quot;Hi {}.&quot;, &quot;there&quot;);
   * </pre>
   *
   * will return the string "Hi there.".
   * <p>
   *
   * @param messagePattern
   *          The message pattern which will be parsed and formatted
   * @param arg
   *          The argument to be substituted in place of the formatting anchor
   * @return The formatted message
   */
  public static FormattingTuple format(String messagePattern, Object arg) {
    return arrayFormat(messagePattern, new Object[] {arg});
  }

  /**
   *
   * Performs a two argument substitution for the 'messagePattern' passed as
   * parameter.
   * <p>
   * For example,
   *
   * <pre>
   * MessageFormatter.format(&quot;Hi {}. My name is {}.&quot;, &quot;Alice&quot;, &quot;Bob&quot;);
   * </pre>
   *
   * will return the string "Hi Alice. My name is Bob.".
   *
   * @param messagePattern
   *          The message pattern which will be parsed and formatted
   * @param arg1
   *          The argument to be substituted in place of the first formatting
   *          anchor
   * @param arg2
   *          The argument to be substituted in place of the second formatting
   *          anchor
   * @return The formatted message
   */
  public static FormattingTuple format(final String messagePattern, Object arg1, Object arg2) {
    return arrayFormat(messagePattern, new Object[] {arg1, arg2});
  }

  public static FormattingTuple arrayFormat(final String messagePattern, final Object[] argArray) {
    var throwableCandidate = MessageFormatter.getThrowableCandidate(argArray);
    var args = argArray;
    if (throwableCandidate != null) {
      args = MessageFormatter.trimmedCopy(argArray);
    }
    return arrayFormat(messagePattern, args, throwableCandidate);
  }

  public static FormattingTuple arrayFormat(@Nullable final String messagePattern, @Nullable final Object[] argArray, @Nullable Throwable throwable) {

    if (messagePattern == null) {
      return new FormattingTuple(null, throwable);
    }

    if (argArray == null) {
      return new FormattingTuple(messagePattern);
    }

    var i = 0;
    int j;
    // use string builder for better multicore performance
    var sbuf = new StringBuilder(messagePattern.length() + 50);

    int L;
    for (L = 0; L < argArray.length; L++) {

      j = messagePattern.indexOf(DELIM_STR, i);

      if (j == -1) {
        // no more variables
        if (i == 0) { // this is a simple string
          return new FormattingTuple(messagePattern, throwable);
        } else { // add the tail string which contains no variables and return
          // the result.
          sbuf.append(messagePattern, i, messagePattern.length());
          return new FormattingTuple(sbuf.toString(), throwable);
        }
      } else {
        if (isEscapedDelimiter(messagePattern, j)) {
          if (!isDoubleEscaped(messagePattern, j)) {
            L--; // DELIM_START was escaped, thus should not be incremented
            sbuf.append(messagePattern, i, j - 1);
            sbuf.append(DELIM_START);
            i = j + 1;
          } else {
            // The escape character preceding the delimiter start is
            // itself escaped: "abc x:\\{}"
            // we have to consume one backward slash
            sbuf.append(messagePattern, i, j - 1);
            deeplyAppendParameter(sbuf, argArray[L], new HashMap<>());
            i = j + 2;
          }
        } else {
          // normal case
          sbuf.append(messagePattern, i, j);
          deeplyAppendParameter(sbuf, argArray[L], new HashMap<>());
          i = j + 2;
        }
      }
    }
    // append the characters following the last {} pair.
    sbuf.append(messagePattern, i, messagePattern.length());
    return new FormattingTuple(sbuf.toString(), throwable);
  }

  static boolean isEscapedDelimiter(String messagePattern, int delimiterStartIndex) {
    if (delimiterStartIndex == 0) {
      return false;
    }
    var potentialEscape = messagePattern.charAt(delimiterStartIndex - 1);
    return potentialEscape == ESCAPE_CHAR;
  }

  static boolean isDoubleEscaped(String messagePattern, int delimeterStartIndex) {
    return delimeterStartIndex >= 2 && messagePattern.charAt(delimeterStartIndex - 2) == ESCAPE_CHAR;
  }

  // special treatment of array values was suggested by 'lizongbo'
  private static void deeplyAppendParameter(StringBuilder sbuf, @Nullable Object o, Map<Object[], Object> seenMap) {
    if (o == null) {
      sbuf.append("null");
      return;
    }
    if (!o.getClass().isArray()) {
      safeObjectAppend(sbuf, o);
    } else {
      // check for primitive array types because they
      // unfortunately cannot be cast to Object[]
      if (o instanceof boolean[]) {
        booleanArrayAppend(sbuf, (boolean[]) o);
      } else if (o instanceof byte[]) {
        byteArrayAppend(sbuf, (byte[]) o);
      } else if (o instanceof char[]) {
        charArrayAppend(sbuf, (char[]) o);
      } else if (o instanceof short[]) {
        shortArrayAppend(sbuf, (short[]) o);
      } else if (o instanceof int[]) {
        intArrayAppend(sbuf, (int[]) o);
      } else if (o instanceof long[]) {
        longArrayAppend(sbuf, (long[]) o);
      } else if (o instanceof float[]) {
        floatArrayAppend(sbuf, (float[]) o);
      } else if (o instanceof double[]) {
        doubleArrayAppend(sbuf, (double[]) o);
      } else {
        objectArrayAppend(sbuf, (Object[]) o, seenMap);
      }
    }
  }

  private static void safeObjectAppend(StringBuilder sbuf, Object o) {
    try {
      var oAsString = o.toString();
      sbuf.append(oAsString);
    } catch (Throwable t) {
      sbuf.append("[FAILED toString()]");
    }
  }

  private static void objectArrayAppend(StringBuilder sbuf, Object[] a, Map<Object[], Object> seenMap) {
    sbuf.append('[');
    if (!seenMap.containsKey(a)) {
      seenMap.put(a, null);
      final var len = a.length;
      for (var i = 0; i < len; i++) {
        deeplyAppendParameter(sbuf, a[i], seenMap);
        if (i != len - 1) {
          sbuf.append(", ");
        }
      }
      // allow repeats in siblings
      seenMap.remove(a);
    } else {
      sbuf.append("...");
    }
    sbuf.append(']');
  }

  private static void booleanArrayAppend(StringBuilder sbuf, boolean[] a) {
    sbuf.append('[');
    final var len = a.length;
    for (var i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1) {
        sbuf.append(", ");
      }
    }
    sbuf.append(']');
  }

  private static void byteArrayAppend(StringBuilder sbuf, byte[] a) {
    sbuf.append('[');
    final var len = a.length;
    for (var i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1) {
        sbuf.append(", ");
      }
    }
    sbuf.append(']');
  }

  private static void charArrayAppend(StringBuilder sbuf, char[] a) {
    sbuf.append('[');
    final var len = a.length;
    for (var i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1) {
        sbuf.append(", ");
      }
    }
    sbuf.append(']');
  }

  private static void shortArrayAppend(StringBuilder sbuf, short[] a) {
    sbuf.append('[');
    final var len = a.length;
    for (var i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1) {
        sbuf.append(", ");
      }
    }
    sbuf.append(']');
  }

  private static void intArrayAppend(StringBuilder sbuf, int[] a) {
    sbuf.append('[');
    final var len = a.length;
    for (var i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1) {
        sbuf.append(", ");
      }
    }
    sbuf.append(']');
  }

  private static void longArrayAppend(StringBuilder sbuf, long[] a) {
    sbuf.append('[');
    final var len = a.length;
    for (var i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1) {
        sbuf.append(", ");
      }
    }
    sbuf.append(']');
  }

  private static void floatArrayAppend(StringBuilder sbuf, float[] a) {
    sbuf.append('[');
    final var len = a.length;
    for (var i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1) {
        sbuf.append(", ");
      }
    }
    sbuf.append(']');
  }

  private static void doubleArrayAppend(StringBuilder sbuf, double[] a) {
    sbuf.append('[');
    final var len = a.length;
    for (var i = 0; i < len; i++) {
      sbuf.append(a[i]);
      if (i != len - 1) {
        sbuf.append(", ");
      }
    }
    sbuf.append(']');
  }

  /**
   * Helper method to determine if an {@link Object} array contains a {@link Throwable} as last element
   *
   * @param argArray
   *          The arguments off which we want to know if it contains a {@link Throwable} as last element
   * @return if the last {@link Object} in argArray is a {@link Throwable} this method will return it,
   *          otherwise it returns null
   */
  public static Throwable getThrowableCandidate(final Object[] argArray) {
    return NormalizedParameters.getThrowableCandidate(argArray);
  }

  /**
   * Helper method to get all but the last element of an array
   *
   * @param argArray
   *          The arguments from which we want to remove the last element
   *
   * @return a copy of the array without the last element
   */
  public static Object[] trimmedCopy(final Object[] argArray) {
    return NormalizedParameters.trimmedCopy(argArray);
  }

}
