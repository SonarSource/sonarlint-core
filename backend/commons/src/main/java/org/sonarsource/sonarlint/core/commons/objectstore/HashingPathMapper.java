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
package org.sonarsource.sonarlint.core.commons.objectstore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Map strings to unique relative filesystem paths using their SHA1 hash.
 */
public class HashingPathMapper implements PathMapper<String> {

  private static final String HEX_LETTERS = "0123456789abcdef";
  private static final String HASHING_ALGORITHM = "SHA1";
  private static final int HASH_LENGTH = 40;

  private final Path base;
  private final int levels;

  public HashingPathMapper(Path base, int levels) {
    if (levels < 1) {
      throw new IllegalArgumentException("levels must be > 0");
    }
    if (levels > HASH_LENGTH) {
      throw new IllegalArgumentException("levels must be less than the length of the generated hash: " + HASH_LENGTH);
    }

    this.base = base;
    this.levels = levels;
  }

  @Override
  public Path apply(String key) {
    var hashedHexString = toHexString(toHash(key));

    var path = base;
    for (var i = 0; i < levels; i++) {
      path = path.resolve(hashedHexString.substring(i, i + 1));
    }
    return path.resolve(hashedHexString);
  }

  private static byte[] toHash(String key) {
    try {
      var digest = MessageDigest.getInstance(HASHING_ALGORITHM);
      digest.update(key.getBytes(StandardCharsets.UTF_8));
      return digest.digest();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("could not get hashing algoritm: " + HASHING_ALGORITHM, e);
    }
  }

  private static String toHexString(byte[] bytes) {
    final var hex = new StringBuilder(2 * bytes.length);
    for (byte b : bytes) {
      hex.append(HEX_LETTERS.charAt((b & 0xF0) >> 4)).append(HEX_LETTERS.charAt(b & 0x0F));
    }
    return hex.toString();
  }
}
