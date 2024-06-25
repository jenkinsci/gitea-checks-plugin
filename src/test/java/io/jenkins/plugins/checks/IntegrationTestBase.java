package io.jenkins.plugins.checks;

import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import io.jenkins.plugins.util.IntegrationTest;

/**
 * Base class for integration tests.
 */
public abstract class IntegrationTestBase extends IntegrationTest {
    /**
     * Provide a jenkins rule.
     */
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Override
    protected JenkinsRule getJenkins() {
        return r;
    }
}
