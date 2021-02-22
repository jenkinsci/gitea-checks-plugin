package io.jenkins.plugins.checks.gitea;

import hudson.model.Run;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GitSCMChecksContextTest {
    @Test
    public void shouldGetRepository() {
        for (String url : new String[]{
                "git@197.168.2.0:flozzone/gitea-checks-plugin",
                "git@localhost:flozzone/gitea-checks-plugin",
                "git@github.com:flozzone/gitea-checks-plugin",
                "http://github.com/flozzone/gitea-checks-plugin.git",
                "https://github.com/flozzone/gitea-checks-plugin.git"
        }) {
            assertThat(new GitSCMChecksContext(mock(Run.class), "").getRepository(url))
                    .isEqualTo("flozzone/gitea-checks-plugin");
        }
    }
}
