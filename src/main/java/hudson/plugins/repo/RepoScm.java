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

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * The main entrypoint of the plugin. This class contains code to store user
 * configuration and to check out the code using a repo binary.
 */
public class RepoScm extends SCM {

	private final String manifestRepositoryUrl;

	// Advanced Fields:
	private final String manifestBranch;
	private final String manifestFile;
	private final String repoUrl;

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
    * Returns the initial manifest file name. By default
    */
	public String getManifestFile() {
		return manifestFile;
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
	 *            The initial manifest file.
	 */
	@DataBoundConstructor
	public RepoScm(final String manifestRepositoryUrl,
			final String manifestBranch,
			final String manifestFile) {
		this.manifestRepositoryUrl = manifestRepositoryUrl;
		this.manifestBranch = Util.fixEmptyAndTrim(manifestBranch);
		this.manifestFile = Util.fixEmptyAndTrim(manifestFile);
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
		if (!checkoutCode(launcher, workspace, listener.getLogger())) {
			// Some error occurred, try a build now so it gets logged.
			return new PollingResult(myBaseline, myBaseline,
					Change.INCOMPARABLE);
		}
		final RevisionState currentState =
				new RevisionState(getStaticManifest(launcher, workspace,
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
		if (!checkoutCode(launcher, workspace, listener.getLogger())) {
			return false;
		}
		final String manifest =
				getStaticManifest(launcher, workspace, listener.getLogger());
		final RevisionState currentState =
				new RevisionState(manifest, manifestBranch,
						listener.getLogger());
		build.addAction(currentState);
		final RevisionState previousState =
				getLastState(build.getPreviousBuild());
		ChangeLog.saveChangeLog(currentState, previousState,
				changelogFile, launcher, workspace);
		build.addAction(new TagAction(build));
		return true;
	}

	private boolean checkoutCode(final Launcher launcher,
			final FilePath workspace, final OutputStream logger)
			throws IOException, InterruptedException {
		final List<String> commands = new ArrayList<String>(4);
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
		commands.clear();
		commands.add(getDescriptor().getExecutable());
		commands.add("sync");
		commands.add("-d");
		returnCode =
				launcher.launch().stdout(logger).pwd(workspace)
						.cmds(commands).join();
		if (returnCode != 0) {
			return false;
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
        launcher.launch().stderr(logger).stdout(output).pwd(workspace).
            cmds(commands).join();
        final String manifestText = output.toString();
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
