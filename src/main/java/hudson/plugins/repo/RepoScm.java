/*
 * The MIT License
 *
 * Copyright (c) 2010, Brad Larson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.repo;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.PollingResult.Change;
import hudson.util.FormValidation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * The main entrypoint of the plugin. This class contains code to store user
 * configuration and to check out the code using a repo binary.
 */
public class RepoScm extends SCM {

    private static Logger debug = Logger
        .getLogger("hudson.plugins.repo.RepoScm");

    private final String manifestRepositoryUrl;

    // Advanced Fields:
    private final String manifestBranch;
    private final String manifestFile;
    private final String repoUrl;
    private final String mirrorDir;
    private final int jobs;
    private final String localManifest;
    private final String destinationDir;
    private final boolean currentBranch;
    private final boolean quiet;
    private final String dynamicTrigger;

    /**
     * Returns the manifest repository URL.
     */
    public String getManifestRepositoryUrl() {
        return manifestRepositoryUrl;
    }

    /**
     * Returns the manifest branch name. By default, this is null and repo
     * defaults to "master".
     */
    public String getManifestBranch() {
        return manifestBranch;
    }

    /**
     * Returns the initial manifest file name. By default, this is null and repo
     * defaults to "default.xml"
     */
    public String getManifestFile() {
        return manifestFile;
    }

    /**
     * Returns the repo url. by default, this is null and
     * repo is fetched from aosp
     */
    public String getRepoUrl() {
        return repoUrl;
    }
    /**
     * Returns the name of the mirror directory. By default, this is null and
     * repo does not use a mirror.
     */
    public String getMirrorDir() {
        return mirrorDir;
    }

    /**
     * Returns the number of jobs used for sync. By default, this is null and
     * repo does not use concurrent jobs.
     */
    public int getJobs() {
        return jobs;
    }

    /**
     * Returns the contents of the local_manifest.xml. By default, this is null
     * and a local_manifest.xml is neither created nor modified.
     */
    public String getLocalManifest() {
        return localManifest;
    }

    /**
     * Returns the destination directory. By default, this is null and the
     * source is synced to the root of the workspace.
     */
    public String getDestinationDir() {
        return destinationDir;
    }

    /**
     * Returns the value of currentBranch.
     */
    public boolean isCurrentBranch() {
        return currentBranch;
    }

    /**
     * Returns the value of quiet.
     */
    public boolean isQuiet() {
        return quiet;
    }

    /**
     * Returns the file name to generate a dynamic trigger file to.
     * by default, this is null and there isn't any generated
     */
    public String getDynamicTrigger() {
        return dynamicTrigger;
    }

    /**
     * The constructor takes in user parameters and sets them. Each job using
     * the RepoSCM will call this constructor.
     *
     * @param manifestRepositoryUrl
     *            The URL for the manifest repository.
     * @param manifestBranch
     *            The branch of the manifest repository. Typically this is null
     *            or the empty string, which will cause repo to default to
     *            "master".
     * @param manifestFile
     *            The file to use as the repository manifest. Typically this is
     *            null which will cause repo to use the default of "default.xml"
     * @param mirrorDir
     *            The path of the mirror directory to reference when
     *            initializing repo.
     * @param jobs
     *            The number of concurrent jobs to use for the sync command. If
     *            this is 0 or negative the jobs parameter is not specified.
     * @param localManifest
     *            May be null, a string containing XML, or an URL.
     *            If XML, this string is written to .repo/local_manifest.xml
     *            If an URL, the URL is fetched and the content is written
     *            to .repo/local_manifest.xml
     * @param destinationDir
     *            If not null then the source is synced to the destinationDir
     *            subdirectory of the workspace.
     * @param repoUrl
     *            If not null then use this url as repo base,
     *            instead of the default
     * @param currentBranch
     * 			  if this value is true,
     *            add "-c" options when excute "repo sync".
     * @param quiet
     * 			  if this value is true,
     *            add "-q" options when excute "repo sync".
     *
     * @param dynamicTrigger
     *            if not null, generate a gerrit-trigger dynamic trigger file
     *            using this filename
     */
    @DataBoundConstructor
    public RepoScm(final String manifestRepositoryUrl,
            final String manifestBranch, final String manifestFile,
            final String mirrorDir, final int jobs,
            final String localManifest, final String destinationDir,
            final String repoUrl,
            final boolean currentBranch,
            final boolean quiet,
            final String dynamicTrigger
            ) {
        this.manifestRepositoryUrl = manifestRepositoryUrl;
        this.manifestBranch = Util.fixEmptyAndTrim(manifestBranch);
        this.manifestFile = Util.fixEmptyAndTrim(manifestFile);
        this.mirrorDir = Util.fixEmptyAndTrim(mirrorDir);
        this.jobs = jobs;
        this.localManifest = Util.fixEmptyAndTrim(localManifest);
        this.destinationDir = Util.fixEmptyAndTrim(destinationDir);
        this.currentBranch = currentBranch;
        this.quiet = quiet;
        this.repoUrl = Util.fixEmptyAndTrim(repoUrl);
        this.dynamicTrigger = dynamicTrigger;
            }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(
            final AbstractBuild<?, ?> build, final Launcher launcher,
            final TaskListener listener) throws IOException,
           InterruptedException {
               // We add our SCMRevisionState from within checkout, so this shouldn't
               // be called often. However it will be called if this is the first
               // build, if a build was aborted before it reported the repository
               // state, etc.
               return null;
            }

