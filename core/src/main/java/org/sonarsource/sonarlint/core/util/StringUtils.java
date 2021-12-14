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
package org.sonarsource.sonarlint.core.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.annotation.Nullable;

public class StringUtils {

  private StringUtils() {
  }

  public static String urlEncode(String toEncode) {
    try {
      return URLEncoder.encode(toEncode, StandardCharsets.UTF_8.name());
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Should never happen", e);
    }
  }

  public static boolean isEmpty(@Nullable String str) {
    return str == null || str.isEmpty();
  }

  public static boolean isNotEmpty(String str) {
    return !StringUtils.isEmpty(str);
  }

  public static boolean isBlank(@Nullable String str) {
    int strLen;
    if (str == null || (strLen = str.length()) == 0) {
      return true;
    }
    for (var i = 0; i < strLen; i++) {
      if (!Character.isWhitespace(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  public static String md5(String s) {
    try {
      return md5(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    } catch (IOException e) {
      // Should never occur
      throw new IllegalStateException(e);
    }
  }

  public static String md5(InputStream data) throws IOException {
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      // Should never occur
      throw new IllegalStateException(e);
    }
    var bufLen = 8192;
    final var buffer = new byte[bufLen];
    int read = data.read(buffer, 0, bufLen);

    while (read > -1) {
      md.update(buffer, 0, read);
      read = data.read(buffer, 0, bufLen);
    }

    byte[] array = md.digest();
    var sb = new StringBuilder();
    for (var i = 0; i < array.length; ++i) {
      sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1, 3));
    }
    return sb.toString();
  }

  public static boolean equalsIgnoringTrailingSlash(String aString, String anotherString) {
    return withTrailingSlash(aString).equals(withTrailingSlash(anotherString));
  }

  private static String withTrailingSlash(String str) {
    if (!str.endsWith("/")) {
      return str + '/';
    }
    return str;
  }

}
