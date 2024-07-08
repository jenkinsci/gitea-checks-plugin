package io.jenkins.plugins.checks.gitea;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Job;
import hudson.model.Run;
import java.util.Optional;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import org.jenkinsci.plugin.gitea.GiteaSCMSource;
import org.jenkinsci.plugin.gitea.PullRequestSCMRevision;
import org.jenkinsci.plugins.displayurlapi.ClassicDisplayURLProvider;
import org.junit.jupiter.api.Test;

class GiteaSCMSourceChecksContextTest {
    private static final String URL = "URL";

    @Test
    void shouldGetHeadShaFromMasterBranch() {
        Job job = mock(Job.class);
        SCMHead head = mock(SCMHead.class);
        AbstractGitSCMSource.SCMRevisionImpl revision = mock(AbstractGitSCMSource.SCMRevisionImpl.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);

        assertThat(GiteaSCMSourceChecksContext.fromJob(
                                job, URL, createGiteaSCMFacadeWithRevision(job, source, head, revision, "a1b2c3"))
                        .getHeadSha())
                .isEqualTo("a1b2c3");
    }

    @Test
    void shouldGetHeadShaFromPullRequest() {
        Job job = mock(Job.class);
        SCMHead head = mock(SCMHead.class);
        PullRequestSCMRevision revision = mock(PullRequestSCMRevision.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);

        assertThat(GiteaSCMSourceChecksContext.fromJob(
                                job, URL, createGiteaSCMFacadeWithRevision(job, source, head, revision, "a1b2c3"))
                        .getHeadSha())
                .isEqualTo("a1b2c3");
    }