    @Override
    protected PollingResult compareRemoteRevisionWith(
            final AbstractProject<?, ?> project, final Launcher launcher,
            final FilePath workspace, final TaskListener listener,
            final SCMRevisionState baseline) throws IOException,
              InterruptedException {
                  SCMRevisionState myBaseline = baseline;
                  if (myBaseline == null) {
                      // Probably the first build, or possibly an aborted build.
                      myBaseline = getLastState(project.getLastBuild());
                      if (myBaseline == null) {
                          return PollingResult.BUILD_NOW;
                      }
                  }

                  FilePath repoDir;
                  if (destinationDir != null) {
                      repoDir = workspace.child(destinationDir);
                      if (!repoDir.isDirectory()) {
                          repoDir.mkdirs();
                      }
                  } else {
                      repoDir = workspace;
                  }

                  if (!checkoutCode(launcher, repoDir, listener.getLogger())) {
                      // Some error occurred, try a build now so it gets logged.
                      return new PollingResult(myBaseline, myBaseline,
                              Change.INCOMPARABLE);
                  }

                  final RevisionState currentState =
                      new RevisionState(getManifest(launcher, repoDir,
                                  listener.getLogger(), true), manifestBranch,
                              listener.getLogger());
                  final Change change;
                  if (currentState.equals(myBaseline)) {
                      change = Change.NONE;
                  } else {
                      change = Change.SIGNIFICANT;
                  }
                  return new PollingResult(myBaseline, currentState, change);
            }

    @Override
    public boolean checkout(
            @SuppressWarnings("rawtypes") final AbstractBuild build,
            final Launcher launcher, final FilePath workspace,
            final BuildListener listener, final File changelogFile)
    throws IOException, InterruptedException {

    FilePath repoDir;
    if (destinationDir != null) {
        repoDir = workspace.child(destinationDir);
        if (!repoDir.isDirectory()) {
            repoDir.mkdirs();
        }
    } else {
        repoDir = workspace;
    }

    if (!checkoutCode(launcher, repoDir, listener.getLogger())) {
        return false;
    }

    if (dynamicTrigger != null) {
        final String manifest = getManifest(launcher, repoDir, listener.getLogger(), false);
        final String dynamicTriggerContent = createDynamicTrigger(manifest,  listener.getLogger());
        final File outf = new File(workspace.child(dynamicTrigger).toString());
        outf.createNewFile();
        FileWriter out = new FileWriter(outf, false);
        out.write(dynamicTriggerContent);
        out.flush();
        out.close();
    }


    final String manifest =
        getManifest(launcher, repoDir, listener.getLogger(), true);
    final RevisionState currentState =
        new RevisionState(manifest, manifestBranch,
                listener.getLogger());
    build.addAction(currentState);
    final RevisionState previousState =
        getLastState(build.getPreviousBuild());

    ChangeLog.saveChangeLog(currentState, previousState, changelogFile,
            launcher, repoDir);
    build.addAction(new TagAction(build));
    return true;
            }

    private int doSync(final Launcher launcher, final FilePath workspace,
            final OutputStream logger)
        throws IOException, InterruptedException {
        final List<String> commands = new ArrayList<String>(4);
        debug.log(Level.FINE, "Syncing out code in: " + workspace.getName());
        commands.clear();
        commands.add(getDescriptor().getExecutable());
        commands.add("sync");
        commands.add("-d");
        if (isCurrentBranch()) {
            commands.add("-c");
        }
        if (isQuiet()) {
            commands.add("-q");
        }
        if (jobs > 0) {
            commands.add("--jobs=" + jobs);
        }
        int returnCode =
            launcher.launch().stdout(logger).pwd(workspace)
            .cmds(commands).join();
        return returnCode;
    }

    private boolean checkoutCode(final Launcher launcher,
            final FilePath workspace, final OutputStream logger)
        throws IOException, InterruptedException {
        final List<String> commands = new ArrayList<String>(4);

        debug.log(Level.INFO, "Checking out code in: " + workspace.getName());

        commands.add(getDescriptor().getExecutable());
        commands.add("init");
        commands.add("-u");
        commands.add(manifestRepositoryUrl);
        if (manifestBranch != null) {
            commands.add("-b");
            commands.add(manifestBranch);
        }
        if (manifestFile != null) {
            commands.add("-m");
            commands.add(manifestFile);
        }
        if (mirrorDir != null) {
            commands.add("--reference=" + mirrorDir);
        }
        if (repoUrl != null) {
            commands.add("--repo-url=" + repoUrl);
            commands.add("--no-repo-verify");
        }
        int returnCode =
            launcher.launch().stdout(logger).pwd(workspace)
            .cmds(commands).join();
        if (returnCode != 0) {
            return false;
        }
        if (workspace != null) {
            FilePath rdir = workspace.child(".repo");
            FilePath lm = rdir.child("local_manifest.xml");
            lm.delete();
            if (localManifest != null) {
                if (localManifest.startsWith("<?xml")) {
                    lm.write(localManifest, null);
                } else {
                    URL url = new URL(localManifest);
                    lm.copyFrom(url);
                }
            }
        }

        returnCode = doSync(launcher, workspace, logger);
        if (returnCode != 0) {
            debug.log(Level.WARNING, "Sync failed. Resetting repository");
            commands.clear();
            commands.add(getDescriptor().getExecutable());
            commands.add("forall");
            commands.add("-c");
            commands.add("git reset --hard");
            launcher.launch().stdout(logger).pwd(workspace).cmds(commands)
                .join();
            returnCode = doSync(launcher, workspace, logger);
            if (returnCode != 0) {
                return false;
            }
        }

        return true;
    }

