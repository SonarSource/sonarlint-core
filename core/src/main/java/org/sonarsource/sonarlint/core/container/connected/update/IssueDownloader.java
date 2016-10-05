package org.sonarsource.sonarlint.core.container.connected.update;

import org.sonar.scanner.protocol.input.ScannerInput;

public interface IssueDownloader {

  Iterable<ScannerInput.ServerIssue> fetchIssues(String moduleKey);

}
