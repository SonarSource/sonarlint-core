package org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SonarProjectDtoTest {

  @Test
  void should_have_object_methods() {
    var one = new SonarProjectDto("mySearchTerm", "project");
    var other = new SonarProjectDto("mySearchTerm", "project");

    assertThat(one)
      .isEqualTo(other)
      .hasSameHashCodeAs(other)
      .hasToString("SonarProjectDto{key='mySearchTerm', name='project'}");
  }
}
