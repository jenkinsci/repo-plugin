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
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

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
	 *            If not null this string is written to .repo/local_manifest.xml
	 * @param destinationDir
	 *            If not null then the source is synced to the destinationDir
	 *            subdirectory of the workspace.
	 */
	@DataBoundConstructor
	public RepoScm(final String manifestRepositoryUrl,
			final String manifestBranch, final String manifestFile,
			final String mirrorDir, final int jobs,
			final String localManifest, final String destinationDir) {
		this.manifestRepositoryUrl = manifestRepositoryUrl;
		this.manifestBranch = Util.fixEmptyAndTrim(manifestBranch);
		this.manifestFile = Util.fixEmptyAndTrim(manifestFile);
		this.mirrorDir = Util.fixEmptyAndTrim(mirrorDir);
		this.jobs = jobs;
		this.localManifest = Util.fixEmptyAndTrim(localManifest);
		this.destinationDir = Util.fixEmptyAndTrim(destinationDir);
		// TODO: repoUrl
		this.repoUrl = null;
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
				new RevisionState(getStaticManifest(launcher, repoDir,
						listener.getLogger()), manifestBranch,
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
		final String manifest =
				getStaticManifest(launcher, repoDir, listener.getLogger());
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
			if (localManifest != null) {
				lm.write(localManifest, null);
			} else {
				lm.delete();
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

	private String getStaticManifest(final Launcher launcher,
			final FilePath workspace, final OutputStream logger)
			throws IOException, InterruptedException {
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		final List<String> commands = new ArrayList<String>(6);
		commands.add(getDescriptor().getExecutable());
		commands.add("manifest");
		commands.add("-o");
		commands.add("-");
		commands.add("-r");
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
