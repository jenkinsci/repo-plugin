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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.scm.PollingResult.Change;
import hudson.util.FormValidation;

/**
 * The main entrypoint of the plugin. This class contains code to store user
 * configuration and to check out the code using a repo binary.
 */

@ExportedBean
public class RepoScm extends SCM implements Serializable {

	private static Logger debug = Logger
			.getLogger("hudson.plugins.repo.RepoScm");

	private final String manifestRepositoryUrl;

	// Advanced Fields:
	private final String manifestBranch;
	private final String manifestFile;
	private final String manifestGroup;
	private final String repoUrl;
	private final String mirrorDir;
	private final int jobs;
	private final int depth;
	private final String localManifest;
	private final String destinationDir;
	private final boolean currentBranch;
	private final boolean resetFirst;
	private final boolean quiet;
	private boolean forceSync;
	private final boolean trace;
	private final boolean showAllChanges;
	private boolean noTags;
	private final String customGitConfig;

	/**
	 * Returns the manifest repository URL.
	 */
	@Exported
	public String getManifestRepositoryUrl() {
		return manifestRepositoryUrl;
	}

	/**
	 * Returns the manifest branch name. By default, this is null and repo
	 * defaults to "master".
	 */
	@Exported
	public String getManifestBranch() {
		return manifestBranch;
	}

	/**
	 * Same as {@link #getManifestBranch()} but with <em>default</em>
	 * values of parameters expanded.
	 * @param environment   an existing environment, which contains already
	 *                      properties from the current build
	 * @param project       the project that is being built
	 */
	private EnvVars getEnvVars(final EnvVars environment,
			final AbstractProject<?, ?> project) {
		// create an empty vars map
		final EnvVars finalEnv = new EnvVars();
		final ParametersDefinitionProperty params = project.getProperty(
				ParametersDefinitionProperty.class);
		if (params != null) {
			for (ParameterDefinition param
					: params.getParameterDefinitions()) {
				if (param instanceof StringParameterDefinition) {
					final StringParameterDefinition stpd =
						(StringParameterDefinition) param;
					final String dflt = stpd.getDefaultValue();
					if (dflt != null) {
						finalEnv.put(param.getName(), dflt);
					}
				}
			}
		}
		// now merge the settings from the last build environment
		if (environment != null) {
			finalEnv.overrideAll(environment);
		}

		if (customGitConfig != null) {
			finalEnv.override("XDG_CONFIG_HOME",
					finalEnv.get("WORKSPACE") + "/.config");
		}

		EnvVars.resolve(finalEnv);
		return finalEnv;
	}

	/**
	 * Returns the initial manifest file name. By default, this is null and repo
	 * defaults to "default.xml"
	 */
	@Exported
	public String getManifestFile() {
		return manifestFile;
	}

	/**
	 * Returns the group of projects to fetch. By default, this is null and
	 * repo will fetch the default group.
	 */
	@Exported
	public String getManifestGroup() {
		return manifestGroup;
	}

	/**
	 * Returns the repo url. by default, this is null and
	 * repo is fetched from aosp
	 */
	@Exported
	public String getRepoUrl() {
		return repoUrl;
	}

	/**
	 * Returns the name of the mirror directory. By default, this is null and
	 * repo does not use a mirror.
	 */
	@Exported
	public String getMirrorDir() {
		return mirrorDir;
	}

	/**
	 * Returns the number of jobs used for sync. By default, this is null and
	 * repo does not use concurrent jobs.
	 */
	@Exported
	public int getJobs() {
		return jobs;
	}

	/**
	 * Returns the depth used for sync.  By default, this is null and repo
	 * will sync the entire history.
	 */
	@Exported
	public int getDepth() {
		return depth;
	}
	/**
	 * Returns the contents of the local_manifest.xml. By default, this is null
	 * and a local_manifest.xml is neither created nor modified.
	 */
	@Exported
	public String getLocalManifest() {
		return localManifest;
	}

	/**
	 * Returns the destination directory. By default, this is null and the
	 * source is synced to the root of the workspace.
	 */
	@Exported
	public String getDestinationDir() {
		return destinationDir;
	}

