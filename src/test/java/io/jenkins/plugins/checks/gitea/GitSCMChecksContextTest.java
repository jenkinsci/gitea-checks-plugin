package io.jenkins.plugins.checks.gitea;

import hudson.model.Run;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GitSCMChecksContextTest {
    @Test
    public void shouldGetRepository() {
        for (String url : new String[]{
                "git@197.168.2.0:Caellion/gitea-checks-plugin",
                "git@localhost:Caellion/gitea-checks-plugin",
                "git@github.com:Caellion/gitea-checks-plugin",
                "http://github.com/Caellion/gitea-checks-plugin.git",
                "https://github.com/Caellion/gitea-checks-plugin.git"
        }) {
            assertThat(new GitSCMChecksContext(mock(Run.class), "").getRepository(url))
                    .isEqualTo("Caellion/gitea-checks-plugin");
        }
    }
}
