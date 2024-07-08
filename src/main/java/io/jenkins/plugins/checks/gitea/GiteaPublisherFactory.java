package io.jenkins.plugins.checks.gitea;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.util.PluginLogger;
import java.util.Optional;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;

/**
 * An factory which produces {@link GiteaChecksPublisher}.
 */
@Extension
public class GiteaPublisherFactory extends ChecksPublisherFactory {
    private final SCMFacade scmFacade;
    private final DisplayURLProvider urlProvider;

    /**
     * Creates a new instance of {@link GiteaPublisherFactory}.
     */
    public GiteaPublisherFactory() {
        this(new SCMFacade(), DisplayURLProvider.get());
    }

    @VisibleForTesting
    GiteaPublisherFactory(final SCMFacade scmFacade, final DisplayURLProvider urlProvider) {
        super();

        this.scmFacade = scmFacade;
        this.urlProvider = urlProvider;
    }

    @Override
    protected Optional<ChecksPublisher> createPublisher(final Run<?, ?> run, final TaskListener listener) {
        final String runURL = urlProvider.getRunURL(run);
        return createPublisher(
                listener,
                GiteaSCMSourceChecksContext.fromRun(run, runURL, scmFacade),
                new GitSCMChecksContext(run, runURL, scmFacade));
    }

    @Override
    protected Optional<ChecksPublisher> createPublisher(final Job<?, ?> job, final TaskListener listener) {
        return createPublisher(
                listener, GiteaSCMSourceChecksContext.fromJob(job, urlProvider.getJobURL(job), scmFacade));
    }

    private Optional<ChecksPublisher> createPublisher(
            final TaskListener listener, final GiteaChecksContext... contexts) {
        FilteredLog causeLogger = new FilteredLog("Causes for no suitable publisher found: ");
        PluginLogger consoleLogger = new PluginLogger(listener.getLogger(), "Gitea Checks");

        for (GiteaChecksContext ctx : contexts) {
            if (ctx.isValid(causeLogger)) {
                return Optional.of(new GiteaChecksPublisher(ctx, consoleLogger));
            }
        }

        consoleLogger.logEachLine(causeLogger.getErrorMessages());
        return Optional.empty();
    }
}