	/**
	 * Returns the value of currentBranch.
	 */
	@Exported
	public boolean isCurrentBranch() {
		return currentBranch;
	}
	/**
	 * Returns the value of resetFirst.
	 */
	@Exported
	public boolean isResetFirst() { return resetFirst; }
	/**
	 * Returns the value of showAllChanges.
	 */
	@Exported
	public boolean isShowAllChanges() { return showAllChanges; }
	/**
	 * Returns the value of quiet.
	 */
	@Exported
	public boolean isQuiet() {
		return quiet;
	}
	/**
	 * Returns the value of forceSync.
	 */
	@Exported
	public boolean isForceSync() {
		return forceSync;
	}
	/**
	 * Returns the value of trace.
	 */
	@Exported
	public boolean isTrace() { return trace; }
	/**
	 * Returns the value of noTags.
	 */
	@Exported
	public boolean isNoTags() { return noTags; }
	/**
	 * Returns the value of customGitConfig.
	 */
	@Exported
	public String getcustomGitConfig() { return customGitConfig; }

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
	 * @param manifestGroup
	 *            The group name for the projects that need to be fetched.
	 *            Typically, this is null and all projects tagged 'default' will
	 *            be fetched.
	 * @param mirrorDir
	 *            The path of the mirror directory to reference when
	 *            initializing repo.
	 * @param jobs
	 *            The number of concurrent jobs to use for the sync command. If
	 *            this is 0 or negative the jobs parameter is not specified.
	 * @param depth
	 *            This is the depth to use when syncing.  By default this is 0
	 *            and the full history is synced.
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
	 *            If this value is true, add the "-c" option when executing
	 *            "repo sync".
	 * @param resetFirst
	 *            If this value is true, do "repo forall -c 'git reset --hard'"
	 *            before syncing.
	 * @param quiet
	 *            If this value is true, add the "-q" option when executing
	 *            "repo sync".
	 * @param trace
	 *            If this value is true, add the "--trace" option when
	 *            executing "repo init" and "repo sync".
	 * @param showAllChanges
	 *            If this value is true, add the "--first-parent" option to
	 *            "git log" when determining changesets.
	 * @param customGitConfig
	 *            A string contains lines, and each line is consist of
	 *            a git config name value set.
	 */
	@DataBoundConstructor
	public RepoScm(final String manifestRepositoryUrl,
			final String manifestBranch, final String manifestFile,
			final String manifestGroup, final String mirrorDir, final int jobs,
			final int depth,
			final String localManifest, final String destinationDir,
			final String repoUrl,
			final boolean currentBranch,
			final boolean resetFirst,
			final boolean quiet,
			final boolean trace,
			final boolean showAllChanges,
			final String customGitConfig) {
		this.manifestRepositoryUrl = manifestRepositoryUrl;
		this.manifestBranch = Util.fixEmptyAndTrim(manifestBranch);
		this.manifestGroup = Util.fixEmptyAndTrim(manifestGroup);
		this.manifestFile = Util.fixEmptyAndTrim(manifestFile);
		this.mirrorDir = Util.fixEmptyAndTrim(mirrorDir);
		this.jobs = jobs;
		this.depth = depth;
		this.localManifest = Util.fixEmptyAndTrim(localManifest);
		this.destinationDir = Util.fixEmptyAndTrim(destinationDir);
		this.currentBranch = currentBranch;
		this.resetFirst = resetFirst;
		this.quiet = quiet;
		this.trace = trace;
		this.showAllChanges = showAllChanges;
		this.repoUrl = Util.fixEmptyAndTrim(repoUrl);
		this.customGitConfig = customGitConfig;
	}

  /**
   * Enables --force-sync option on repo sync command.
	 * @param forceSync
	 *        If this value is true, add the "--force-sync" option when
   *        executing "repo sync".
   */
  @DataBoundSetter
  public void setForceSync(final boolean forceSync) {
    this.forceSync = forceSync;
  }

