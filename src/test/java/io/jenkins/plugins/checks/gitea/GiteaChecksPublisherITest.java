package io.jenkins.plugins.checks.gitea;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Queue;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;
import io.jenkins.plugins.util.PluginLogger;
import jenkins.model.ParameterizedJobMixIn;
import org.jenkinsci.plugin.gitea.GiteaSCMSource;
import org.jenkinsci.plugin.gitea.PullRequestSCMRevision;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import org.jenkinsci.plugins.displayurlapi.ClassicDisplayURLProvider;

import hudson.model.Run;
import jenkins.scm.api.SCMHead;

import io.jenkins.plugins.checks.api.ChecksAction;
import io.jenkins.plugins.checks.api.ChecksAnnotation.ChecksAnnotationBuilder;
import io.jenkins.plugins.checks.api.ChecksAnnotation.ChecksAnnotationLevel;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksDetails.ChecksDetailsBuilder;
import io.jenkins.plugins.checks.api.ChecksImage;
import io.jenkins.plugins.checks.api.ChecksOutput.ChecksOutputBuilder;
import io.jenkins.plugins.checks.api.ChecksStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests if the {@link GiteaChecksPublisher} actually sends out the requests to Gitea in order to publish the check
 * runs.
 */
@RunWith(Parameterized.class)
@SuppressWarnings({"PMD.ExcessiveImports", "checkstyle:ClassDataAbstractionCoupling", "rawtypes", "checkstyle:ClassFanOutComplexity", "checkstyle:JavaNCSS"})
public class GiteaChecksPublisherITest extends IntegrationTestWithJenkinsPerTest {
    /**
     * Provides parameters for tests.
     * @return A list of methods used to create GiteaChecksContexts, with which each test should be run.
     */
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> contextBuilders() {
        return Arrays.asList(new Object[][]{
                {"Freestyle (run)", (Function<GiteaChecksPublisherITest, GiteaChecksContext>) GiteaChecksPublisherITest::createGiteaChecksContextWithGiteaSCMFreestyle, false},
                {"Freestyle (job)", (Function<GiteaChecksPublisherITest, GiteaChecksContext>) GiteaChecksPublisherITest::createGiteaChecksContextWithGiteaSCMFreestyle, true},
                {"Pipeline (run)", (Function<GiteaChecksPublisherITest, GiteaChecksContext>) GiteaChecksPublisherITest::createGiteaChecksContextWithGiteaSCMFromPipeline, false},
                {"Pipeline (job)", (Function<GiteaChecksPublisherITest, GiteaChecksContext>) GiteaChecksPublisherITest::createGiteaChecksContextWithGiteaSCMFromPipeline, true}
        });
    }

    /**
     * Human readable name of the context builder - used only for test name formatting.
     */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    @Parameterized.Parameter(0)
    @CheckForNull
    public String contextBuilderName;

    /**
     * Reference to method used to create GiteaChecksContext with either a pipeline or freestyle job.
     */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    @Parameterized.Parameter(1)
    @CheckForNull
    public Function<GiteaChecksPublisherITest, GiteaChecksContext> contextBuilder;

    /**
     * Create GiteaChecksContext from the job instead of the run.
     */
    @SuppressWarnings("checkstyle:VisibilityModifier")
    @Parameterized.Parameter(2)
    public boolean fromJob;

    /**
     * Rule for the log system.
     */
    @Rule
    public LoggerRule loggerRule = new LoggerRule();

    /**
     * A rule which provides a mock server.
     */
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(
            WireMockConfiguration.options().dynamicPort());

    /**
     * Checks should be published to Gitea correctly when Gitea SCM is found and parameters are correctly set.
     */
    @Test
    public void shouldPublishGiteaCheckRunCorrectly() {
        ChecksDetails details = new ChecksDetailsBuilder()
                .withName("Jenkins")
                .withStatus(ChecksStatus.COMPLETED)
                .withDetailsURL("https://ci.jenkins.io")
                .withStartedAt(LocalDateTime.ofEpochSecond(999_999, 0, ZoneOffset.UTC))
                .withCompletedAt(LocalDateTime.ofEpochSecond(999_999, 0, ZoneOffset.UTC))
                .withConclusion(ChecksConclusion.SUCCESS)
                .withOutput(new ChecksOutputBuilder()
                        .withTitle("Jenkins Check")
                        .withSummary("# A Successful Build")
                        .withText("## 0 Failures")
                        .withAnnotations(Arrays.asList(
                                new ChecksAnnotationBuilder()
                                        .withPath("Jenkinsfile")
                                        .withLine(1)
                                        .withAnnotationLevel(ChecksAnnotationLevel.NOTICE)
                                        .withMessage("say hello to Jenkins")
                                        .withStartColumn(0)
                                        .withEndColumn(20)
                                        .withTitle("Hello Jenkins")
                                        .withRawDetails("a simple echo command")
                                        .build(),
                                new ChecksAnnotationBuilder()
                                        .withPath("Jenkinsfile")
                                        .withLine(2)
                                        .withAnnotationLevel(ChecksAnnotationLevel.WARNING)
                                        .withMessage("say hello to Gitea Checks API")
                                        .withStartColumn(0)
                                        .withEndColumn(30)
                                        .withTitle("Hello Gitea Checks API")
                                        .withRawDetails("a simple echo command")
                                        .build()))
                        .withImages(Collections.singletonList(
                                new ChecksImage("Jenkins",
                                        "https://ci.jenkins.io/static/cd5757a8/images/jenkins-header-logo-v2.svg",
                                        "Jenkins Symbol")))
                        .build())
                .withActions(Collections.singletonList(
                        new ChecksAction("re-run", "re-run Jenkins build", "#0")))
                .build();

        new GiteaChecksPublisher(contextBuilder.apply(this),
                new PluginLogger(getJenkins().createTaskListener().getLogger(), "Gitea Checks"),
                wireMockRule.baseUrl())
                .publish(details);
    }

