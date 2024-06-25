package io.jenkins.plugins.checks.gitea;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.security.ACL;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.SCMSource;
import jenkins.triggers.SCMTriggerItem;
import org.jenkinsci.plugin.gitea.GiteaSCMSource;
import org.jenkinsci.plugin.gitea.PullRequestSCMRevision;
import org.jenkinsci.plugin.gitea.client.api.GiteaAuth;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Facade to {@link GiteaSCMSource} and {@link GitSCM} in Jenkins.
 * Used for finding a supported SCM of a job.
 */
public class SCMFacade {
    private static final Logger LOGGER = Logger.getLogger(GiteaChecksPublisher.class.getName());

    /**
     * Find {@link GiteaSCMSource} (or Gitea repository) used by the {@code job}.
     *
     * @param job
     *            the Jenkins project
     * @return the found Gitea SCM source used or empty
     */
    @CheckForNull
    public SCMSource findSCMSource(final Job<?, ?> job) {
        return SCMSource.SourceByItem.findSource(job);
    }

    /**
     * Find {@link GiteaSCMSource} (or Gitea repository) used by the {@code job}.
     *
     * @param job
     *            the Jenkins project
     * @return the found Gitea SCM source used or empty
     */
    public Optional<GiteaSCMSource> findGiteaSCMSource(final Job<?, ?> job) {
        SCMSource source = findSCMSource(job);
        return source instanceof GiteaSCMSource ? Optional.of((GiteaSCMSource) source) : Optional.empty();
    }

    /**
     * Find {@link GitSCMSource} used by the {@code job}.
     *
     * @param job
     *            the Jenkins project
     * @return the found Git SCM source or empty
     */
    public Optional<GitSCMSource> findGitSCMSource(final Job<?, ?> job) {
        SCMSource source = findSCMSource(job);
        return source instanceof GitSCMSource ? Optional.of((GitSCMSource) source) : Optional.empty();
    }

    /**
     * Finds the {@link GitSCM} used by the {@code run}.
     *
     * @param run
     *            the run to get the SCM from
     * @return the found GitSCM or empty
     */
    public Optional<GitSCM> findGitSCM(final Run<?, ?> run) {
        SCM scm = getScm(run);

        return toGitScm(scm);
    }

    /**
     * Finds the {@link GitSCM} used by the {@code job}.
     * 
     * @param job
     *            the job to get the SCM from
     * @return the found GitSCM or empty
     */
    public Optional<GitSCM> findGitSCM(final Job<?, ?> job) {
        SCM scm = getScm(job);

        return toGitScm(scm);
    }

    private Optional<GitSCM> toGitScm(final SCM scm) {
        if (scm instanceof GitSCM) {
            return Optional.of((GitSCM) scm);
        }

        return Optional.empty();
    }

    UserRemoteConfig getUserRemoteConfig(final GitSCM scm) {
        List<UserRemoteConfig> configs = scm.getUserRemoteConfigs();
        if (configs.isEmpty()) {
            return new UserRemoteConfig(null, null, null, null);
        }
        return configs.get(0);
    }

    /**
     * Find {@link StandardCredentials} with the {@code credentialsId} used by the
     * {@code job}.
     *
     * @param job
     *                      the Jenkins project
     * @param credentialsId
     *                      the id of the target credentials
     * @return the found Gitea App credentials or empty
     */
    public Optional<StandardCredentials> findGiteaAppCredentials(final Job<?, ?> job, final String credentialsId) {
        StandardCredentials credential = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardCredentials.class, job, ACL.SYSTEM,
                        Collections.emptyList()),
                CredentialsMatchers.allOf(
                        AuthenticationTokens.matcher(GiteaAuth.class),
                        CredentialsMatchers.withId(credentialsId)));

        return Optional.ofNullable(credential);
    }

    /**
     * Find {@link SCMHead} (or branch) used by the {@code job}.
     *
     * @param job
     *            the Jenkins project
     * @return the found SCM head or empty
     */
    public Optional<SCMHead> findHead(final Job<?, ?> job) {
        SCMHead head = SCMHead.HeadByItem.findHead(job);
        return Optional.ofNullable(head);
    }

    /**
     * Fetch the current {@link SCMRevision} used by the {@code head} of the
     * {@code source}.
     *
     * @param source
     *               the Gitea repository
     * @param head
     *               the branch
     * @return the fetched revision or empty
     */
    public Optional<SCMRevision> findRevision(final SCMSource source, final SCMHead head) {
        try {
            return Optional.ofNullable(source.fetch(head, null));
        }
        catch (IOException | InterruptedException e) {
            throw new IllegalStateException(String.format("Could not fetch revision from repository: %s and branch: %s",
                    source.getId(), head.getName()), e);
        }
    }

    /**
     * Find the current {@link SCMRevision} of the {@code source} and {@code run}
     * locally through
     * {@link jenkins.scm.api.SCMRevisionAction}.
     *
     * @param source
     *               the Gitea repository
     * @param run
     *               the Jenkins run
     * @return the found revision or empty
     */
    public Optional<SCMRevision> findRevision(final GiteaSCMSource source, final Run<?, ?> run) {
        return Optional.ofNullable(SCMRevisionAction.getRevision(source, run));
    }

    /**
     * Find the hash value in {@code revision}.
     *
     * @param revision
     *                 the revision for a build
     * @return the found hash or empty
     */
    public Optional<String> findHash(final SCMRevision revision) {
        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl) {
            return Optional.of(((AbstractGitSCMSource.SCMRevisionImpl) revision).getHash());
        }
        else if (revision instanceof PullRequestSCMRevision) {
            return Optional.of(((PullRequestSCMRevision) revision).getOrigin().getHash());
        }
        else {
            return Optional.empty();
        }
    }

    /**
     * Returns the SCM in a given build. If no SCM can be determined, then a
     * {@link NullSCM} instance will be returned.
     *
     * @param run
     *            the build to get the SCM from
     *
     * @return the SCM
     */
    public SCM getScm(final Run<?, ?> run) {
        return getScm(run.getParent());
    }

    /**
     * Returns the SCM in a given job. If no SCM can be determined, then a
     * {@link NullSCM} instance will be returned.
     *
     * @param job
     *            the job to get the SCM from
     *
     * @return the SCM
     */
    public SCM getScm(final Job<?, ?> job) {
        if (job instanceof AbstractProject) {
            return extractFromProject((AbstractProject<?, ?>) job);
        }
        else if (job instanceof SCMTriggerItem) {
            return extractFromPipeline(job);
        }
        return new NullSCM();
    }

    private SCM extractFromPipeline(final Job<?, ?> job) {
        Collection<? extends SCM> scms = ((SCMTriggerItem) job).getSCMs();
        if (!scms.isEmpty()) {
            return scms.iterator().next(); // TODO: what should we do if more than one SCM has been used
        }

        if (job instanceof WorkflowJob) {
            FlowDefinition definition = ((WorkflowJob) job).getDefinition();
            if (definition instanceof CpsScmFlowDefinition) {
                return ((CpsScmFlowDefinition) definition).getScm();
            }
        }

        return new NullSCM();
    }

    private SCM extractFromProject(final AbstractProject<?, ?> job) {
        if (job.getScm() != null) {
            return job.getScm();
        }

        SCM scm = job.getRootProject().getScm();
        if (scm != null) {
            return scm;
        }

        return new NullSCM();
    }
}
