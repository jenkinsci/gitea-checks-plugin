package io.jenkins.plugins.checks.gitea;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import org.jenkinsci.plugin.gitea.client.api.GiteaCommitState;

import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksStatus;

/**
 * An adaptor which adapts the generic checks objects of {@link ChecksDetails} to the specific Gitea checks run.
 */
class GiteaChecksDetails {
    private final ChecksDetails details;

    private static final int GITEA_MAX_DESCRIPTION_SIZE = 256;

    /**
     * Construct with the given {@link ChecksDetails}.
     *
     * @param details the details of a generic check run
     */
    GiteaChecksDetails(final ChecksDetails details) {
        if (details.getConclusion() == ChecksConclusion.NONE) {
            if (details.getStatus() == ChecksStatus.COMPLETED) {
                throw new IllegalArgumentException("No conclusion has been set when status is completed.");
            }

            if (details.getCompletedAt().isPresent()) {
                throw new IllegalArgumentException("No conclusion has been set when \"completedAt\" is provided.");
            }
        }

        this.details = details;
    }

    public String getName() {
        return details.getName()
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new IllegalArgumentException("The check name is blank."));
    }

    /**
     * Returns the name of a Gitea commit status. This is displayed on a PR page on gitea, together with
     * the description.
     *
     * @return the name of the check
     */
    public String getContextString() {
        return getName();
    }

    /**
     * Returns the {@link GiteaCommitState} of a Gitea check run.
     *
     * @return the status of a check run
     * @throws IllegalArgumentException if the status of the {@code details} is not one of {@link ChecksStatus}
     */
    @SuppressWarnings("fallthrough")
    public GiteaCommitState getStatus() {

        switch (details.getStatus()) {
            case NONE:
            case QUEUED:
            case IN_PROGRESS:
                return GiteaCommitState.PENDING;
            case COMPLETED:
                switch (details.getConclusion()) {
                    case NEUTRAL:
                    case SKIPPED:
                    case SUCCESS:
                        return GiteaCommitState.SUCCESS;
                    case ACTION_REQUIRED:
                        return GiteaCommitState.WARNING;
                    case CANCELED:
                    case FAILURE:
                        return GiteaCommitState.FAILURE;
                    case TIME_OUT:
                        return GiteaCommitState.ERROR;
                }
            default:
                throw new IllegalArgumentException("Unsupported checks status: " + details.getStatus());
        }
    }

    /**
     * Returns the URL of site which contains details of a Gitea check run.
     *
     * @return an URL of the site
     */
    public Optional<String> getDetailsURL() {
        if (details.getDetailsURL().filter(StringUtils::isBlank).isPresent()) {
            return Optional.empty();
        }

        details.getDetailsURL().ifPresent(url -> {
            if (!StringUtils.equalsAny(URI.create(url).getScheme(), "http", "https")) {
                throw new IllegalArgumentException("The details url is not http or https scheme: " + url);
            }
        }
        );
        return details.getDetailsURL();
    }

    /**
     * Returns the description {@link String} of a Gitea check run.
     *
     * @return the output of a check run
     */
    public Optional<String> getDescription() {
        if (details.getOutput().isPresent()) {
            return details.getOutput().get().getSummary(GITEA_MAX_DESCRIPTION_SIZE);
        }

        return Optional.empty();
    }

    /**
     * Returns the UTC time when the check started.
     *
     * @deprecated This is being deprecated since 1.0
     * @return the start time of a check
     */
    @Deprecated
    public Optional<Date> getStartedAt() {
        if (details.getStartedAt().isPresent()) {
            return Optional.of(Date.from(
                    details.getStartedAt().get()
                            .toInstant(ZoneOffset.UTC)));
        }
        return Optional.empty();
    }

    /**
     * Returns the UTC time when the check completed.
     *
     * @deprecated This is being deprecated since 1.0
     * @return the completed time of a check
     */
    @Deprecated
    public Optional<Date> getCompletedAt() {
        if (details.getCompletedAt().isPresent()) {
            return Optional.of(Date.from(
                    details.getCompletedAt().get()
                            .toInstant(ZoneOffset.UTC)));
        }
        return Optional.empty();
    }
}