    /**
     * If exception happens when publishing checks, it should output all parameters of the check to the system log.
     */
    @Issue("issue-20")
    @Test
    public void shouldLogChecksParametersIfExceptionHappensWhenPublishChecks() {
        loggerRule.record(GiteaChecksPublisher.class.getName(), Level.WARNING).capture(1);

        ChecksDetails details = new ChecksDetailsBuilder()
                .withName("Jenkins")
                .withStatus(ChecksStatus.COMPLETED)
                .withConclusion(ChecksConclusion.SUCCESS)
                .withOutput(new ChecksOutputBuilder()
                        .withTitle("Jenkins Check")
                        .withSummary("# A Successful Build")
                        .withAnnotations(Collections.singletonList(
                                new ChecksAnnotationBuilder()
                                        .withPath("Jenkinsfile")
                                        .withStartLine(1)
                                        .withEndLine(2)
                                        .withStartColumn(0)
                                        .withEndColumn(20)
                                        .withAnnotationLevel(ChecksAnnotationLevel.WARNING)
                                        .withMessage("say hello to Jenkins")
                                        .build()))
                        .build())
                .build();

        new GiteaChecksPublisher(contextBuilder.apply(this),
                new PluginLogger(getJenkins().createTaskListener().getLogger(), "Gitea Checks"),
                wireMockRule.baseUrl())
                .publish(details);

        assertThat(loggerRule.getRecords().size()).isEqualTo(1);
        assertThat(loggerRule.getMessages().get(0))
                .contains("Failed Publishing Gitea checks: ")
                .contains("name='Jenkins'")
                .contains("status=COMPLETED")
                .contains("conclusion=SUCCESS")
                .contains("title='Jenkins Check'")
                .contains("summary='# A Successful Build'")
                .contains("path='Jenkinsfile'")
                .contains("startLine=1")
                .contains("endLine=2")
                .contains("startColumn=0")
                .contains("endColumn=20")
                .contains("annotationLevel=WARNING")
                .contains("message='say hello to Jenkins'");
    }

    private GiteaChecksContext createGiteaChecksContextWithGiteaSCMFreestyle() {
        FreeStyleProject job = createFreeStyleProject();
        return createGiteaChecksContextWithGiteaSCM(job);
    }

    private GiteaChecksContext createGiteaChecksContextWithGiteaSCMFromPipeline() {
        WorkflowJob job = createPipeline();
        job.setDefinition(new CpsFlowDefinition("node {}", true));
        return createGiteaChecksContextWithGiteaSCM(job);
    }

    private <R extends Run<J, R> & Queue.Executable, J extends Job<J, R> & ParameterizedJobMixIn.ParameterizedJob<J, R>>
            GiteaChecksContext createGiteaChecksContextWithGiteaSCM(final J job) {
        Run run = buildSuccessfully(job);

        SCMFacade scmFacade = mock(SCMFacade.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);
        StandardCredentials credentials = mock(StandardCredentials.class);
        SCMHead head = mock(SCMHead.class);
        PullRequestSCMRevision revision = mock(PullRequestSCMRevision.class);
        ClassicDisplayURLProvider urlProvider = mock(ClassicDisplayURLProvider.class);

        when(source.getCredentialsId()).thenReturn("1");
        when(source.getRepoOwner()).thenReturn("XiongKezhi");
        when(source.getRepository()).thenReturn("Sandbox");
        //when(credentials.getId()).thenReturn(Secret.fromString("password"));

        when(scmFacade.findGiteaSCMSource(job)).thenReturn(Optional.of(source));
        when(scmFacade.findGiteaAppCredentials(job, "1")).thenReturn(Optional.of(credentials));
        when(scmFacade.findHead(job)).thenReturn(Optional.of(head));
        when(scmFacade.findRevision(source, run)).thenReturn(Optional.of(revision));
        when(scmFacade.findRevision(source, head)).thenReturn(Optional.of(revision));
        when(scmFacade.findHash(revision)).thenReturn(Optional.of("18c8e2fd86e7aa3748e279c14a00dc3f0b963e7f"));

        when(urlProvider.getRunURL(run)).thenReturn("https://ci.jenkins.io");
        when(urlProvider.getJobURL(job)).thenReturn("https://ci.jenkins.io");

        if (fromJob) {
            return GiteaSCMSourceChecksContext.fromJob(job, urlProvider.getJobURL(job), scmFacade);
        }
        return GiteaSCMSourceChecksContext.fromRun(run, urlProvider.getRunURL(run), scmFacade);
    }
}
