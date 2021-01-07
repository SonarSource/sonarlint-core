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
package org.sonarsource.sonarlint.core.plugin.cache;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PluginHashesTest {

  SecureRandom secureRandom = new SecureRandom();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void test_md5_hash() {
    assertThat(hash("sonar")).isEqualTo("d85e336d61f5344395c42126fac239bc");

    // compare results with commons-codec
    for (int index = 0; index < 100; index++) {
      String random = randomString();
      assertThat(hash(random)).as(random).isEqualTo(
        DigestUtils.md5Hex(random).toLowerCase());
    }
  }

  @Test
  public void test_hash_file() throws IOException {
    File f = temp.newFile();
    Files.write(f.toPath(), "sonar".getBytes(StandardCharsets.UTF_8));
    assertThat(hashFile(f)).isEqualTo("d85e336d61f5344395c42126fac239bc");
  }

  @Test
  public void test_toHex() {
    // lower-case
    assertThat(PluginHashes.toHex("aloa_bi_bop_a_loula".getBytes())).isEqualTo("616c6f615f62695f626f705f615f6c6f756c61");

    // compare results with commons-codec
    for (int index = 0; index < 100; index++) {
      String random = randomString();
      assertThat(PluginHashes.toHex(random.getBytes())).as(random).isEqualTo(
        Hex.encodeHexString(random.getBytes()).toLowerCase());
    }
  }

  @Test
  public void fail_if_file_does_not_exist() throws IOException {
    File file = temp.newFile("does_not_exist");
    FileUtils.forceDelete(file);

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Fail to compute hash of: " + file.getAbsolutePath());

    new PluginHashes().of(file.toPath());
  }

  @Test
  public void fail_if_stream_is_closed() throws Exception {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Fail to compute hash");

    InputStream input = mock(InputStream.class);
    when(input.read(any(byte[].class), anyInt(), anyInt())).thenThrow(new IllegalThreadStateException());
    new PluginHashes().of(input);
  }

  private String randomString() {
    return new BigInteger(130, secureRandom).toString(32);
  }

  private String hash(String s) {
    InputStream in = new ByteArrayInputStream(s.getBytes());
    try {
      return new PluginHashes().of(in);
    } finally {
      IOUtils.closeQuietly(in);
    }
  }

  private String hashFile(File f) {
    return new PluginHashes().of(f.toPath());
  }
}
