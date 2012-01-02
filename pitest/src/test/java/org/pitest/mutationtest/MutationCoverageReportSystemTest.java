/*
 * Copyright 2010 Henry Coles
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.pitest.mutationtest;

import static org.pitest.mutationtest.results.DetectionStatus.KILLED;
import static org.pitest.mutationtest.results.DetectionStatus.NO_COVERAGE;
import static org.pitest.mutationtest.results.DetectionStatus.SURVIVED;
import static org.pitest.mutationtest.results.DetectionStatus.TIMED_OUT;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Ignore;
import org.junit.Test;
import org.pitest.coverage.execute.CoverageOptions;
import org.pitest.coverage.execute.LaunchOptions;
import org.pitest.extension.Configuration;
import org.pitest.functional.predicate.True;
import org.pitest.help.PitHelpError;
import org.pitest.internal.IsolationUtils;
import org.pitest.internal.classloader.ClassPathRoot;
import org.pitest.junit.JUnitCompatibleConfiguration;
import org.pitest.mutationtest.instrument.JarCreatingJarFinder;
import org.pitest.testng.TestGroupConfig;
import org.pitest.testng.TestNGConfiguration;
import org.pitest.util.FileUtil;
import org.pitest.util.JavaAgent;

import com.example.CoveredByEasyMock;
import com.example.CoveredByJMockit;
import com.example.FailsTestWhenEnvVariableSetTestee;
import com.example.FullyCoveredTestee;
import com.example.FullyCoveredTesteeTest;
import com.example.MultipleMutations;

public class MutationCoverageReportSystemTest extends ReportTestBase {

  @Test
  public void shouldPickRelevantTestsAndKillMutationsBasedOnCoverageData() {
    this.data.setTargetClasses(predicateFor("com.example.FullyCovered*"));
    this.data.setVerbose(true);
    createAndRun();
    verifyResults(KILLED);
  }

  @Test
  public void shouldPickRelevantTestsAndKillMutationsBasedOnCoverageDataWhenLimitedByClassReach() {
    this.data.setDependencyAnalysisMaxDistance(2);
    this.data.setTargetTests(predicateFor("com.example.*FullyCovered*"));
    this.data.setTargetClasses(predicateFor("com.example.FullyCovered*"));
    createAndRun();
    verifyResults(KILLED);
  }

  @Test
  public void shouldReportUnCoveredMutations() {
    this.data.setTargetClasses(predicateFor("com.example.PartiallyCovered*"));
    createAndRun();
    verifyResults(KILLED, NO_COVERAGE);
  }

  @Test
  public void shouldReportSurvivingMutations() {
    this.data
        .setTargetClasses(predicateFor("com.example.CoveredButOnlyPartiallyTested*"));
    createAndRun();
    verifyResults(KILLED, SURVIVED);
  }

  @Test
  public void shouldKillMutationsInStaticInitializersWhenThereIsCoverageAndMutateStaticFlagIsSet() {
    this.data.setMutateStaticInitializers(true);
    this.data
        .setTargetClasses(predicateFor("com.example.HasMutableStaticInitializer*"));
    createAndRun();
    verifyResults(KILLED);
  }

  @Test
  public void shouldNotCreateMutationsInStaticInitializersWhenFlagNotSet() {
    this.data.setMutateStaticInitializers(false);
    this.data
        .setTargetClasses(predicateFor("com.example.HasMutableStaticInitializer*"));
    createAndRun();
    verifyResults();
  }

  @Test(expected = PitHelpError.class)
  public void shouldFailRunWithHelpfulMessageIfTestsNotGreen() {
    this.data.setMutators(Mutator.MATH.asCollection());
    this.data
        .setTargetClasses(predicateFor("com.example.FailsTestWhenEnvVariableSet*"));
    this.data.addChildJVMArgs(Arrays.asList("-D"
        + FailsTestWhenEnvVariableSetTestee.class.getName() + "=true"));
    createAndRun();
    // should not get here
  }

  @Test
  public void shouldOnlyRunTestsMathchingSuppliedFilter() {
    this.data
        .setTargetClasses(predicateFor(com.example.HasMutableStaticInitializer.class));
    this.data
        .setTargetTests(predicateFor(com.example.HasMutableStaticInitializerTest.class));
    createAndRun();
    verifyResults(KILLED);
  }

  @Test
  public void shouldLoadResoucesOffClassPathFromFolderWithSpaces() {
    this.data.setMutators(Mutator.RETURN_VALS.asCollection());

    this.data
        .setTargetClasses(predicateFor("com.example.LoadsResourcesFromClassPath*"));
    createAndRun();
    verifyResults(KILLED);
  }

  @Test
  public void shouldPickRelevantTestsFromSuppliedTestSuites() {
    this.data.setTargetClasses(predicateFor("com.example.FullyCovered*"));
    this.data
        .setTargetTests(predicateFor(com.example.SuiteForFullyCovered.class));
    createAndRun();
    verifyResults(KILLED);
  }

  @Test
  public void shouldNotMutateMethodsMatchingExclusionPredicate() {
    this.data.setTargetClasses(predicateFor("com.example.HasExcludedMethods*"));
    this.data.setExcludedMethods(predicateFor("excludeMe"));
    createAndRun();
    verifyResults();
  }

  @Test
  public void shouldLimitNumberOfMutationsPerClass() {
    this.data.setTargetClasses(predicateFor(MultipleMutations.class));
    this.data
        .setTargetTests(predicateFor(com.example.FullyCoveredTesteeTest.class));
    this.data.setMaxMutationsPerClass(1);
    createAndRun();
    verifyResults(NO_COVERAGE);
  }

  @Test
  public void shouldWorkWithEasyMock() {
    this.data.setTargetClasses(predicateFor(CoveredByEasyMock.class));
    this.data.setClassesInScope(predicateFor("com.example.*EasyMock*"));
    this.data.setTargetTests(predicateFor(com.example.EasyMockTest.class));
    createAndRun();
    verifyResults(KILLED, KILLED, KILLED);
  }

  @Test
  @Ignore("does not seem to be possible to have TestNG on the classpath when jmockit agent is loaded")
  public void shouldWorkWithJMockit() {
    this.data.setTargetClasses(predicateFor(CoveredByJMockit.class));
    this.data.setClassesInScope(predicateFor("com.example.*JMockit*"));
    this.data.setTargetTests(predicateFor(com.example.JMockitTest.class));
    createAndRun();
    verifyResults(KILLED, KILLED, TIMED_OUT);
  }

  @Test
  public void shouldWorkWithPowerMock() {
    this.data.setTargetClasses(predicateFor("com.example.PowerMockCallFoo"));
    this.data.setClassesInScope(predicateFor("com.example.Power*"));
    this.data.setTargetTests(predicateFor(com.example.PowerMockTest.class));
    this.data.setVerbose(true);
    createAndRun();
    verifyResults(KILLED);
  }

  @Test
  public void shouldWorkWhenPowerMockReplacesCallsWithinMutee() {
    this.data
        .setTargetClasses(predicateFor("com.example.PowerMockCallsOwnMethod"));
    this.data.setClassesInScope(predicateFor("com.example.Power*"));
    this.data.setTargetTests(predicateFor(com.example.PowerMockTest.class));
    this.data.setVerbose(true);
    createAndRun();
    verifyResults(KILLED);
  }

  @Test
  public void shouldWorkWithMockitoJUnitRunner() {
    this.data.setTargetClasses(predicateFor("com.example.MockitoCallFoo"));
    this.data.setClassesInScope(predicateFor("com.example.Mockito*"));
    this.data.setTargetTests(predicateFor(com.example.MockitoRunnerTest.class));
    this.data.setVerbose(true);
    createAndRun();
    verifyResults(KILLED);
  }

  @Test
  public void shouldWorkWithPowerMockJavaAgent() {
    this.data
        .setTargetClasses(predicateFor("com.example.PowerMockAgentCallFoo"));
    this.data.setClassesInScope(predicateFor("com.example.Power*"));
    this.data
        .setTargetTests(predicateFor(com.example.PowerMockAgentTest.class));
    this.data.setVerbose(true);
    createAndRun();
    verifyResults(KILLED);
  }

  @Test(expected = PitHelpError.class)
  public void shouldReportHelpfulErrorIfNoMutationsFounds() {
    this.data.setFailWhenNoMutations(true);
    this.data.setTargetClasses(predicateFor("foo"));
    this.data.setClassesInScope(predicateFor("foo"));
    createAndRun();
  }

  @Test
  public void shouldExcludeFilteredTests() {
    this.data.setTargetTests(predicateFor("com.example.*FullyCoveredTestee*"));
    this.data.setTargetClasses(predicateFor("com.example.FullyCovered*"));
    this.data.setExcludedClasses(predicateFor(FullyCoveredTesteeTest.class));
    createAndRun();
    verifyResults(NO_COVERAGE);
  }

  @Test
  public void willAllowExcludedClassesToBeReIncludedViaSuite() {
    this.data
        .setTargetTests(predicateFor("com.example.*SuiteForFullyCovered*"));
    this.data.setTargetClasses(predicateFor("com.example.FullyCovered*"));
    this.data.setExcludedClasses(predicateFor(FullyCoveredTesteeTest.class));
    createAndRun();
    verifyResults(KILLED);
  }

  @Test(expected = PitHelpError.class)
  public void shouldExcludeFilteredClasses() {
    this.data.setFailWhenNoMutations(true);
    this.data.setTargetClasses(predicateFor(FullyCoveredTestee.class));
    this.data.setExcludedClasses(predicateFor(FullyCoveredTestee.class));
    createAndRun();
  }

  @Test
  public void shouldMutateClassesSuppliedToAlternateClassPath()
      throws IOException {
    // yes, this is horrid
    final String location = FileUtil.randomFilename() + ".jar";
    try {
      final FileOutputStream fos = new FileOutputStream(location);
      final InputStream stream = IsolationUtils.getContextClassLoader()
          .getResourceAsStream("outofcp.jar");
      copy(stream, fos);
      fos.close();

      this.data.setClassesInScope(predicateFor("com.outofclasspath.*"));
      this.data.setTargetClasses(predicateFor("com.outofclasspath.*Mutee*"));
      this.data.addClassPathElements(Arrays.asList(location));
      this.data.setDependencyAnalysisMaxDistance(-1);
      this.data.setExcludedClasses(predicateFor("*Power*", "*JMockit*"));
      createAndRun();
      verifyResults(KILLED);
    } finally {
      new File(location).delete();
    }
  }

  @Test
  public void shouldSupportTestNG() {
    this.data
        .setTargetClasses(predicateFor("com.example.testng.FullyCovered*"));
    this.data.setVerbose(true);
    createAndRun(new TestNGConfiguration(new TestGroupConfig(
        Collections.<String> emptyList(), Collections.<String> emptyList())));
    verifyResults(KILLED);
  }

  private void createAndRun() {
    createAndRun(new JUnitCompatibleConfiguration());
  }

  private void createAndRun(final Configuration configuration) {
    final JavaAgent agent = new JarCreatingJarFinder();
    try {

      this.data.setConfiguration(configuration);
      final CoverageOptions coverageOptions = this.data.createCoverageOptions();
      final LaunchOptions launchOptions = new LaunchOptions(agent,
          this.data.getJvmArgs());

      final PathFilter pf = new PathFilter(new True<ClassPathRoot>(),
          new True<ClassPathRoot>());
      final MutationClassPaths cps = new MutationClassPaths(
          this.data.getClassPath(), this.data.createClassesFilter(), pf);

      final Timings timings = new Timings();
      final CoverageDatabase coverageDatabase = new DefaultCoverageDatabase(
          coverageOptions, launchOptions, cps, timings);
      final MutationCoverageReport testee = new MutationCoverageReport(
          coverageDatabase, this.data, listenerFactory(), timings);

      testee.run();
    } finally {
      agent.close();
    }
  }

  private static void copy(final InputStream in, final OutputStream out)
      throws IOException {
    // Read bytes and write to destination until eof

    final byte[] buf = new byte[1024];
    int len = 0;
    while ((len = in.read(buf)) >= 0) {
      out.write(buf, 0, len);
    }
  }

}
