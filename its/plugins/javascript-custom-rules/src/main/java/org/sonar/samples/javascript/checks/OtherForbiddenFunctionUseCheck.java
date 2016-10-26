/*
 * Copyright (C) 2009-2013 SonarSource SA
 * All rights reserved
 * mailto:contact AT sonarsource DOT com
 */
package org.sonar.samples.javascript.checks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.javascript.api.tree.Tree;
import org.sonar.plugins.javascript.api.tree.Tree.Kind;
import org.sonar.plugins.javascript.api.tree.expression.CallExpressionTree;
import org.sonar.plugins.javascript.api.tree.expression.ExpressionTree;
import org.sonar.plugins.javascript.api.tree.expression.IdentifierTree;
import org.sonar.plugins.javascript.api.visitors.SubscriptionVisitorCheck;
import org.sonar.squidbridge.annotations.SqaleConstantRemediation;
import org.sonar.squidbridge.annotations.SqaleSubCharacteristic;

/**
 * Example of a check extending {@link SubscriptionVisitorCheck}.
 * 
 * We define the kinds of the nodes that we subscribe to in {@link #nodesToVisit()}.
 * We can then override visitNode or leaveNode: these methods will be called for all nodes
 * of the kinds we subscribed to.
 */
@Rule(
  key = "S2",
  priority = Priority.MINOR,
  name = "'baz' function should not be used.",
  tags = {"convention"},
  description = "It's better not to use the 'baz' function."
  )
@SqaleSubCharacteristic(RulesDefinition.SubCharacteristics.DATA_RELIABILITY)
@SqaleConstantRemediation("5min")
public class OtherForbiddenFunctionUseCheck extends SubscriptionVisitorCheck {

  private static final Set<String> FORBIDDEN_FUNCTIONS = ImmutableSet.of("baz");

  @Override
  public List<Kind> nodesToVisit() {
    return ImmutableList.of(Kind.CALL_EXPRESSION);
  }

  @Override
  public void visitNode(Tree tree) {
    // we can do this cast because we subscribed only to nodes of kind CALL_EXPRESSION
    CallExpressionTree callExpressionTree = (CallExpressionTree) tree;
    ExpressionTree callee = callExpressionTree.callee();
    if (callee.is(Kind.IDENTIFIER_REFERENCE) && FORBIDDEN_FUNCTIONS.contains(((IdentifierTree) callee).name())) {
      addIssue(tree, "Remove the usage of this forbidden function.");
    }
  }

}
