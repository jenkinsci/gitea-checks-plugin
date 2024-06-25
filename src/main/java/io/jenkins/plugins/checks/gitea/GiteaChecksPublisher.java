package io.jenkins.plugins.checks.gitea;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksPublisher;
import io.jenkins.plugins.util.PluginLogger;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import org.jenkinsci.plugin.gitea.client.api.Gitea;
import org.jenkinsci.plugin.gitea.client.api.GiteaAuth;
import org.jenkinsci.plugin.gitea.client.api.GiteaCommitStatus;
import org.jenkinsci.plugin.gitea.client.api.GiteaConnection;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * A publisher which publishes Gitea check runs.
 */
public class GiteaChecksPublisher extends ChecksPublisher {
    private static final Logger SYSTEM_LOGGER = Logger.getLogger(GiteaChecksPublisher.class.getName());

    private final GiteaChecksContext context;
    private final PluginLogger buildLogger;
    private final String giteaServerUrl;

    /**
     * {@inheritDoc}.
     *
     * @param context
     *                a context which contains SCM properties
     */
    public GiteaChecksPublisher(final GiteaChecksContext context, final PluginLogger buildLogger) {
        this(context, buildLogger, context.getGiteaServerUrl());
    }

    GiteaChecksPublisher(final GiteaChecksContext context, final PluginLogger buildLogger,
            final String giteaServerUrl) {
        super();

        this.context = context;
        this.buildLogger = buildLogger;
        this.giteaServerUrl = giteaServerUrl;
    }

    /**
     * Publishes a Gitea check run.
     *
     * @param details
     *                the details of a check run
     */
    @Override
    public void publish(final ChecksDetails details) {
        try {
            GiteaConnection giteaConnection = connect(giteaServerUrl, context.getCredentials());

            GiteaChecksDetails giteaDetails = new GiteaChecksDetails(details);
            publishGiteaCommitStatus(giteaConnection, giteaDetails);

            buildLogger.log("Gitea check (name: %s, status: %s, description: %s) has been published.",
                    giteaDetails.getContextString(),
                    giteaDetails.getStatus(), giteaDetails.getDescription());
            SYSTEM_LOGGER.fine(format("Published check for repo: %s, sha: %s, job name: %s, name: %s, status: %s",
                    context.getRepository(),
                    context.getHeadSha(),
                    context.getJob().getFullName(),
                    giteaDetails.getContextString(),
                    giteaDetails.getStatus()).replaceAll("[\r\n]", ""));
        }
        catch (IOException | InterruptedException e) {
            String message = "Failed Publishing Gitea checks: ";
            SYSTEM_LOGGER.log(Level.WARNING, (message + details).replaceAll("[\r\n]", ""), e);
            buildLogger.log(message + e);
        }
    }

    private static GiteaConnection connect(String serverUrl, StandardCredentials credentials)
            throws IOException, InterruptedException {
        return Gitea.server(serverUrl)
                .as(AuthenticationTokens.convert(GiteaAuth.class, credentials))
                .open();
    }

    private GiteaCommitStatus publishGiteaCommitStatus(GiteaConnection giteaConnection,
            GiteaChecksDetails giteaChecksDetails) throws IOException, InterruptedException {
        GiteaCommitStatus commitStatus = new GiteaCommitStatus();

        giteaChecksDetails.getDetailsURL().ifPresent(commitStatus::setTargetUrl);

        commitStatus.setContext(giteaChecksDetails.getContextString());

        giteaChecksDetails.getDescription().ifPresent(commitStatus::setDescription);

        commitStatus.setState(giteaChecksDetails.getStatus());

        return giteaConnection.createCommitStatus(
                context.getRepoOwner(),
                context.getRepo(),
                context.getHeadSha(),
                commitStatus);
    }
}
