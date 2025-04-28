package io.jenkins.plugins.checks.gitea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import io.jenkins.plugins.checks.api.ChecksAction;
import io.jenkins.plugins.checks.api.ChecksAnnotation.ChecksAnnotationBuilder;
import io.jenkins.plugins.checks.api.ChecksAnnotation.ChecksAnnotationLevel;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksDetails.ChecksDetailsBuilder;
import io.jenkins.plugins.checks.api.ChecksImage;
import io.jenkins.plugins.checks.api.ChecksOutput.ChecksOutputBuilder;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.util.PluginLogger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Stream;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.scm.api.SCMHead;
import org.jenkinsci.plugin.gitea.GiteaSCMSource;
import org.jenkinsci.plugin.gitea.PullRequestSCMRevision;
import org.jenkinsci.plugins.displayurlapi.ClassicDisplayURLProvider;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Tests if the {@link GiteaChecksPublisher} actually sends out the requests to
 * Gitea in order to publish the check runs.
 */
@WithJenkins
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GiteaChecksPublisherITest {

    /**
     * Recorder for the log system.
     */
    private final LogRecorder logging = new LogRecorder();

    /**
     * Provides a mock server.
     */
    @RegisterExtension
    private static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.options().dynamicPort())
            .build();

    private JenkinsRule r;

    @BeforeEach
    void setUp(final JenkinsRule rule) {
        r = rule;
    }

    /**
     * Provides parameters for tests.
     * @return A list of methods used to create GiteaChecksContexts, with which each test should be run.
     */
    Stream<Arguments> contextBuilders() {
        return Stream.of(
                Arguments.of("Freestyle (run)", (Supplier<GiteaChecksContext>)
                        () -> createGiteaChecksContextWithGiteaSCMFreestyle(false)),
                Arguments.of("Freestyle (job)", (Supplier<GiteaChecksContext>)
                        () -> createGiteaChecksContextWithGiteaSCMFreestyle(true)),
                Arguments.of("Pipeline (run)", (Supplier<GiteaChecksContext>)
                        () -> createGiteaChecksContextWithGiteaSCMFromPipeline(false)),
                Arguments.of("Pipeline (job)", (Supplier<GiteaChecksContext>)
                        () -> createGiteaChecksContextWithGiteaSCMFromPipeline(true)));
    }

    /**
     * Checks should be published to Gitea correctly when Gitea SCM is found and parameters are correctly set.
     *
     * @param contextBuilderName Human-readable name of the context builder - used only for test name formatting.
     * @param contextBuilder Reference to the method used to create GiteaChecksContext with either a pipeline or freestyle job.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("contextBuilders")
    void shouldPublishGiteaCheckRunCorrectly(
            final String contextBuilderName, final Supplier<GiteaChecksContext> contextBuilder) {
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
                        .withImages(Collections.singletonList(new ChecksImage(
                                "Jenkins",
                                "https://ci.jenkins.io/static/cd5757a8/images/jenkins-header-logo-v2.svg",
                                "Jenkins Symbol")))
                        .build())
                .withActions(Collections.singletonList(new ChecksAction("re-run", "re-run Jenkins build", "#0")))
                .build();

        new GiteaChecksPublisher(
                        contextBuilder.get(),
                        new PluginLogger(r.createTaskListener().getLogger(), "Gitea Checks"),
                        wireMock.baseUrl())
                .publish(details);
    }

    /**
     * If an exception happens when publishing checks, it should output all parameters of the check to the system log.
     *
     * @param contextBuilderName Human-readable name of the context builder - used only for test name formatting.
     * @param contextBuilder Reference to the method used to create GiteaChecksContext with either a pipeline or freestyle job.
     */
    @Issue("issue-20")
    @ParameterizedTest(name = "{0}")
    @MethodSource("contextBuilders")
    void shouldLogChecksParametersIfExceptionHappensWhenPublishChecks(
            final String contextBuilderName, final Supplier<GiteaChecksContext> contextBuilder) {
        logging.record(GiteaChecksPublisher.class.getName(), Level.WARNING).capture(1);

        ChecksDetails details = new ChecksDetailsBuilder()
                .withName("Jenkins")
                .withStatus(ChecksStatus.COMPLETED)
                .withConclusion(ChecksConclusion.SUCCESS)
                .withOutput(new ChecksOutputBuilder()
                        .withTitle("Jenkins Check")
                        .withSummary("# A Successful Build")
                        .withAnnotations(Collections.singletonList(new ChecksAnnotationBuilder()
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

        new GiteaChecksPublisher(
                        contextBuilder.get(),
                        new PluginLogger(r.createTaskListener().getLogger(), "Gitea Checks"),
                        wireMock.baseUrl())
                .publish(details);

        assertThat(logging.getRecords()).hasSize(1);
        assertThat(logging.getMessages().get(0))
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

    private GiteaChecksContext createGiteaChecksContextWithGiteaSCMFreestyle(final boolean fromJob) {
        return assertDoesNotThrow(() -> {
            FreeStyleProject job = r.createFreeStyleProject();
            return createGiteaChecksContextWithGiteaSCM(job, fromJob);
        });
    }

    private GiteaChecksContext createGiteaChecksContextWithGiteaSCMFromPipeline(final boolean fromJob) {
        return assertDoesNotThrow(() -> {
            WorkflowJob job = r.createProject(WorkflowJob.class);
            job.setDefinition(new CpsFlowDefinition("node {}", true));
            return createGiteaChecksContextWithGiteaSCM(job, fromJob);
        });
    }

    private <R extends Run<J, R> & Queue.Executable, J extends Job<J, R> & ParameterizedJobMixIn.ParameterizedJob<J, R>>
            GiteaChecksContext createGiteaChecksContextWithGiteaSCM(final J job, final boolean fromJob)
                    throws Exception {
        Run<J, R> run = r.buildAndAssertSuccess(job);

        SCMFacade scmFacade = mock(SCMFacade.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);
        StandardCredentials credentials = mock(StandardCredentials.class);
        SCMHead head = mock(SCMHead.class);
        PullRequestSCMRevision revision = mock(PullRequestSCMRevision.class);
        ClassicDisplayURLProvider urlProvider = mock(ClassicDisplayURLProvider.class);

        when(source.getCredentialsId()).thenReturn("1");
        when(source.getRepoOwner()).thenReturn("XiongKezhi");
        when(source.getRepository()).thenReturn("Sandbox");
        // when(credentials.getId()).thenReturn(Secret.fromString("password"));

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
