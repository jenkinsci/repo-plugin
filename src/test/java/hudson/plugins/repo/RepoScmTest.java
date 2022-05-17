package hudson.plugins.repo;

import hudson.AbortException;
import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link JenkinsRule} based tests for {@link RepoScm}
 */
public class RepoScmTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        final String manifestRepositoryUrl = "https://gerrit/projects/platform.git";
        RepoScm scm = new RepoScm(manifestRepositoryUrl);
        scm.setCleanFirst(true);
        project.setScm(scm);
        project.getBuildersList().add(new Shell("ecgo hello"));
        project.save();
        j.configRoundtrip(project);
        project = j.jenkins.getItemByFullName(project.getFullName(), FreeStyleProject.class);
        scm = (RepoScm) project.getScm();
        assertTrue(scm.isCleanFirst());
        assertEquals(manifestRepositoryUrl, scm.getManifestRepositoryUrl());
    }

    @Issue("JENKINS-68562")
    @Test
    public void abortIfUrlLocal() throws Exception {
        final String manifestRepositoryUrl = "https://gerrit/projects/platform.git";
        try {
            new RepoScm(manifestRepositoryUrl).abortIfUrlLocal();
        } catch (AbortException e) {
            fail("https manifest URLs should always be valid");
        }
    }
}