    @Test
    void shouldGetHeadShaFromRun() {
        Job job = mock(Job.class);
        Run run = mock(Run.class);
        PullRequestSCMRevision revision = mock(PullRequestSCMRevision.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);

        when(run.getParent()).thenReturn(job);
        when(job.getLastBuild()).thenReturn(run);

        assertThat(GiteaSCMSourceChecksContext.fromRun(
                                run, URL, createGiteaSCMFacadeWithRevision(run, source, revision, "a1b2c3"))
                        .getHeadSha())
                .isEqualTo("a1b2c3");
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenGetHeadShaButNoSCMHeadAvailable() {
        Job job = mock(Job.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);

        when(job.getName()).thenReturn("gitea-checks-plugin");

        assertThatThrownBy(
                        GiteaSCMSourceChecksContext.fromJob(job, URL, createGiteaSCMFacadeWithSource(job, source))
                                ::getHeadSha)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No SHA found for job: gitea-checks-plugin");
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenGetHeadShaButNoSCMRevisionAvailable() {
        Job job = mock(Job.class);
        SCMHead head = mock(SCMHead.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);

        when(job.getName()).thenReturn("gitea-checks-plugin");
        when(source.getRepoOwner()).thenReturn("jenkinsci");
        when(source.getRepository()).thenReturn("gitea-checks-plugin");
        when(head.getName()).thenReturn("master");

        assertThatThrownBy(GiteaSCMSourceChecksContext.fromJob(
                        job, URL, createGiteaSCMFacadeWithRevision(job, source, head, null, null))::getHeadSha)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No SHA found for job: gitea-checks-plugin");
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenGetHeadShaButNoSuitableSCMRevisionAvailable() {
        Job job = mock(Job.class);
        SCMHead head = mock(SCMHead.class);
        SCMRevision revision = mock(SCMRevision.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);

        when(job.getName()).thenReturn("gitea-checks-plugin");

        assertThatThrownBy(GiteaSCMSourceChecksContext.fromJob(
                        job, URL, createGiteaSCMFacadeWithRevision(job, source, head, revision, null))::getHeadSha)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No SHA found for job: gitea-checks-plugin");
    }

    @Test
    void shouldGetRepositoryName() {
        Job job = mock(Job.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);

        when(source.getRepoOwner()).thenReturn("jenkinsci");
        when(source.getRepository()).thenReturn("gitea-checks-plugin");

        assertThat(GiteaSCMSourceChecksContext.fromJob(job, URL, createGiteaSCMFacadeWithSource(job, source))
                        .getRepository())
                .isEqualTo("jenkinsci/gitea-checks-plugin");
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenGetRepositoryButNoGiteaSCMSourceAvailable() {
        Job job = mock(Job.class);
        when(job.getName()).thenReturn("gitea-checks-plugin");

        assertThatThrownBy(
                        () -> GiteaSCMSourceChecksContext.fromJob(job, URL, createGiteaSCMFacadeWithSource(job, null))
                                .getRepository())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No Gitea SCM source found for job: gitea-checks-plugin");
    }

    @Test
    void shouldGetCredentials() {
        Job job = mock(Job.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);
        StandardCredentials credentials = mock(StandardCredentials.class);

        assertThat(GiteaSCMSourceChecksContext.fromJob(
                                job, URL, createGiteaSCMFacadeWithCredentials(job, source, credentials, "1"))
                        .getCredentials())
                .isEqualTo(credentials);
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenGetCredentialsButNoCredentialsAvailable() {
        Job job = mock(Job.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);

        when(job.getName()).thenReturn("gitea-checks-plugin");

        assertThatThrownBy(GiteaSCMSourceChecksContext.fromJob(
                        job, URL, createGiteaSCMFacadeWithCredentials(job, source, null, null))::getCredentials)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No Gitea APP credentials available for job: gitea-checks-plugin");
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenGetCredentialsButNoSourceAvailable() {
        Job job = mock(Job.class);
        SCMFacade scmFacade = mock(SCMFacade.class);

        when(job.getName()).thenReturn("gitea-checks-plugin");

        assertThatThrownBy(GiteaSCMSourceChecksContext.fromJob(job, URL, scmFacade)::getCredentials)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No Gitea APP credentials available for job: gitea-checks-plugin");
    }

    @Test
    void shouldGetURLForJob() {
        Job job = mock(Job.class);

        assertThat(GiteaSCMSourceChecksContext.fromJob(job, URL, createGiteaSCMFacadeWithSource(job, null))
                        .getURL())
                .isEqualTo(URL);
    }

    @Test
    void shouldGetURLForRun() {
        Run<?, ?> run = mock(Run.class);
        Job<?, ?> job = mock(Job.class);
        ClassicDisplayURLProvider urlProvider = mock(ClassicDisplayURLProvider.class);

        when(urlProvider.getRunURL(run)).thenReturn("http://127.0.0.1:8080/job/gitea-checks-plugin/job/master/200");

        assertThat(GiteaSCMSourceChecksContext.fromRun(
                                run, urlProvider.getRunURL(run), createGiteaSCMFacadeWithSource(job, null))
                        .getURL())
                .isEqualTo("http://127.0.0.1:8080/job/gitea-checks-plugin/job/master/200");
    }

    @Test
    void shouldReturnFalseWhenValidateContextButHasNoValidCredentials() {
        Job<?, ?> job = mock(Job.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);
        FilteredLog logger = new FilteredLog("");

        assertThat(GiteaSCMSourceChecksContext.fromJob(job, URL, createGiteaSCMFacadeWithSource(job, source))
                        .isValid(logger))
                .isFalse();
        assertThat(logger.getErrorMessages()).contains("No credentials found");
    }

    @Test
    void shouldReturnFalseWhenValidateContextButHasNoValidGiteaAppCredentials() {
        Job<?, ?> job = mock(Job.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);
        FilteredLog logger = new FilteredLog("");

        when(source.getCredentialsId()).thenReturn("oauth-credentials");

        assertThat(GiteaSCMSourceChecksContext.fromJob(job, URL, createGiteaSCMFacadeWithSource(job, source))
                        .isValid(logger))
                .isFalse();
        assertThat(logger.getErrorMessages())
                .contains("No Gitea app credentials found: 'oauth-credentials'")
                .contains("See: https://plugins.jenkins.io/gitea/");
    }

    @Test
    void shouldReturnFalseWhenValidateContextButHasNoValidSHA() {
        Run run = mock(Run.class);
        Job job = mock(Job.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);
        StandardCredentials credentials = mock(StandardCredentials.class);
        FilteredLog logger = new FilteredLog("");

        when(run.getParent()).thenReturn(job);

        when(source.getRepoOwner()).thenReturn("jenkinsci");
        when(source.getRepository()).thenReturn("gitea-checks");

        assertThat(GiteaSCMSourceChecksContext.fromRun(
                                run, URL, createGiteaSCMFacadeWithCredentials(job, source, credentials, "1"))
                        .isValid(logger))
                .isFalse();
        assertThat(logger.getErrorMessages()).contains("No HEAD SHA found for jenkinsci/gitea-checks");
    }

    private SCMFacade createGiteaSCMFacadeWithRevision(
            final Job<?, ?> job,
            final GiteaSCMSource source,
            final SCMHead head,
            final SCMRevision revision,
            final String hash) {
        SCMFacade facade = createGiteaSCMFacadeWithSource(job, source);

        when(facade.findHead(job)).thenReturn(Optional.ofNullable(head));
        when(facade.findRevision(source, head)).thenReturn(Optional.ofNullable(revision));
        when(facade.findHash(revision)).thenReturn(Optional.ofNullable(hash));

        return facade;
    }

    private SCMFacade createGiteaSCMFacadeWithRevision(
            final Run<?, ?> run,
            final GiteaSCMSource source,
            @CheckForNull final SCMRevision revision,
            @CheckForNull final String hash) {
        SCMFacade facade = createGiteaSCMFacadeWithSource(run.getParent(), source);

        when(facade.findRevision(source, run)).thenReturn(Optional.of(revision));
        when(facade.findHash(revision)).thenReturn(Optional.of(hash));

        return facade;
    }

    private SCMFacade createGiteaSCMFacadeWithCredentials(
            final Job<?, ?> job,
            final GiteaSCMSource source,
            @CheckForNull final StandardCredentials credentials,
            final String credentialsId) {
        SCMFacade facade = createGiteaSCMFacadeWithSource(job, source);

        when(source.getCredentialsId()).thenReturn(credentialsId);
        when(facade.findGiteaAppCredentials(job, credentialsId)).thenReturn(Optional.ofNullable(credentials));

        return facade;
    }

    private SCMFacade createGiteaSCMFacadeWithSource(final Job<?, ?> job, @CheckForNull final GiteaSCMSource source) {
        SCMFacade facade = mock(SCMFacade.class);

        when(facade.findGiteaSCMSource(job)).thenReturn(Optional.ofNullable(source));

        return facade;
    }
}
