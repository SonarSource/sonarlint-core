/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.analysis.filesystem;

import java.io.File;
import java.nio.charset.Charset;
import java.util.stream.StreamSupport;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.InputDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;

public class SonarLintFileSystem extends DefaultFileSystem {

  private static final Logger LOG = Loggers.get(SonarLintFileSystem.class);

  private FileIndexer indexer;
  private final DefaultFilePredicates filePredicates;

  public SonarLintFileSystem(StandaloneAnalysisConfiguration analysisConfiguration, InputPathCache moduleInputFileCache, FileIndexer indexer) {
    super(analysisConfiguration.baseDir(), moduleInputFileCache);
    this.indexer = indexer;
    this.filePredicates = new DefaultFilePredicates();
    setWorkDir(analysisConfiguration.workDir());
  }

  public void index() {
    indexer.index(this);
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
  public DefaultFileSystem setEncoding(Charset c) {
    LOG.debug("Setting filesystem encoding: " + c);
    return super.setEncoding(c);
  }

  @Override
  public Charset encoding() {
    if (super.encoding() == null) {
      setEncoding(StreamSupport.stream(inputFiles().spliterator(), false)
        .map(InputFile::charset)
        .findFirst()
        .orElse(Charset.defaultCharset()));
    }
    return super.encoding();
  }
}