    private String getManifest(final Launcher launcher,
            final FilePath workspace, final OutputStream logger,
            final boolean makeStatic)
        throws IOException, InterruptedException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final List<String> commands = new ArrayList<String>(6);
        commands.add(getDescriptor().getExecutable());
        commands.add("manifest");
        commands.add("-o");
        commands.add("-");
        if (makeStatic) {
            commands.add("-r");
        }
        // TODO: should we pay attention to the output from this?
        launcher.launch().stderr(logger).stdout(output).pwd(workspace)
            .cmds(commands).join();
        final String manifestText = output.toString();
        debug.log(Level.FINEST, manifestText);
        return manifestText;
    }

    private RevisionState getLastState(final Run<?, ?> lastBuild) {
        if (lastBuild == null) {
            return null;
        }
        final RevisionState lastState =
            lastBuild.getAction(RevisionState.class);
        if (lastState != null && lastState.getBranch() == manifestBranch) {
            return lastState;
        }
        return getLastState(lastBuild.getPreviousBuild());
    }

    private String createDynamicTrigger(final String manifest, final PrintStream logger) {
        StringBuilder ret = new StringBuilder();
        try {
            final InputSource xmlSource = new InputSource();
            xmlSource.setCharacterStream(new StringReader(manifest));
            final Document doc =
                DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(xmlSource);

            if (!doc.getDocumentElement().getNodeName().equals("manifest")) {
                logger.println("Error - malformed manifest");
                return null;
            }


            String defaultRevision = null;
            final NodeList defaultNodes = doc.getElementsByTagName("default");
            if (defaultNodes.getLength() > 1) {
                logger.println("Error - malformed manifest - can only have one <default> element");
                return null;
            }
            if (defaultNodes.getLength() == 1) {
                final Element defaultElement =  (Element) defaultNodes.item(0);
                defaultRevision = Util.fixEmptyAndTrim(defaultElement.getAttribute("revision"));
            }



            final NodeList projectNodes = doc.getElementsByTagName("project");
            final int numProjects = projectNodes.getLength();
            for (int i = 0; i < numProjects; i++) {
                final Element projectElement = (Element) projectNodes.item(i);
                final String serverPath =
                    Util.fixEmptyAndTrim(projectElement
                            .getAttribute("name"));
                String revision =
                    Util.fixEmptyAndTrim(projectElement
                            .getAttribute("revision"));


                if (revision == null) {
                    revision = defaultRevision;
                }
                if (revision == null || serverPath == null) {
                    logger.println("Error - malformed manifest - revision or name missing at project element nr " + i);
                    return null;
                }

                ret.append("p=" + serverPath + "\n");
                ret.append("b=" + revision + "\n");
                ret.append("f~.*\n");
                ret.append("\n");

            }
        } catch (final Exception e) {
            logger.println(e);
            return null;
        }
        return ret.toString();
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new ChangeLog();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * A DescriptorImpl contains variables used server-wide. In our case, we
     * only store the path to the repo executable, which defaults to just
     * "repo". This class also handles some Jenkins housekeeping.
     */
    @Extension
    public static class DescriptorImpl extends SCMDescriptor<RepoScm> {
        private String repoExecutable;

        /**
         * Call the superclass constructor and load our configuration from the
         * file system.
         */
        public DescriptorImpl() {
            super(null);
            load();
        }

        @Override
        public String getDisplayName() {
            return "Gerrit Repo";
        }

        @Override
        public boolean configure(final StaplerRequest req,
                final JSONObject json)
        throws hudson.model.Descriptor.FormException {
        repoExecutable =
            Util.fixEmptyAndTrim(json.getString("executable"));
        save();
        return super.configure(req, json);
        }

        /**
         * Check that the specified parameter exists on the file system and is a
         * valid executable.
         *
         * @param value
         *            A path to an executable on the file system.
         * @return Error if the file doesn't exist, otherwise return OK.
         */
        public FormValidation doExecutableCheck(
                @QueryParameter final String value) {
            return FormValidation.validateExecutable(value);
                }

        /**
         * Returns the command to use when running repo. By default, we assume
         * that repo is in the server's PATH and just return "repo".
         */
        public String getExecutable() {
            if (repoExecutable == null) {
                return "repo";
            } else {
                return repoExecutable;
            }
        }
    }
}
