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
package org.sonarsource.sonarlint.core.analyzer.sensor;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.internal.DefaultTextPointer;
import org.sonar.api.batch.fs.internal.DefaultTextRange;
import org.sonar.api.batch.sensor.issue.Issue.Flow;
import org.sonar.api.batch.sensor.issue.IssueLocation;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultSensorStorageTest {

  @Test
  public void keepOnlyFlowsLocationOnTheSameFile() {
    InputComponent currentFile = mock(InputComponent.class);
    String currentFileKey = "currentFileKey";
    when(currentFile.key()).thenReturn(currentFileKey);
    InputComponent anotherFile = mock(InputComponent.class);
    when(anotherFile.key()).thenReturn("anotherFileKey");

    IssueLocation location1 = mock(IssueLocation.class);
    when(location1.textRange()).thenReturn(new DefaultTextRange(new DefaultTextPointer(4, 4), new DefaultTextPointer(5, 5)));
    when(location1.message()).thenReturn("location1");
    when(location1.inputComponent()).thenReturn(currentFile);

    IssueLocation location2 = mock(IssueLocation.class);
    when(location2.textRange()).thenReturn(new DefaultTextRange(new DefaultTextPointer(6, 6), new DefaultTextPointer(7, 7)));
    when(location2.message()).thenReturn("location2");
    when(location2.inputComponent()).thenReturn(currentFile);

    IssueLocation extLocation = mock(IssueLocation.class);
    when(extLocation.textRange()).thenReturn(new DefaultTextRange(new DefaultTextPointer(6, 6), new DefaultTextPointer(7, 7)));
    when(extLocation.message()).thenReturn("extLocation");
    when(extLocation.inputComponent()).thenReturn(anotherFile);

    Flow flow1 = mock(Flow.class);
    when(flow1.locations()).thenReturn(asList(location1, extLocation), singletonList(extLocation));

    Flow flow2 = mock(Flow.class);
    when(flow2.locations()).thenReturn(asList(location1, extLocation, location2));

    Flow flow3 = mock(Flow.class);
    when(flow3.locations()).thenReturn(asList(extLocation));

    List<org.sonarsource.sonarlint.core.client.api.common.analysis.Issue.Flow> flows = DefaultSensorStorage.mapFlows(Arrays.asList(flow1, flow2, flow3), currentFile);

    assertThat(flows).hasSize(2);
    assertThat(flows.get(0).locations()).hasSize(1);
    assertThat(flows.get(0).locations().get(0)).extracting("startLine", "startLineOffset", "endLine", "endLineOffset", "message")
      .containsExactly(4, 4, 5, 5, "location1");

    assertThat(flows.get(1).locations()).hasSize(2);
    assertThat(flows.get(1).locations().get(0)).extracting("startLine", "startLineOffset", "endLine", "endLineOffset", "message")
      .containsExactly(4, 4, 5, 5, "location1");
    assertThat(flows.get(1).locations().get(1)).extracting("startLine", "startLineOffset", "endLine", "endLineOffset", "message")
      .containsExactly(6, 6, 7, 7, "location2");
  }

}
