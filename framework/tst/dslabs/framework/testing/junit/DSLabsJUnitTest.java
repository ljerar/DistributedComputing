/*
 * Copyright (c) 2022 Ellis Michael (emichael@cs.washington.edu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dslabs.framework.testing.junit;

import com.google.common.collect.ImmutableList;
import dslabs.framework.Address;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.utils.GlobalSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

/**
 * All test classes must inherit from this class. Test classes (or their superclass) must be
 * annotated with {@code @Lab}. If and only if there are multiple test classes for a lab, each class
 * in the lab must be annotated with {@code @Part}. Test classes must be public top-level classes,
 * cannot be abstract, and must be in a package beginning with "dslabs" in order to be found by the
 * test runner in {@link DSLabsTestCore}.
 *
 * <p>Individual tests should be annotated with {@code @Test} and {@code @TestDescription}. Tests
 * should be named {@code test01Foo}, {@code test02Bar}, etc.
 */
@RunWith(DSLabsTestRunner.class)
public abstract class DSLabsJUnitTest {
  public static Address client(int i) {
    return new LocalAddress("client" + i);
  }

  public static Address server(int i) {
    return new LocalAddress("server" + i);
  }

  static boolean isInCategory(Description description, Class<?> category) {
    Category cat = description.getAnnotation(Category.class);
    return cat != null && Arrays.asList(cat.value()).contains(category);
  }

  /** Simple functional interface for a test rule that can throw exceptions. */
  @FunctionalInterface
  interface StatementTransformer {
    void evaluate(Statement base, Description description) throws Throwable;
  }

  /** Helper method to create JUnit test rules from lambdas. */
  static TestRule testRule(StatementTransformer rule) {
    return new TestRule() {
      @Override
      public Statement apply(Statement base, Description description) {
        return new Statement() {
          @Override
          public void evaluate() throws Throwable {
            rule.evaluate(base, description);
          }
        };
      }
    };
  }

  /**
   * When a test method is marked with {@link ChecksEnabled}, this test rule ensures that extra
   * checks are enabled during its execution.
   */
  @Rule
  public final TestRule enableChecks() {
    return testRule(
        (base, description) -> {
          if (description.getAnnotation(ChecksEnabled.class) != null) {
            GlobalSettings.errorChecksTemporarilyEnabled(true);
            try {
              base.evaluate();
            } finally {
              GlobalSettings.errorChecksTemporarilyEnabled(false);
            }
          } else {
            base.evaluate();
          }
        });
  }

  private List<Throwable> testFailures;

  /**
   * Throws all reported failures (even those reported through {@link #failAndContinue}) at the end
   * of a test.
   */
  @Rule(order = Rule.DEFAULT_ORDER - 1)
  public final TestRule failureMonitor() {
    return testRule(
        ((base, description) -> {
          testFailures = new ArrayList<>();
          try {
            base.evaluate();
          } catch (Throwable t) {
            testFailures.add(t);
          } finally {
            throwFailures();
          }
        }));
  }

  @SneakyThrows
  private void throwFailures() {
    if (testFailures == null || testFailures.isEmpty()) {
      return;
    }
    if (testFailures.size() == 1) {
      throw testFailures.get(0);
    }
    throw new MultipleFailureException(ImmutableList.copyOf(testFailures));
  }

  protected void failAndContinue(Throwable failure) {
    testFailures.add(failure);
  }
}
