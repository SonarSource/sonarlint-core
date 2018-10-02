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

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFileFilter;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.container.model.DefaultAnalysisResult;
import org.sonarsource.sonarlint.core.util.ProgressReport;

/**
 * Index input files into {@link InputPathCache}.
 */
@SonarLintSide
public class FileIndexer {

  private static final Logger LOG = Loggers.get(FileIndexer.class);

  private final InputFileBuilder inputFileBuilder;
  private final StandaloneAnalysisConfiguration analysisConfiguration;
  private final DefaultAnalysisResult analysisResult;
  private final InputFileFilter[] filters;

  private ProgressReport progressReport;

  public FileIndexer(InputFileBuilder inputFileBuilder, StandaloneAnalysisConfiguration analysisConfiguration,
    DefaultAnalysisResult analysisResult,
    InputFileFilter[] filters) {
    this.inputFileBuilder = inputFileBuilder;
    this.analysisConfiguration = analysisConfiguration;
    this.analysisResult = analysisResult;
    this.filters = filters;
  }

  public FileIndexer(InputFileBuilder inputFileBuilder, StandaloneAnalysisConfiguration analysisConfiguration,
    DefaultAnalysisResult analysisResult) {
    this(inputFileBuilder, analysisConfiguration, analysisResult, new InputFileFilter[0]);
  }

  void index(SonarLintFileSystem fileSystem) {
    progressReport = new ProgressReport("Report about progress of file indexation", TimeUnit.SECONDS.toMillis(10));
    progressReport.start("Index files");

    Progress progress = new Progress();

    try {
      indexFiles(fileSystem, progress, analysisConfiguration.inputFiles());
    } catch (Exception e) {
      progressReport.stop(null);
      throw e;
    }
    progressReport.stop(progress.count() + " files indexed");
    analysisResult.setIndexedFileCount(progress.count());
  }

  private void indexFiles(SonarLintFileSystem fileSystem, Progress progress, Iterable<ClientInputFile> inputFiles) {
    for (ClientInputFile file : inputFiles) {
      indexFile(fileSystem, progress, file);
    }
  }

  private void indexFile(SonarLintFileSystem fileSystem, Progress progress, ClientInputFile file) {
    SonarLintInputFile inputFile = inputFileBuilder.create(file);
    if (accept(inputFile)) {
      analysisResult.setLanguageForFile(file, inputFile.language());
      indexFile(fileSystem, progress, inputFile);
    }
  }

  private void indexFile(final SonarLintFileSystem fs, final Progress status, final SonarLintInputFile inputFile) {
    fs.add(inputFile);
    status.markAsIndexed(inputFile);
    SonarLintInputDir inputDir = new SonarLintInputDir(inputFile.path().getParent());
    fs.add(inputDir);
  }

  private boolean accept(InputFile indexedFile) {
    // InputFileFilter extensions. Might trigger generation of metadata
    for (InputFileFilter filter : filters) {
      if (!filter.accept(indexedFile)) {
        LOG.debug("'{}' excluded by {}", indexedFile, filter.getClass().getName());
        return false;
      }
    }
    return true;
  }

  private class Progress {
    private final Set<Path> indexed = new HashSet<>();

    void markAsIndexed(SonarLintInputFile inputFile) {
      if (indexed.contains(inputFile.path())) {
        throw MessageException.of("File " + inputFile + " can't be indexed twice.");
      }
      indexed.add(inputFile.path());
      int size = indexed.size();
      progressReport.message(() -> size + " files indexed...  (last one was " + inputFile.absolutePath() + ")");
    }

    int count() {
      return indexed.size();
    }
  }

}
