package org.sonar.samples.javascript.checks;

import java.io.File;
import org.junit.Test;
import org.sonar.javascript.checks.verifier.JavaScriptCheckVerifier;

/**
 * Test class to test the check implementation.
 */
public class ForbiddenFunctionUseCheckTest {

  @Test
  public void test() throws Exception {
    JavaScriptCheckVerifier.issues(new ForbiddenFunctionUseCheck(), new File("src/test/resources/checks/forbiddenFunctionUseCheck.js"))
      .next().atLine(1).withMessage("Remove the usage of this forbidden function.")
      .next().atLine(2)
      .next().atLine(4)
      .next().atLine(5)
      .noMore();
  }

}
