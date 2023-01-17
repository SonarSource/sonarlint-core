/*
 * SonarLint Core - Analysis Engine
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
package org.sonarsource.sonarlint.core.analysis.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarlint.core.commons.Language;

/**
 * InputFile as provided by client
 * @since 1.1
 */
public interface ClientInputFile {

  /**
   * The absolute file path. It needs to correspond to a file in the filesystem because some plugins don't use {@link #contents} 
   * or {@link inputStream} yet, and will attempt to access the file directly.
   * @deprecated avoid calling this method if possible, since it may require to create a temporary copy of the file
   */
  @Deprecated
  String getPath();

  /**
   * Flag an input file as test file. Analyzers may apply different rules on test files.
   */
  boolean isTest();

  /**
   * Charset to be used to read file content. If null it means the charset is unknown and analysis will likely use JVM default encoding to read the file.
   */
  @CheckForNull
  Charset getCharset();

  /**
   * Language key of the file. If not null, language detection based on the file name suffix is skipped. The file will be analyzed by a analyzer that can
   * handle the language.
   */
  @CheckForNull
  default Language language() {
    return null;
  }

  /**
   * Allow clients to pass their own object to ease mapping back to IDE file.
   */
  <G> G getClientObject();

  /**
   *  Gets a stream of the contents of the file.
   */
  InputStream inputStream() throws IOException;

  /**
   *  Gets the contents of the file. 
   */
  String contents() throws IOException;

  /**
   * Logical relative path with '/' separators. Used to apply SonarLintPathPatterns and by some analyzers. Example: 'src/main/java/Foo.java'.
   * Can be project relative path when it makes sense.
   */
  String relativePath();

  /**
   * URI to uniquely identify this file.
   */
  URI uri();

}
