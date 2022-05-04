package hudson.plugins.repo;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class Security2478Test {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Issue("SECURITY-2478")
    @Test
    public void checkoutShouldAbortWhenUrlIsNonRemoteAndBuildOnController() throws Exception {
        FreeStyleProject freeStyleProject = rule.createFreeStyleProject();
        String manifestRepositoryUrl = testFolder.newFolder().toString();
        RepoScm scm = new RepoScm(manifestRepositoryUrl);
        freeStyleProject.setScm(scm);
        FreeStyleBuild freeStyleBuild = rule.assertBuildStatus(Result.FAILURE, freeStyleProject.scheduleBuild2(0));
        rule.assertLogContains("Checkout of Repo url '" + manifestRepositoryUrl +
                "' aborted because it references a local directory, " +
                "which may be insecure. You can allow local checkouts anyway by setting the system property '" +
                RepoScm.ALLOW_LOCAL_CHECKOUT_PROPERTY + "' to true.", freeStyleBuild);
    }

    @Issue("SECURITY-2478")
    @Test
    public void checkoutShouldNotAbortWhenUrlIsNonRemoteAndEscapeHatchTrue() throws Exception {
        try {
            RepoScm.ALLOW_LOCAL_CHECKOUT = true;
            FreeStyleProject freeStyleProject = rule.createFreeStyleProject();
            String manifestRepositoryUrl = testFolder.newFolder().toString();
            RepoScm scm = new RepoScm(manifestRepositoryUrl);
            freeStyleProject.setScm(scm);
            FreeStyleBuild freeStyleBuild = rule.assertBuildStatus(Result.FAILURE, freeStyleProject.scheduleBuild2(0));

            // build fails because of manifestRepositoryUrl is not a repo(git) repository, but we don't care,
            // we verify that build was not aborted because of RepoScm uses local path.
            rule.assertLogNotContains("Checkout of Repo url '" + manifestRepositoryUrl +
                    "' aborted because it references a local directory, " +
                    "which may be insecure. You can allow local checkouts anyway by setting the system property '" +
                    RepoScm.ALLOW_LOCAL_CHECKOUT_PROPERTY + "' to true.", freeStyleBuild);
        } finally {
            RepoScm.ALLOW_LOCAL_CHECKOUT = false;
        }
    }

    @Issue("SECURITY-2478")
    @Test
    public void checkoutShouldNotAbortWhenUrlIsNonRemoteAndBuildOnAgent() throws Exception {
        DumbSlave agent = rule.createOnlineSlave();
        FreeStyleProject freeStyleProject = rule.createFreeStyleProject();

        String manifestRepositoryUrl = testFolder.newFolder().toString();

        RepoScm scm = new RepoScm(manifestRepositoryUrl);
        freeStyleProject.setScm(scm);
        freeStyleProject.setAssignedLabel(agent.getSelfLabel());

        // build fails because of manifestRepositoryUrl is not a repo(git) repository, but we don't care,
        // we verify that build was not aborted because of RepoScm uses local path.
        rule.assertLogNotContains("Checkout of Repo url '" + manifestRepositoryUrl +
                "' aborted because it references a local directory, " +
                "which may be insecure. You can allow local checkouts anyway by setting the system property '" +
                RepoScm.ALLOW_LOCAL_CHECKOUT_PROPERTY + "' to true.", freeStyleProject.scheduleBuild2(0).get());
    }
}
