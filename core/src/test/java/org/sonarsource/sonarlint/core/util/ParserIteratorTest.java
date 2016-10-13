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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonarsource.sonarlint.core.container.storage.ProtobufUtil;
import org.sonarsource.sonarlint.core.proto.Sonarlint.PluginReferences.PluginReference;

import com.google.protobuf.Parser;

public class ParserIteratorTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void test() {
    // write 3 messages to byte array
    Parser<PluginReference> parser = PluginReference.parser();
    ByteArrayOutputStream outStream = new ByteArrayOutputStream();

    List<PluginReference> refs = new LinkedList<>();
    for (int i = 0; i < 3; i++) {
      refs.add(PluginReference.newBuilder().setFilename("file" + i).build());
    }

    ProtobufUtil.writeMessages(outStream, refs.iterator());

    // create InputStream from array
    CheckCloseByteArrayInputStream in = new CheckCloseByteArrayInputStream(outStream.toByteArray());

    ParserIterator<PluginReference> it = new ParserIterator<>(in, parser);
    assertThat(it.hasNext()).isTrue();
    assertThat(it.next().getFilename()).isEqualTo("file0");

    assertThat(it.hasNext()).isTrue();
    assertThat(it.next().getFilename()).isEqualTo("file1");

    assertThat(it.hasNext()).isTrue();
    assertThat(it.next().getFilename()).isEqualTo("file2");

    assertThat(in.closed).isFalse();
    assertThat(it.hasNext()).isFalse();
    assertThat(it.hasNext()).isFalse();
    assertThat(in.closed).isTrue();
  }

  @Test
  public void testExceptionIfNoElement() {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
    Parser<PluginReference> parser = PluginReference.parser();
    ParserIterator<PluginReference> it = new ParserIterator<>(in, parser);

    exception.expect(NoSuchElementException.class);
    it.next();
  }

  @Test
  public void testErrorReading() {
    ByteArrayInputStream in = new ByteArrayInputStream("trash".getBytes(StandardCharsets.UTF_8));
    Parser<PluginReference> parser = PluginReference.parser();
    ParserIterator<PluginReference> it = new ParserIterator<>(in, parser);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("failed to parse protobuf message");
    it.next();
  }

  private static class CheckCloseByteArrayInputStream extends ByteArrayInputStream {
    public CheckCloseByteArrayInputStream(byte[] buf) {
      super(buf);
    }

    private boolean closed = false;

    @Override
    public void close() {
      closed = true;
    }
  }

}