	/**
	 * Set noTags.
	 *
	 * @param noTags
	 *            If this value is true, add the "--no-tags" option when
	 *            executing "repo sync".
	 */
	@DataBoundSetter
	public final void setNoTags(final boolean noTags) {
		this.noTags = noTags;
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
		final EnvVars env = getEnvVars(null, project);
		final String expandedManifestBranch = env.expand(manifestBranch);
		final AbstractBuild<?, ?> lastBuild = project.getLastBuild();

		if (myBaseline == null) {
			// Probably the first build, or possibly an aborted build.
			myBaseline = getLastState(lastBuild, expandedManifestBranch);
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

		if (!checkoutCode(launcher, repoDir, env, listener.getLogger())) {
			// Some error occurred, try a build now so it gets logged.
			return new PollingResult(myBaseline, myBaseline,
					Change.INCOMPARABLE);
		}

		final RevisionState currentState = new RevisionState(
				getStaticManifest(launcher, repoDir, listener.getLogger()),
				getManifestRevision(launcher, repoDir, listener.getLogger()),
				expandedManifestBranch, listener.getLogger());
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

		AbstractProject<?, ?> proj = build.getProject();
		EnvVars env = build.getEnvironment(listener);
		env = getEnvVars(env, proj);
		if (!checkoutCode(launcher, repoDir, env, listener.getLogger())) {
			return false;
		}
		final String manifest =
				getStaticManifest(launcher, repoDir, listener.getLogger());
		final String manifestRevision =
				getManifestRevision(launcher, repoDir, listener.getLogger());
		final String expandedBranch = env.expand(manifestBranch);
		final RevisionState currentState =
				new RevisionState(manifest, manifestRevision, expandedBranch,
						listener.getLogger());
		build.addAction(currentState);

		final Run previousBuild = build.getPreviousBuild();
		final RevisionState previousState =
				getLastState(previousBuild, expandedBranch);

		ChangeLog.saveChangeLog(currentState, previousState, changelogFile,
				launcher, repoDir, showAllChanges);
		build.addAction(new TagAction(build));
		return true;
	}

	private int doSync(final Launcher launcher, final FilePath workspace,
			final OutputStream logger, final EnvVars env)
		throws IOException, InterruptedException {
		final List<String> commands = new ArrayList<String>(4);
		debug.log(Level.FINE, "Syncing out code in: " + workspace.getName());
		commands.clear();
		if (resetFirst) {
			commands.add(getDescriptor().getExecutable());
			commands.add("forall");
			commands.add("-c");
			commands.add("git reset --hard");
			int syncCode = launcher.launch().stdout(logger)
				.stderr(logger).pwd(workspace).cmds(commands).join();

			if (syncCode != 0) {
				debug.log(Level.WARNING, "Failed to reset first.");
			}
			commands.clear();
		}
		commands.add(getDescriptor().getExecutable());
		if (trace) {
		    commands.add("--trace");
		}
		commands.add("sync");
		commands.add("-d");
		if (isCurrentBranch()) {
			commands.add("-c");
		}
		if (isQuiet()) {
			commands.add("-q");
		}
		if (isForceSync()) {
			commands.add("--force-sync");
		}
		if (jobs > 0) {
			commands.add("--jobs=" + jobs);
		}
		if (isNoTags()) {
			commands.add("--no-tags");
		}

		int returnCode =
				launcher.launch().stdout(logger).pwd(workspace)
						.cmds(commands).envs(env).join();
		return returnCode;
	}

	private boolean checkoutCode(final Launcher launcher,
			final FilePath workspace,
			final EnvVars env,
			final OutputStream logger)
			throws IOException, InterruptedException {

		customGitConfig(launcher, workspace, env, logger);

		final List<String> commands = new ArrayList<String>(4);

		debug.log(Level.INFO, "Checking out code in: " + workspace.getName());

		commands.add(getDescriptor().getExecutable());
		if (trace) {
		    commands.add("--trace");
		}
		commands.add("init");
		commands.add("-u");
		commands.add(env.expand(manifestRepositoryUrl));
		if (manifestBranch != null) {
			commands.add("-b");
			commands.add(env.expand(manifestBranch));
		}
		if (manifestFile != null) {
			commands.add("-m");
			commands.add(env.expand(manifestFile));
		}
		if (mirrorDir != null) {
			commands.add("--reference=" + env.expand(mirrorDir));
		}
		if (repoUrl != null) {
			commands.add("--repo-url=" + env.expand(repoUrl));
			commands.add("--no-repo-verify");
		}
		if (manifestGroup != null) {
			commands.add("-g");
			commands.add(env.expand(manifestGroup));
		}
		if (depth != 0) {
			commands.add("--depth=" + depth);
		}
		int returnCode =
				launcher.launch().stdout(logger).pwd(workspace)
						.cmds(commands).envs(env).join();
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

		returnCode = doSync(launcher, workspace, logger, env);
		if (returnCode != 0) {
			debug.log(Level.WARNING, "Sync failed. Resetting repository");
			commands.clear();
			commands.add(getDescriptor().getExecutable());
			commands.add("forall");
			commands.add("-c");
			commands.add("git reset --hard");
			launcher.launch().stdout(logger).pwd(workspace).cmds(commands)
				.envs(env).join();
			returnCode = doSync(launcher, workspace, logger, env);
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

	private String getManifestRevision(final Launcher launcher,
			final FilePath workspace, final OutputStream logger)
			throws IOException, InterruptedException {
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		final List<String> commands = new ArrayList<String>(6);
		commands.add("git");
		commands.add("rev-parse");
		commands.add("HEAD");
		launcher.launch().stderr(logger).stdout(output).pwd(
				new FilePath(workspace, ".repo/manifests"))
				.cmds(commands).join();
		final String manifestText = output.toString().trim();
		debug.log(Level.FINEST, manifestText);
		return manifestText;
	}

	private RevisionState getLastState(final Run<?, ?> lastBuild,
			final String expandedManifestBranch) {
		if (lastBuild == null) {
			return null;
		}
		final RevisionState lastState =
				lastBuild.getAction(RevisionState.class);
		if (lastState != null
				&& StringUtils.equals(lastState.getBranch(),
						expandedManifestBranch)) {
			return lastState;
		}
		return getLastState(lastBuild.getPreviousBuild(),
				expandedManifestBranch);
	}

	private void customGitConfig(final Launcher launcher,
			final FilePath workspace,
			final EnvVars env,
			final OutputStream logger)
		throws IOException, InterruptedException {
		if (customGitConfig != null) {

			FilePath dir = new FilePath(workspace, ".config/git");
			dir.mkdirs();

			String[] configs = customGitConfig.split("\\r?\\n");
			for (String config : configs) {
				String[] options = config.split("\\s+");
				if (options.length == 2) {
					final List<String> commands = new ArrayList<String>(4);
					commands.add("git");
					commands.add("config");
					commands.add("--file");
					commands.add(env.get("XDG_CONFIG_HOME") + "/git/config");
					commands.add(options[0]);
					commands.add(options[1]);
					launcher.launch().stdout(logger).pwd(workspace)
							.cmds(commands).envs(env).join();
					commands.clear();
				}
			}
		}
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
	 * A DescriptorImpl contains variables used server-wide. In our263 case, we
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
