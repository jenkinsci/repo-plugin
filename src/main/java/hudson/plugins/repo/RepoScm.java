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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Job;
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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
	@CheckForNull private String manifestFile;
	@CheckForNull private String manifestGroup;
	@CheckForNull private String manifestPlatform;
	@CheckForNull private String repoUrl;
	@CheckForNull private String repoBranch;
	@CheckForNull private String mirrorDir;
	@CheckForNull private String manifestBranch;
	@CheckForNull private int jobs;
	@CheckForNull private int depth;
	@CheckForNull private String localManifest;
	@CheckForNull private String destinationDir;
	@CheckForNull private boolean currentBranch;
	@CheckForNull private boolean resetFirst;
	@CheckForNull private boolean cleanFirst;
	@CheckForNull private boolean quiet;
	@CheckForNull private boolean forceSync;
	@CheckForNull private boolean trace;
	@CheckForNull private boolean showAllChanges;
	@CheckForNull private boolean noTags;
	@CheckForNull private boolean manifestSubmodules;
	@CheckForNull private boolean fetchSubmodules;
	@CheckForNull private Set<String> ignoreProjects;
	@CheckForNull private EnvVars extraEnvVars;
	@CheckForNull private boolean noCloneBundle;
	@CheckForNull private boolean worktree;

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
	 * Merge the provided environment with the <em>default</em> values of
	 * the project parameters. The values from the provided environment
	 * take precedence.
	 * @param environment   an existing environment, which contains already
	 *                      properties from the current build
	 * @param project       the project that is being built
	 */
	private EnvVars getEnvVars(final EnvVars environment,
			final Job<?, ?> project) {
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

		// merge extra env vars, if specified
		if (extraEnvVars != null) {
			finalEnv.overrideAll(extraEnvVars);
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
	 * Returns the platform of projects to fetch. By default, this is null and
	 * repo will automatically fetch the appropriate platform.
	 */
	@CheckForNull
	public String getManifestPlatform() {
		return manifestPlatform;
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
	 * Returns the repo branch. by default, this is null and
	 * repo is used from the default branch
	 */
	@Exported
	public String getRepoBranch() {
		return repoBranch;
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
	 * Returns the contents of the local_manifests/local.xml. By default, this is null
	 * and a local_manifests/local.xml is neither created nor modified.
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
	 * returns list of ignore projects.
	 */
	@Exported
	public String getIgnoreProjects() {
		return StringUtils.join(ignoreProjects, '\n');
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
	public boolean isResetFirst() {
		return resetFirst;
	}

	/**
	 * Returns the value of cleanFirst.
	 */
	@Exported
	public boolean isCleanFirst() {
		return cleanFirst;
	}

	/**
	 * Returns the value of showAllChanges.
	 */
	@Exported
	public boolean isShowAllChanges() {
		return showAllChanges;
	}

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
	public boolean isTrace() {
		return trace;
	}

	/**
	 * Returns the value of noTags.
	 */
	@Exported
	public boolean isNoTags() {
		return noTags;
	}
	/**
	 * Returns the value of noCloneBundle.
	 */
	@Exported
	public boolean isNoCloneBundle() {
		return noCloneBundle;
	}
	/**
	 * Returns the value of isWorktree.
	 */
	@Exported
	public boolean isWorktree() {
		return worktree;
	}

	/**
	 * Returns the value of manifestSubmodules.
	 */
	@Exported
	public boolean isManifestSubmodules() {
		return manifestSubmodules;
	}

	/**
	 * Returns the value of fetchSubmodules.
	 */
	public boolean isFetchSubmodules() {
		return fetchSubmodules;
	}

	/**
	 * Returns the value of extraEnvVars.
	 */
	@Exported
	public Map<String, String> getExtraEnvVars() {
		return extraEnvVars;
	}

	/**
	 * The constructor takes in user parameters and sets them. Each job using
	 * the RepoSCM will call this constructor.
	 *
	 * @param manifestRepositoryUrl The URL for the manifest repository.
	 * @param manifestBranch        The branch of the manifest repository. Typically this is null
	 *                              or the empty string, which will cause repo to default to
	 *                              "master".
	 * @param manifestFile          The file to use as the repository manifest. Typically this is
	 *                              null which will cause repo to use the default of "default.xml"
	 * @param manifestGroup         The group name for the projects that need to be fetched.
	 *                              Typically, this is null and all projects tagged 'default' will
	 *                              be fetched.
	 * @param mirrorDir             The path of the mirror directory to reference when
	 *                              initializing repo.
	 * @param jobs                  The number of concurrent jobs to use for the sync command. If
	 *                              this is 0 or negative the jobs parameter is not specified.
	 * @param depth                 This is the depth to use when syncing.  By default this is 0
	 *                              and the full history is synced.
	 * @param localManifest         May be null, a string containing XML, or an URL.
	 *                              If XML, this string is written to
	 *                              .repo/local_manifests/local.xml
	 *                              If an URL, the URL is fetched and the content is written
	 *                              to .repo/local_manifests/local.xml
	 * @param destinationDir        If not null then the source is synced to the destinationDir
	 *                              subdirectory of the workspace.
	 * @param repoUrl               If not null then use this url as repo base,
	 *                              instead of the default.
	 * @param currentBranch         If this value is true, add the "-c" option when executing
	 *                              "repo sync".
	 * @param resetFirst            If this value is true, do "repo forall -c 'git reset --hard'"
	 *                              before syncing.
	 * @param quiet                 If this value is true, add the "-q" option when executing
	 *                              "repo sync".
	 * @param trace                 If this value is true, add the "--trace" option when
	 *                              executing "repo init" and "repo sync".
	 * @param showAllChanges        If this value is true, add the "--first-parent" option to
	 *                              "git log" when determining changesets.
	 *
	 */
	@Deprecated
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
				   final boolean showAllChanges) {
		this(manifestRepositoryUrl);
		setManifestBranch(manifestBranch);
		setManifestGroup(manifestGroup);
		setManifestFile(manifestFile);
		setMirrorDir(mirrorDir);
		setJobs(jobs);
		setDepth(depth);
		setLocalManifest(localManifest);
		setDestinationDir(destinationDir);
		setCurrentBranch(currentBranch);
		setResetFirst(resetFirst);
		setCleanFirst(false);
		setQuiet(quiet);
		setTrace(trace);
		setShowAllChanges(showAllChanges);
		setRepoUrl(repoUrl);
		ignoreProjects = Collections.<String>emptySet();
		setWorktree(false);
	}

	/**
	 * The constructor takes in user parameters and sets them. Each job using
	 * the RepoSCM will call this constructor.
	 *
	 * @param manifestRepositoryUrl The URL for the manifest repository.
	 */
	@DataBoundConstructor
	public RepoScm(final String manifestRepositoryUrl) {
		this.manifestRepositoryUrl = manifestRepositoryUrl;
		manifestFile = null;
		manifestGroup = null;
		repoUrl = null;
		repoBranch = null;
		mirrorDir = null;
		manifestBranch = null;
		jobs = 0;
		depth = 0;
		localManifest = null;
		destinationDir = null;
		currentBranch = false;
		resetFirst = false;
		cleanFirst = false;
		quiet = false;
		forceSync = false;
		trace = false;
		showAllChanges = false;
		noTags = false;
		manifestSubmodules = false;
		fetchSubmodules = false;
		ignoreProjects = Collections.<String>emptySet();
		noCloneBundle = false;
		worktree = false;
	}

	/**
	 * Set the manifest branch name.
	 *
	 * @param manifestBranch
	 *        The branch of the manifest repository. Typically this is null
	 *        or the empty string, which will cause repo to default to
	 *        "master".
     */
	@DataBoundSetter
	public void setManifestBranch(@CheckForNull final String manifestBranch) {
		this.manifestBranch = Util.fixEmptyAndTrim(manifestBranch);
	}

	/**
	 * Set the initial manifest file name.
	 *
	 * @param manifestFile
	 *        The file to use as the repository manifest. Typically this is
	 *        null which will cause repo to use the default of "default.xml"
     */
	@DataBoundSetter
	public void setManifestFile(@CheckForNull final String manifestFile) {
		this.manifestFile = Util.fixEmptyAndTrim(manifestFile);
	}

	/**
	 * Set the group of projects to fetch.
	 *
	 * @param manifestGroup
	 *        The group name for the projects that need to be fetched.
	 *        Typically, this is null and all projects tagged 'default' will
	 *        be fetched.
     */
	@DataBoundSetter
	public void setManifestGroup(@CheckForNull final String manifestGroup) {
		this.manifestGroup = Util.fixEmptyAndTrim(manifestGroup);
	}

	/**
	 * Set the platform of projects to fetch.
	 *
	 * @param manifestPlatform
	 *        The platform for the projects that need to be fetched.
	 *        Typically, this is null and only projects for the current platform
	 *        will be fetched.
	 */
	@DataBoundSetter
	public void setManifestPlatform(@CheckForNull final String manifestPlatform) {
		this.manifestPlatform = Util.fixEmptyAndTrim(manifestPlatform);
	}

	/**
	 * Set the name of the mirror directory.
	 *
	 * @param mirrorDir
	 *        The path of the mirror directory to reference when
	 *        initializing repo.
     */
	@DataBoundSetter
	public void setMirrorDir(@CheckForNull final String mirrorDir) {
		this.mirrorDir = Util.fixEmptyAndTrim(mirrorDir);
	}

	/**
	 * Set the number of jobs used for sync.
	 *
	 * @param jobs
	 *        The number of concurrent jobs to use for the sync command. If
	 *        this is 0 or negative the jobs parameter is not specified.
     */
	@DataBoundSetter
	public void setJobs(final int jobs) {
		this.jobs = jobs;
	}

	/**
	 * Set the depth used for sync.
	 *
	 * @param depth
	 *        This is the depth to use when syncing.  By default this is 0
	 *        and the full history is synced.
     */
	@DataBoundSetter
	public void setDepth(final int depth) {
		this.depth = depth;
	}

	/**
	 * Set the content of the local manifest.
	 *
	 * @param localManifest
	 *        May be null, a string containing XML, or an URL.
	 *        If XML, this string is written to .repo/local_manifests/local.xml
	 *        If an URL, the URL is fetched and the content is written
	 *        to .repo/local_manifests/local.xml
     */
	@DataBoundSetter
	public void setLocalManifest(@CheckForNull final String localManifest) {
		this.localManifest = Util.fixEmptyAndTrim(localManifest);
	}

	/**
	 * Set the destination directory.
	 *
	 * @param destinationDir
	 *        If not null then the source is synced to the destinationDir
	 *        subdirectory of the workspace.
     */
	@DataBoundSetter
	public void setDestinationDir(@CheckForNull final String destinationDir) {
		this.destinationDir = Util.fixEmptyAndTrim(destinationDir);
	}

	/**
	 * Set currentBranch.
	 *
	 * @param currentBranch
	 * 		  If this value is true, add the "-c" option when executing
	 *        "repo sync".
     */
	@DataBoundSetter
	public void setCurrentBranch(final boolean currentBranch) {
		this.currentBranch = currentBranch;
	}

	/**
	 * Set resetFirst.
	 *
	 * @param resetFirst
	 *        If this value is true, do "repo forall -c 'git reset --hard'"
	 *        before syncing.
     */
	@DataBoundSetter
	public void setResetFirst(final boolean resetFirst) {
		this.resetFirst = resetFirst;
	}

	/**
	 * Set cleanFirst.
	 *
	 * @param cleanFirst
	 *        If this value is true, do "repo forall -c 'git clean -fdx'"
	 *        before syncing.
     */
	@DataBoundSetter
	public void setCleanFirst(final boolean cleanFirst) {
		this.cleanFirst = cleanFirst;
	}

	/**
	 * Set quiet.
	 *
	 * @param quiet
	 * *      If this value is true, add the "-q" option when executing
	 *        "repo sync".
     */
	@DataBoundSetter
	public void setQuiet(final boolean quiet) {
		this.quiet = quiet;
	}

	/**
	 * Set trace.
	 *
	 * @param trace
	 *        If this value is true, add the "--trace" option when
	 *        executing "repo init" and "repo sync".
     */

	@DataBoundSetter
	public void setTrace(final boolean trace) {
		this.trace = trace;
	}

	/**
	 * Set showAllChanges.
	 *
	 * @param showAllChanges
	 *        If this value is true, add the "--first-parent" option to
	 *        "git log" when determining changesets.
     */
	@DataBoundSetter
	public void setShowAllChanges(final boolean showAllChanges) {
		this.showAllChanges = showAllChanges;
	}

	/**
	 * Set noCloneBundle.
	 *
	 * @param noCloneBundle
	 *        If this value is true, add the "--no-clone-bundle" option when
	 *        running the "repo init" and "repo sync" commands.
     */
	@DataBoundSetter
	public void setNoCloneBundle(final boolean noCloneBundle) {
		this.noCloneBundle = noCloneBundle;
	}

	/**
	 * Set worktree.
	 *
	 * @param worktree
	 *        If this value is true, add the "--worktree" option when
	 *        running the "repo init" command.
     */
	@DataBoundSetter
	public void setWorktree(final boolean worktree) {
		this.worktree = worktree;
	}

	/**
	 * Set the repo url.
	 *
	 * @param repoUrl
	 *        If not null then use this url as repo base,
	 *        instead of the default
     */
	@DataBoundSetter
	public void setRepoUrl(@CheckForNull final String repoUrl) {
		this.repoUrl = Util.fixEmptyAndTrim(repoUrl);
	}

	/**
	 * Set the repo branch.
	 *
	 * @param repoBranch
	 *        If not null then use this as branch for repo itself
	 *        instead of the default.
	 */
	@DataBoundSetter
	public void setRepoBranch(@CheckForNull final String repoBranch) {
		this.repoBranch = Util.fixEmptyAndTrim(repoBranch);
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

	/**
	 * Set manifestSubmodules.
	 *
	 * @param manifestSubmodules
	 *            If this value is true, add the "--submodules" option when
	 *            executing "repo init".
	 */
	@DataBoundSetter
	public void setManifestSubmodules(final boolean manifestSubmodules) {
		this.manifestSubmodules = manifestSubmodules;
	}

	/**
	 * Set fetchSubmodules.
	 *
	 * @param fetchSubmodules
	 *            If this value is true, add the "--fetch-submodules" option when
	 *            executing "repo sync".
	 */
	@DataBoundSetter
	public void setFetchSubmodules(final boolean fetchSubmodules) {
		this.fetchSubmodules = fetchSubmodules;
	}

	/**
	 * Sets list of projects which changes will be ignored when
	 * calculating whether job needs to be rebuild. This field corresponds
	 * to serverpath i.e. "name" section of the manifest.
	 * @param ignoreProjects
	 *            String representing project names separated by " ".
	 */
	@DataBoundSetter
	public final void setIgnoreProjects(final String ignoreProjects) {
		if (ignoreProjects == null) {
			this.ignoreProjects = Collections.<String>emptySet();
			return;
		}
		this.ignoreProjects = new LinkedHashSet<String>(
				Arrays.asList(ignoreProjects.split("\\s+")));
	}

	/**
	 * Set additional environment variables to use. These variables will override
	 * any parameter from the project or variable set in environment already.
	 * @param extraEnvVars
	 * 			  Additional environment variables to set.
	 */
	@DataBoundSetter
	public void setExtraEnvVars(@CheckForNull final Map<String, String> extraEnvVars) {
		this.extraEnvVars = extraEnvVars != null ? new EnvVars(extraEnvVars) : null;
	}

	@Override
	public SCMRevisionState calcRevisionsFromBuild(
			@Nonnull final Run<?, ?> build, @Nullable final FilePath workspace,
			@Nullable final Launcher launcher, @Nonnull final TaskListener listener
			) throws IOException, InterruptedException {
		// We add our SCMRevisionState from within checkout, so this shouldn't
		// be called often. However it will be called if this is the first
		// build, if a build was aborted before it reported the repository
		// state, etc.
		return SCMRevisionState.NONE;
	}

	private boolean shouldIgnoreChanges(final RevisionState current, final RevisionState baseline) {
		List<ProjectState>  changedProjects = current.whatChanged(baseline);
		if ((changedProjects == null) || (ignoreProjects == null)) {
			return false;
		}
		if (ignoreProjects.isEmpty()) {
			return false;
		}


		// Check for every changed item if it is not contained in the
		// ignored setting .. project must be rebuilt
		for (ProjectState changed : changedProjects) {
			if (!ignoreProjects.contains(changed.getServerPath())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public PollingResult compareRemoteRevisionWith(
			@Nonnull final Job<?, ?> job, @Nullable final Launcher launcher,
			@Nullable final FilePath workspace, @Nonnull final TaskListener listener,
			@Nonnull final SCMRevisionState baseline) throws IOException,
			InterruptedException {
		SCMRevisionState myBaseline = baseline;
		final EnvVars env = getEnvVars(null, job);
		final String expandedManifestUrl = env.expand(manifestRepositoryUrl);
		final String expandedManifestBranch = env.expand(manifestBranch);
		final String expandedManifestFile = env.expand(manifestFile);
		final Run<?, ?> lastRun = job.getLastBuild();

		if (myBaseline == SCMRevisionState.NONE) {
			// Probably the first build, or possibly an aborted build.
			myBaseline = getLastState(lastRun, expandedManifestUrl,
					expandedManifestBranch, expandedManifestFile);
			if (myBaseline == SCMRevisionState.NONE) {
				return PollingResult.BUILD_NOW;
			}
		}

		FilePath repoDir;
		if (destinationDir != null) {
			repoDir = workspace.child(env.expand(destinationDir));
		} else {
			repoDir = workspace;
		}

		if (!repoDir.isDirectory()) {
			repoDir.mkdirs();
		}

		if (!checkoutCode(launcher, repoDir, env, listener.getLogger())) {
			// Some error occurred, try a build now so it gets logged.
			return new PollingResult(myBaseline, myBaseline,
					Change.INCOMPARABLE);
		}

		final RevisionState currentState = new RevisionState(
				getStaticManifest(launcher, repoDir, listener.getLogger(), env),
				getManifestRevision(launcher, repoDir, listener.getLogger(), env),
				expandedManifestUrl, expandedManifestBranch, expandedManifestFile,
				listener.getLogger());

		final Change change;
		if (currentState.equals(myBaseline)) {
			change = Change.NONE;
		} else {
			if (shouldIgnoreChanges(currentState,
					myBaseline instanceof RevisionState ? (RevisionState) myBaseline : null)) {
				change = Change.NONE;
			} else {
				change = Change.SIGNIFICANT;
			}
		}
		return new PollingResult(myBaseline, currentState, change);
	}

	@Override
	public void checkout(
			@Nonnull final Run<?, ?> build, @Nonnull final Launcher launcher,
			@Nonnull final FilePath workspace, @Nonnull final TaskListener listener,
			@CheckForNull final File changelogFile, @CheckForNull final SCMRevisionState baseline)
			throws IOException, InterruptedException {

		Job<?, ?> job = build.getParent();
		EnvVars env = build.getEnvironment(listener);
		env = getEnvVars(env, job);

		FilePath repoDir;
		if (destinationDir != null) {
			repoDir = workspace.child(env.expand(destinationDir));
		} else {
			repoDir = workspace;
		}

		if (!repoDir.isDirectory()) {
			repoDir.mkdirs();
		}

		if (!checkoutCode(launcher, repoDir, env, listener.getLogger())) {
			throw new IOException("Could not checkout");
		}
		final String manifest =
				getStaticManifest(launcher, repoDir, listener.getLogger(), env);
		final String manifestRevision =
				getManifestRevision(launcher, repoDir, listener.getLogger(), env);
        final String expandedUrl = env.expand(manifestRepositoryUrl);
		final String expandedBranch = env.expand(manifestBranch);
        final String expandedFile = env.expand(manifestFile);
		final RevisionState currentState =
				new RevisionState(manifest, manifestRevision, expandedUrl,
                        expandedBranch, expandedFile, listener.getLogger());
		build.addAction(currentState);

		final Run previousBuild = build.getPreviousBuild();
		final SCMRevisionState previousState =
				getLastState(previousBuild, expandedUrl, expandedBranch, expandedFile);

		if (changelogFile != null) {
			ChangeLog.saveChangeLog(
					currentState,
					previousState == SCMRevisionState.NONE ? null : (RevisionState) previousState,
					changelogFile,
					launcher,
					repoDir,
					showAllChanges);
		}

		// TODO: create a single action displaying all manifests?
		ManifestAction manifestAction = new ManifestAction(build);
		int revisionStateCount = build.getActions(RevisionState.class).size();
		manifestAction.setIndex(revisionStateCount);
		build.addAction(manifestAction);
	}

	private int doSync(final Launcher launcher, @Nonnull final FilePath workspace,
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
			int resetCode = launcher.launch().stdout(logger)
				.stderr(logger).pwd(workspace).cmds(commands).envs(env).join();

			if (resetCode != 0) {
				debug.log(Level.WARNING, "Failed to reset first.");
			}
			commands.clear();
		}
		if (cleanFirst) {
			commands.add(getDescriptor().getExecutable());
			commands.add("forall");
			commands.add("-c");
			commands.add("git clean -fdx");
			int cleanCode = launcher.launch().stdout(logger)
				.stderr(logger).pwd(workspace).cmds(commands).envs(env).join();

			if (cleanCode != 0) {
				debug.log(Level.WARNING, "Failed to clean first.");
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
		if (isNoCloneBundle()) {
			commands.add("--no-clone-bundle");
		}
		if (fetchSubmodules) {
			commands.add("--fetch-submodules");
		}
		return launcher.launch().stdout(logger).pwd(workspace)
                .cmds(commands).envs(env).join();
	}

	private boolean checkoutCode(final Launcher launcher,
			@Nonnull final FilePath workspace,
			final EnvVars env,
			final OutputStream logger)
			throws IOException, InterruptedException {
		final List<String> commands = new ArrayList<String>(4);

		debug.log(Level.INFO, "Checking out code in: {0}", workspace.getName());

		FilePath rdir = workspace.child(".repo");
		FilePath lmdir = rdir.child("local_manifests");
		if (rdir.exists()) {
			// Delete the legacy local_manifest.xml in case it exists from a previous build
			rdir.child("local_manifest.xml").delete();

			if (lmdir.exists()) {
				// Delete contents of local_manifests in case it exists from a previous build
				lmdir.deleteContents();
			}
		}

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
		if (repoBranch != null) {
			commands.add("--repo-branch=" + env.expand(repoBranch));
		}
		if (manifestGroup != null) {
			commands.add("-g");
			commands.add(env.expand(manifestGroup));
		}
		if (manifestPlatform != null) {
			commands.add("-p");
			commands.add(env.expand(manifestPlatform));
		}
		if (depth != 0) {
			commands.add("--depth=" + depth);
		}
		if (isNoCloneBundle()) {
			commands.add("--no-clone-bundle");
		}
		if (isWorktree()) {
			commands.add("--worktree");
		}
		if (currentBranch) {
			commands.add("--current-branch");
		}
		if (noTags) {
			commands.add("--no-tags");
		}
		if (manifestSubmodules) {
			commands.add("--submodules");
		}
		int returnCode =
				launcher.launch().stdout(logger).pwd(workspace)
						.cmds(commands).envs(env).join();
		if (returnCode != 0) {
			return false;
		}

		if (localManifest != null) {
			if (!lmdir.exists()) {
				lmdir.mkdirs();
			}
			FilePath lm = lmdir.child("local.xml");
			String expandedLocalManifest = env.expand(localManifest);
			if (expandedLocalManifest.startsWith("<?xml")) {
				lm.write(expandedLocalManifest, null);
			} else {
				URL url = new URL(expandedLocalManifest);
				lm.copyFrom(url);
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
			final FilePath workspace, final OutputStream logger,
			final EnvVars env)
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
				.cmds(commands).envs(env).join();
		final String manifestText = new String(output.toByteArray(), Charset.defaultCharset());
		debug.log(Level.FINEST, manifestText);
		return manifestText;
	}

	private String getManifestRevision(final Launcher launcher,
			final FilePath workspace, final OutputStream logger,
			final EnvVars env)
			throws IOException, InterruptedException {
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		final List<String> commands = new ArrayList<String>(6);
		commands.add("git");
		commands.add("rev-parse");
		commands.add("HEAD");
		launcher.launch().stderr(logger).stdout(output).pwd(
				new FilePath(workspace, ".repo/manifests"))
				.cmds(commands).envs(env).join();
		final String manifestText = new String(output.toByteArray(),
				Charset.defaultCharset()).trim();
		debug.log(Level.FINEST, manifestText);
		return manifestText;
	}

    private boolean isRelevantState(final RevisionState state, final String url,
                                    final String branch, final String file) {
	    return StringUtils.equals(state.getBranch(), branch)
				&& StringUtils.equals(state.getUrl(), url)
				&& StringUtils.equals(state.getFile(), file);
    }

	@Nonnull
	private SCMRevisionState getLastState(final Run<?, ?> lastBuild,
			final String expandedManifestUrl, final String expandedManifestBranch,
			final String expandedManifestFile) {
		if (lastBuild == null) {
			return RevisionState.NONE;
		}
		final List<RevisionState> lastStateList =
				lastBuild.getActions(RevisionState.class);
		for (RevisionState lastState : lastStateList) {
			if (lastState != null
					&& isRelevantState(lastState, expandedManifestUrl,
							expandedManifestBranch, expandedManifestFile)) {
				return lastState;
			}
		}
		return getLastState(lastBuild.getPreviousBuild(),
				expandedManifestUrl, expandedManifestBranch, expandedManifestFile);
	}

	@Override
	public ChangeLogParser createChangeLogParser() {
		return new ChangeLog();
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Nonnull
	@Override
	public String getKey() {
		return new StringBuilder("repo")
			.append(' ')
			.append(getManifestRepositoryUrl())
			.append(' ')
			.append(getManifestFile())
			.append(' ')
			.append(getManifestBranch())
			.toString();
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

		@Override
		public boolean isApplicable(final Job project) {
			return true;
		}
	}
}
