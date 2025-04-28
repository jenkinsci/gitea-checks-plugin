package io.jenkins.plugins.checks.gitea;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import java.util.Collections;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Integration tests for {@link GitSCMChecksContext}.
 */
@WithJenkins
class GitSCMChecksContextITest {

    private static final String EXISTING_HASH = "4ecc8623b06d99d5f029b66927438554fdd6a467";
    private static final String HTTP_URL = "https://github.com/jenkinsci/gitea-checks-plugin.git";
    private static final String CREDENTIALS_ID = "credentials";
    private static final String URL_NAME = "url";

    private JenkinsRule r;

    @BeforeEach
    void setUp(final JenkinsRule rule) {
        r = rule;
    }

    /**
     * Creates a FreeStyle job that uses {@link hudson.plugins.git.GitSCM} and runs a successful build.
     * Then this build is used to create a new {@link GitSCMChecksContext}. So the build actually is not publishing
     * the checks we just ensure that we can create the context with the successful build (otherwise we would need
     * Wiremock to handle the requests to Gitea).
     */
    @Test
    void shouldRetrieveContextFromFreeStyleBuild() throws Exception {
        FreeStyleProject job = r.createFreeStyleProject();

        BranchSpec branchSpec = new BranchSpec(EXISTING_HASH);
        GitSCM scm = new GitSCM(
                GitSCM.createRepoList(HTTP_URL, CREDENTIALS_ID),
                Collections.singletonList(branchSpec),
                null,
                null,
                Collections.emptyList());
        job.setScm(scm);

        Run<?, ?> run = r.buildAndAssertSuccess(job);

        GitSCMChecksContext gitSCMChecksContext = new GitSCMChecksContext(run, URL_NAME);

        assertThat(gitSCMChecksContext.getRepository()).isEqualTo("jenkinsci/gitea-checks-plugin");
        assertThat(gitSCMChecksContext.getHeadSha()).isEqualTo(EXISTING_HASH);
        assertThat(gitSCMChecksContext.getCredentialsId()).isEqualTo(CREDENTIALS_ID);
    }

    /**
     * Creates a pipeline that uses {@link hudson.plugins.git.GitSCM} and runs a successful build.
     * Then this build is used to create a new {@link GitSCMChecksContext}.
     */
    @Test
    void shouldRetrieveContextFromPipeline() throws Exception {
        WorkflowJob job = r.createProject(WorkflowJob.class);

        assertDoesNotThrow(() -> job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  stage ('Checkout') {\n"
                        + "    checkout scm: ([\n"
                        + "                    $class: 'GitSCM',\n"
                        + "                    userRemoteConfigs: [[credentialsId: '" + CREDENTIALS_ID + "', url: '"
                        + HTTP_URL + "']],\n"
                        + "                    branches: [[name: '" + EXISTING_HASH + "']]\n"
                        + "            ])"
                        + "  }\n"
                        + "}\n",
                true)));

        Run<?, ?> run = r.buildAndAssertSuccess(job);

        GitSCMChecksContext gitSCMChecksContext = new GitSCMChecksContext(run, URL_NAME);

        assertThat(gitSCMChecksContext.getRepository()).isEqualTo("jenkinsci/gitea-checks-plugin");
        assertThat(gitSCMChecksContext.getCredentialsId()).isEqualTo(CREDENTIALS_ID);
        assertThat(gitSCMChecksContext.getHeadSha()).isEqualTo(EXISTING_HASH);
    }
}
