package io.jenkins.plugins.checks.gitea;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Job;
import hudson.model.Run;
import org.apache.commons.lang3.StringUtils;
import java.util.Optional;

/**
 * Base class for a context that publishes Gitea checks.
 */
public abstract class GiteaChecksContext {
    private final Job<?, ?> job;
    private final String url;
    private final SCMFacade scmFacade;

    protected GiteaChecksContext(final Job<?, ?> job, final String url, final SCMFacade scmFacade) {
        this.job = job;
        this.url = url;
        this.scmFacade = scmFacade;
    }

    /**
     * Returns the commit sha of the run.
     *
     * @return the commit sha of the run
     */
    public abstract String getHeadSha();

    public abstract String getRepoOwner();
    public abstract String getRepo();
    public abstract String getGiteaServerUrl();

    /**
     * Returns the source repository's full name of the run. The full name consists of the owner's name and the
     * repository's name, e.g. jenkins-ci/jenkins
     *
     * @return the source repository's full name
     */
    public abstract String getRepository();

    /**
     * Returns whether the context is valid (with all properties functional) to use.
     *
     * @param logger
     *         the filtered logger
     * @return whether the context is valid to use
     */
    public abstract boolean isValid(FilteredLog logger);

    @CheckForNull
    protected abstract String getCredentialsId();

    /**
     * Returns the credentials to access the remote Gitea repository.
     *
     * @return the credentials
     */
    public StandardCredentials getCredentials() {
        return getGiteaAppCredentials(getCredentialsId());
    }

    /**
     * Returns the URL of the run's summary page, e.g. https://ci.jenkins.io/job/Core/job/jenkins/job/master/2000/.
     *
     * @return the URL of the summary page
     */
    public String getURL() {
        return url;
    }

    protected Job<?, ?> getJob() {
        return job;
    }

    protected SCMFacade getScmFacade() {
        return scmFacade;
    }


    protected StandardCredentials getGiteaAppCredentials(final String credentialsId) {
        return findGiteaAppCredentials(credentialsId).orElseThrow(() ->
                new IllegalStateException("No Gitea APP credentials available for job: " + getJob().getName()));
    }

    protected boolean hasGiteaAppCredentials() {
        return findGiteaAppCredentials(StringUtils.defaultIfEmpty(getCredentialsId(), "")).isPresent();
    }

    protected boolean hasCredentialsId() {
        return StringUtils.isNoneBlank(getCredentialsId());
    }

    protected boolean hasValidCredentials(final FilteredLog logger) {
        if (!hasCredentialsId()) {
            logger.logError("No credentials found");

            return false;
        }

        if (!hasGiteaAppCredentials()) {
            logger.logError("No Gitea app credentials found: '%s'", getCredentialsId());
            logger.logError("See: https://plugins.jenkins.io/gitea/");
            return false;
        }

        return true;
    }

    private Optional<StandardCredentials> findGiteaAppCredentials(final String credentialsId) {
        return getScmFacade().findGiteaAppCredentials(getJob(), credentialsId);
    }

    protected abstract Optional<Run<?, ?>> getRun();
}
