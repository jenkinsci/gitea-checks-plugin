package io.jenkins.plugins.checks.gitea;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Job;
import hudson.model.Run;
import java.util.Optional;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import org.jenkinsci.plugin.gitea.GiteaSCMSource;

/**
 * Provides a {@link GiteaChecksContext} for a Jenkins job that uses a supported {@link GiteaSCMSource}.
 */
final class GiteaSCMSourceChecksContext extends GiteaChecksContext {
    @CheckForNull
    private final String sha;

    @CheckForNull
    private final Run<?, ?> run;

    static GiteaSCMSourceChecksContext fromRun(final Run<?, ?> run, final String runURL, final SCMFacade scmFacade) {
        return new GiteaSCMSourceChecksContext(run.getParent(), run, runURL, scmFacade);
    }

    static GiteaSCMSourceChecksContext fromJob(final Job<?, ?> job, final String runURL, final SCMFacade scmFacade) {
        return new GiteaSCMSourceChecksContext(job, null, runURL, scmFacade);
    }

    /**
     * Creates a {@link GiteaSCMSourceChecksContext} according to the job and run, if provided. All attributes are computed during this period.
     *
     * @param job
     *         a Gitea Branch Source project
     * @param run
     *         a run of a Gitea Branch Source project
     * @param runURL
     *         the URL to the Jenkins run
     * @param scmFacade
     *         a facade for Jenkins SCM
     */
    private GiteaSCMSourceChecksContext(
            final Job<?, ?> job, @CheckForNull final Run<?, ?> run, final String runURL, final SCMFacade scmFacade) {
        super(job, runURL, scmFacade);
        this.run = run;
        this.sha = Optional.ofNullable(run).map(this::resolveHeadSha).orElse(resolveHeadSha(job));
    }

    @Override
    public String getHeadSha() {
        if (sha == null || sha.isBlank()) {
            throw new IllegalStateException("No SHA found for job: " + getJob().getName());
        }

        return sha;
    }

    @Override
    public String getRepoOwner() {
        GiteaSCMSource source = resolveSource();
        if (source == null) {
            throw new IllegalStateException("No Gitea SCM source found for job: " + getJob().getName());
        } else {
            return source.getRepoOwner();
        }
    }

    @Override
    public String getRepo() {
        GiteaSCMSource source = resolveSource();
        if (source == null) {
            throw new IllegalStateException("No Gitea SCM source found for job: " + getJob().getName());
        } else {
            return source.getRepository();
        }
    }

    @Override
    public String getGiteaServerUrl() {
        GiteaSCMSource giteaSCMSource = getScmFacade()
                .findGiteaSCMSource(getJob())
                .orElseThrow(() ->
                        new IllegalArgumentException("Couldn't get GiteaSCMSource from job: " + getJob().getName()));
        return giteaSCMSource.getServerUrl();
    }

    @Override
    public String getRepository() {
        GiteaSCMSource source = resolveSource();
        if (source == null) {
            throw new IllegalStateException("No Gitea SCM source found for job: " + getJob().getName());
        } else {
            return source.getRepoOwner() + "/" + source.getRepository();
        }
    }

    @Override
    public boolean isValid(final FilteredLog logger) {
        logger.logError("Trying to resolve checks parameters from Gitea SCM...");

        if (resolveSource() == null) {
            logger.logError("Job does not use Gitea SCM");

            return false;
        }

        if (!hasValidCredentials(logger)) {
            return false;
        }

        if (sha == null || sha.isBlank()) {
            logger.logError("No HEAD SHA found for %s", getRepository());

            return false;
        }

        return true;
    }

    @Override
    protected Optional<Run<?, ?>> getRun() {
        return Optional.ofNullable(run);
    }

    @Override
    @CheckForNull
    protected String getCredentialsId() {
        GiteaSCMSource source = resolveSource();
        if (source == null) {
            return null;
        }

        return source.getCredentialsId();
    }

    @CheckForNull
    private GiteaSCMSource resolveSource() {
        return getScmFacade().findGiteaSCMSource(getJob()).orElse(null);
    }

    @CheckForNull
    private String resolveHeadSha(final Run<?, ?> theRun) {
        GiteaSCMSource source = resolveSource();
        if (source != null) {
            Optional<SCMRevision> revision = getScmFacade().findRevision(source, theRun);
            if (revision.isPresent()) {
                return getScmFacade().findHash(revision.get()).orElse(null);
            }
        }

        return null;
    }

    @CheckForNull
    private String resolveHeadSha(final Job<?, ?> job) {
        GiteaSCMSource source = resolveSource();
        Optional<SCMHead> head = getScmFacade().findHead(job);
        if (source != null && head.isPresent()) {
            Optional<SCMRevision> revision = getScmFacade().findRevision(source, head.get());
            if (revision.isPresent()) {
                return getScmFacade().findHash(revision.get()).orElse(null);
            }
        }

        return null;
    }
}
