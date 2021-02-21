package io.jenkins.plugins.checks.gitea;

import java.io.IOException;
import java.util.Optional;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.EnvVars;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import jenkins.scm.api.SCMHead;
import org.jenkinsci.plugin.gitea.GiteaSCMSource;
import org.jenkinsci.plugin.gitea.PullRequestSCMRevision;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;

class GiteaPublisherFactoryTest {
    @Test
    void shouldCreateGiteaChecksPublisherFromRunForProjectWithValidGiteaSCMSource() {
        Run run = mock(Run.class);
        Job job = mock(Job.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);
        StandardCredentials credentials = mock(StandardCredentials.class);
        PullRequestSCMRevision revision = mock(PullRequestSCMRevision.class);
        SCMFacade scmFacade = mock(SCMFacade.class);

        when(run.getParent()).thenReturn(job);
        when(job.getLastBuild()).thenReturn(run);
        when(scmFacade.findGiteaSCMSource(job)).thenReturn(Optional.of(source));
        when(source.getCredentialsId()).thenReturn("credentials id");
        when(scmFacade.findGiteaAppCredentials(job, "credentials id")).thenReturn(Optional.of(credentials));
        when(scmFacade.findRevision(source, run)).thenReturn(Optional.of(revision));
        when(scmFacade.findHash(revision)).thenReturn(Optional.of("a1b2c3"));

        GiteaPublisherFactory factory = new GiteaPublisherFactory(scmFacade, createDisplayURLProvider(run,
                job));
        assertThat(factory.createPublisher(run, TaskListener.NULL)).containsInstanceOf(GiteaChecksPublisher.class);
    }

    @Test
    void shouldReturnGiteaChecksPublisherFromJobProjectWithValidGiteaSCMSource() {
        Run run = mock(Run.class);
        Job job = mock(Job.class);
        GiteaSCMSource source = mock(GiteaSCMSource.class);
        StandardCredentials credentials = mock(StandardCredentials.class);
        PullRequestSCMRevision revision = mock(PullRequestSCMRevision.class);
        SCMHead head = mock(SCMHead.class);
        SCMFacade scmFacade = mock(SCMFacade.class);

        when(run.getParent()).thenReturn(job);
        when(job.getLastBuild()).thenReturn(run);
        when(scmFacade.findGiteaSCMSource(job)).thenReturn(Optional.of(source));
        when(source.getCredentialsId()).thenReturn("credentials id");
        when(scmFacade.findGiteaAppCredentials(job, "credentials id")).thenReturn(Optional.of(credentials));
        when(scmFacade.findHead(job)).thenReturn(Optional.of(head));
        when(scmFacade.findRevision(source, head)).thenReturn(Optional.of(revision));
        when(scmFacade.findHash(revision)).thenReturn(Optional.of("a1b2c3"));

        GiteaPublisherFactory factory = new GiteaPublisherFactory(scmFacade, createDisplayURLProvider(run,
                job));
        assertThat(factory.createPublisher(job, TaskListener.NULL)).containsInstanceOf(GiteaChecksPublisher.class);
    }

    @Test
    void shouldCreateGiteaChecksPublisherFromRunForProjectWithValidGitSCM() throws IOException, InterruptedException {
        Job job = mock(Job.class);
        Run run = mock(Run.class);
        GitSCM gitSCM = mock(GitSCM.class);
        UserRemoteConfig config = mock(UserRemoteConfig.class);
        StandardCredentials credentials = mock(StandardCredentials.class);
        SCMFacade scmFacade = mock(SCMFacade.class);
        EnvVars envVars = mock(EnvVars.class);

        when(run.getParent()).thenReturn(job);
        when(run.getEnvironment(TaskListener.NULL)).thenReturn(envVars);
        when(envVars.get("GIT_COMMIT")).thenReturn("a1b2c3");
        when(scmFacade.getScm(job)).thenReturn(gitSCM);
        when(scmFacade.findGitSCM(run)).thenReturn(Optional.of(gitSCM));
        when(scmFacade.getUserRemoteConfig(gitSCM)).thenReturn(config);
        when(config.getCredentialsId()).thenReturn("1");
        when(scmFacade.findGiteaAppCredentials(job, "1")).thenReturn(Optional.of(credentials));
        when(config.getUrl()).thenReturn("https://github.com/jenkinsci/gitea-checks-plugin");

        GiteaPublisherFactory factory = new GiteaPublisherFactory(scmFacade, createDisplayURLProvider(run,
                job));
        assertThat(factory.createPublisher(run, TaskListener.NULL)).containsInstanceOf(GiteaChecksPublisher.class);
    }

    @Test
    void shouldReturnEmptyFromRunForInvalidProject() {
        Run run = mock(Run.class);
        SCMFacade facade = mock(SCMFacade.class);
        DisplayURLProvider urlProvider = mock(DisplayURLProvider.class);

        GiteaPublisherFactory factory = new GiteaPublisherFactory(facade, urlProvider);
        assertThat(factory.createPublisher(run, TaskListener.NULL)).isNotPresent();
    }

    @Test
    void shouldCreateNullPublisherFromJobForInvalidProject() {
        Job job = mock(Job.class);
        SCMFacade facade = mock(SCMFacade.class);
        DisplayURLProvider urlProvider = mock(DisplayURLProvider.class);

        GiteaPublisherFactory factory = new GiteaPublisherFactory(facade, urlProvider);
        assertThat(factory.createPublisher(job, TaskListener.NULL))
                .isNotPresent();
    }

    private DisplayURLProvider createDisplayURLProvider(final Run<?, ?> run, final Job<?, ?> job) {
        DisplayURLProvider urlProvider = mock(DisplayURLProvider.class);

        when(urlProvider.getRunURL(run)).thenReturn(null);
        when(urlProvider.getJobURL(job)).thenReturn(null);

        return urlProvider;
    }
}
