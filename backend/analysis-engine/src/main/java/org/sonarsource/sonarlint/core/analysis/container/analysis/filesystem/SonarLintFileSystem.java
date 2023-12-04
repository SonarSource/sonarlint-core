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
package org.sonarsource.sonarlint.core.analysis.container.analysis.filesystem;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.SortedSet;
import java.util.stream.StreamSupport;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisConfiguration;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class SonarLintFileSystem implements FileSystem {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final DefaultFilePredicates filePredicates;
  private final Path baseDir;
  private Charset encoding;

  private final InputFileIndex inputFileCache;

  public SonarLintFileSystem(AnalysisConfiguration analysisConfiguration, InputFileIndex inputFileCache) {
    this.inputFileCache = inputFileCache;
    this.baseDir = analysisConfiguration.baseDir();
    this.filePredicates = new DefaultFilePredicates();
  }

  @Override
  public File workDir() {
    LOG.warn("No workDir in SonarLint");
    return baseDir();
  }

  @Override
  public InputDir inputDir(File dir) {
    return new SonarLintInputDir(dir.toPath());
  }

  @Override
  public FilePredicates predicates() {
    return filePredicates;
  }

  @Override
  public File baseDir() {
    return baseDir.toFile();
  }

  private SonarLintFileSystem setEncoding(Charset c) {
    LOG.debug("Setting filesystem encoding: " + c);
    this.encoding = c;
    return this;
  }

  @Override
  public Charset encoding() {
    if (encoding == null) {
      setEncoding(StreamSupport.stream(inputFiles().spliterator(), false)
        .map(InputFile::charset)
        .findFirst()
        .orElse(Charset.defaultCharset()));
    }
    return encoding;
  }

  @Override
  public InputFile inputFile(FilePredicate predicate) {
    var files = inputFiles(predicate);
    var iterator = files.iterator();
    if (!iterator.hasNext()) {
      return null;
    }
    var first = iterator.next();
    if (!iterator.hasNext()) {
      return first;
    }

    var sb = new StringBuilder();
    sb.append("expected one element but was: <" + first);
    for (var i = 0; i < 4 && iterator.hasNext(); i++) {
      sb.append(", " + iterator.next());
    }
    if (iterator.hasNext()) {
      sb.append(", ...");
    }
    sb.append('>');

    throw new IllegalArgumentException(sb.toString());

  }

  public Iterable<InputFile> inputFiles() {
    return inputFiles(filePredicates.all());
  }

  @Override
  public Iterable<InputFile> inputFiles(FilePredicate predicate) {
    return OptimizedFilePredicateAdapter.create(predicate).get(inputFileCache);
  }

  @Override
  public boolean hasFiles(FilePredicate predicate) {
    return inputFiles(predicate).iterator().hasNext();
  }

  @Override
  public Iterable<File> files(FilePredicate predicate) {
    return () -> StreamSupport.stream(inputFiles(predicate).spliterator(), false)
      .map(InputFile::file)
      .iterator();
  }

  @Override
  public SortedSet<String> languages() {
    return inputFileCache.languages();
  }

  @Override
  public File resolvePath(String path) {
    throw new UnsupportedOperationException("resolvePath");
  }

}
