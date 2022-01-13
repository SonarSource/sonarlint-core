/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFileFilter;
import org.sonar.api.utils.MessageException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.api.sonarlint.SonarLintSide;
import org.sonarsource.sonarlint.core.client.api.common.AbstractAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.container.analysis.issue.ignore.scanner.IssueExclusionsLoader;
import org.sonarsource.sonarlint.core.container.model.DefaultAnalysisResult;
import org.sonarsource.sonarlint.core.util.ProgressReport;

/**
 * Index input files into {@link InputFileCache}.
 */
@SonarLintSide
public class FileIndexer {

  private static final Logger LOG = Loggers.get(FileIndexer.class);

  private final InputFileBuilder inputFileBuilder;
  private final AbstractAnalysisConfiguration analysisConfiguration;
  private final DefaultAnalysisResult analysisResult;
  private final InputFileFilter[] filters;
  private final IssueExclusionsLoader issueExclusionsLoader;
  private final InputFileCache inputFileCache;

  private ProgressReport progressReport;

  public FileIndexer(InputFileCache inputFileCache, InputFileBuilder inputFileBuilder, AbstractAnalysisConfiguration analysisConfiguration,
    DefaultAnalysisResult analysisResult, IssueExclusionsLoader issueExclusionsLoader,
    InputFileFilter[] filters) {
    this.inputFileCache = inputFileCache;
    this.inputFileBuilder = inputFileBuilder;
    this.analysisConfiguration = analysisConfiguration;
    this.analysisResult = analysisResult;
    this.issueExclusionsLoader = issueExclusionsLoader;
    this.filters = filters;
  }

  public FileIndexer(InputFileCache inputFileCache, InputFileBuilder inputFileBuilder, AbstractAnalysisConfiguration analysisConfiguration,
    DefaultAnalysisResult analysisResult, IssueExclusionsLoader issueExclusionsLoader) {
    this(inputFileCache, inputFileBuilder, analysisConfiguration, analysisResult, issueExclusionsLoader, new InputFileFilter[0]);
  }

  public void index() {
    progressReport = new ProgressReport("Report about progress of file indexation", TimeUnit.SECONDS.toMillis(10));
    progressReport.start("Index files");

    var progress = new Progress();

    try {
      indexFiles(inputFileCache, progress, analysisConfiguration.inputFiles());
    } catch (Exception e) {
      progressReport.stop(null);
      throw e;
    }
    int totalIndexed = progress.count();
    progressReport.stop(totalIndexed + " " + pluralizeFiles(totalIndexed) + " indexed");
    analysisResult.setIndexedFileCount(totalIndexed);
  }

  private static String pluralizeFiles(int count) {
    return count == 1 ? "file" : "files";
  }

  private void indexFiles(InputFileCache inputFileCache, Progress progress, Iterable<ClientInputFile> inputFiles) {
    for (ClientInputFile file : inputFiles) {
      indexFile(inputFileCache, progress, file);
    }
  }

  private void indexFile(InputFileCache inputFileCache, Progress progress, ClientInputFile file) {
    SonarLintInputFile inputFile = inputFileBuilder.create(file);
    if (accept(inputFile)) {
      analysisResult.setLanguageForFile(file, inputFile.getLanguage());
      indexFile(inputFileCache, progress, inputFile);
      issueExclusionsLoader.addMulticriteriaPatterns(inputFile);
    }
  }

  private void indexFile(final InputFileCache inputFileCache, final Progress status, final SonarLintInputFile inputFile) {
    inputFileCache.doAdd(inputFile);
    status.markAsIndexed(inputFile);
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
    private final Set<URI> indexed = new HashSet<>();

    void markAsIndexed(SonarLintInputFile inputFile) {
      if (indexed.contains(inputFile.uri())) {
        throw MessageException.of("File " + inputFile + " can't be indexed twice.");
      }
      indexed.add(inputFile.uri());
      int size = indexed.size();
      progressReport.message(() -> size + " files indexed...  (last one was " + inputFile.uri() + ")");
    }

    int count() {
      return indexed.size();
    }
  }

}
