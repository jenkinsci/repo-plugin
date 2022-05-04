package hudson.plugins.repo;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

public class ManifestValidatorTest {

    @Issue("SECURITY-2478")
    @Test
    public void validateWhenFetchAttributeReferencesLocalPathThenAbort() {
        String manifest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<manifest>\n" +
                "  <remote  name=\"local\"\n" +
                "           fetch=\"file:///Users/d.platonov/workdir/\"\n" +
                "           revision=\"master\"\n" +
                "           review=\"\" />\n" +
                "\n" +
                "  <project name=\"localProject\" path=\"localProject\" groups=\"lib\" remote=\"local\" />\n" +
                "</manifest>";
        try {
            ManifestValidator.validate(manifest.getBytes(StandardCharsets.UTF_8), "repoUrl");
            fail("should fail because fetch attribute in remote tag references a local path");
        } catch (IOException e) {
            assertThat(e.getMessage(), is("Checkout of Repo url 'repoUrl' aborted because manifest references a local directory, " +
                    "which may be insecure. You can allow local checkouts anyway by setting the system property '" +
                    RepoScm.ALLOW_LOCAL_CHECKOUT_PROPERTY + "' to true."));
        }
    }

    @Issue("SECURITY-2478")
    @Test
    public void validateWhenValidManifestThenDoNotAbort() {
        String manifest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<manifest>\n" +
                "  <remote  name=\"origin\"\n" +
                "           fetch=\"..\"\n" + // https://stackoverflow.com/questions/18251358/repo-manifest-xml-what-does-the-fetch-mean
                "           revision=\"master\"\n" +
                "           review=\"https://github.com\" />\n" +
                "\n" +
                "  <project name=\"any\" path=\"any\" groups=\"gr\" remote=\"origin\" />\n" +
                " </manifest>";

        try {
            ManifestValidator.validate(manifest.getBytes(StandardCharsets.UTF_8), "repoUrl");
        } catch (Exception e) {
            fail("fail because input is valid and no exception expected");
        }
    }
}
