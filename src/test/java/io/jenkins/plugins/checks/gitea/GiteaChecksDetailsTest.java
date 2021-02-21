package io.jenkins.plugins.checks.gitea;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugin.gitea.client.api.GiteaCommitState;
import org.junit.jupiter.api.Test;

import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksDetails.ChecksDetailsBuilder;
import io.jenkins.plugins.checks.api.ChecksStatus;

import static org.assertj.core.api.Assertions.*;

class GiteaChecksDetailsTest {
    @Test
    void shouldReturnAllGiteaObjectsCorrectly() {
        ChecksDetails details = new ChecksDetailsBuilder()
                .withName("checks")
                .withStatus(ChecksStatus.COMPLETED)
                .withConclusion(ChecksConclusion.SUCCESS)
                .withDetailsURL("https://ci.jenkins.io")
                .build();

        GiteaChecksDetails giteaDetails = new GiteaChecksDetails(details);
        assertThat(giteaDetails.getName()).isEqualTo("checks");
        assertThat(giteaDetails.getStatus()).isEqualTo(GiteaCommitState.SUCCESS);
        assertThat(giteaDetails.getDetailsURL()).isPresent().hasValue("https://ci.jenkins.io");
    }

    @Test
    void shouldReturnEmptyWhenDetailsURLIsBlank() {
        GiteaChecksDetails giteaChecksDetails =
                new GiteaChecksDetails(new ChecksDetailsBuilder().withDetailsURL(StringUtils.EMPTY).build());
        assertThat(giteaChecksDetails.getDetailsURL()).isEmpty();
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenDetailsURLIsNotHttpOrHttpsScheme() {
        GiteaChecksDetails giteaChecksDetails =
                new GiteaChecksDetails(new ChecksDetailsBuilder().withDetailsURL("ci.jenkins.io").build());
        assertThatThrownBy(giteaChecksDetails::getDetailsURL)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The details url is not http or https scheme: ci.jenkins.io");
    }
}
