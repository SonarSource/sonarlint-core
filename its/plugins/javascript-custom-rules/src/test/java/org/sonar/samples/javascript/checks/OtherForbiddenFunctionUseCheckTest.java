package org.sonar.samples.javascript.checks;

import java.io.File;
import org.junit.Test;
import org.sonar.javascript.checks.verifier.JavaScriptCheckVerifier;

/**
 * Test class to test the check implementation.
 */
public class OtherForbiddenFunctionUseCheckTest {

  @Test
  public void test() throws Exception {
    JavaScriptCheckVerifier.verify(new OtherForbiddenFunctionUseCheck(), new File("src/test/resources/checks/otherForbiddenFunctionUseCheck.js"));
  }

}
