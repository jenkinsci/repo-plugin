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
import hudson.plugins.repo.behaviors.RepoScmBehavior;
import hudson.plugins.repo.behaviors.RepoScmBehaviorDescriptor;
import hudson.plugins.repo.behaviors.impl.CleanFirst;
import hudson.plugins.repo.behaviors.impl.CurrentBranch;
import hudson.plugins.repo.behaviors.impl.Depth;
import hudson.plugins.repo.behaviors.impl.DestinationDirectory;
import hudson.plugins.repo.behaviors.impl.LocalManifest;
import hudson.plugins.repo.behaviors.impl.ManifestBranch;
import hudson.plugins.repo.behaviors.impl.ManifestFile;
import hudson.plugins.repo.behaviors.impl.ManifestGroup;
import hudson.plugins.repo.behaviors.impl.ManifestPlatform;
import hudson.plugins.repo.behaviors.impl.ManifestSubmodules;
import hudson.plugins.repo.behaviors.impl.MirrorDir;
import hudson.plugins.repo.behaviors.impl.NoCloneBundle;
import hudson.plugins.repo.behaviors.impl.NoTags;
import hudson.plugins.repo.behaviors.impl.RepoBranch;
import hudson.plugins.repo.behaviors.impl.RepoUrl;
import hudson.plugins.repo.behaviors.impl.ResetFirst;
import hudson.plugins.repo.behaviors.impl.Trace;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.PollingResult.Change;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
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

/**
 * The main entrypoint of the plugin. This class contains code to store user
 * configuration and to check out the code using a repo binary.
 */

@ExportedBean
public class RepoScm extends SCM implements Serializable {

	private static Logger debug = Logger.getLogger(RepoScm.class.getName());

	private final String manifestRepositoryUrl;

	// Advanced Fields:

	@CheckForNull private int jobs;






	@CheckForNull private boolean quiet;
	@CheckForNull private boolean forceSync;

	@CheckForNull private boolean showAllChanges;


	@CheckForNull private boolean fetchSubmodules;
	@CheckForNull private Set<String> ignoreProjects;
	@CheckForNull private EnvVars extraEnvVars;


	private List<RepoScmBehavior<?>> behaviors;

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
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (behavior instanceof ManifestBranch) {
				return ((ManifestBranch) behavior).getManifestBranch();
			}
		}
		return null;
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
	@Exported @CheckForNull @Deprecated
	public String getManifestFile() {
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (behavior instanceof ManifestFile) {
				return ((ManifestFile) behavior).getManifestFile();
			}
		}
		return null;
	}

	/**
	 * Returns the group of projects to fetch. By default, this is null and
	 * repo will fetch the default group.
	 *
	 * @deprecated see {@link ManifestGroup}
	 */
	@Exported @CheckForNull @Deprecated
	public String getManifestGroup() {
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (behavior instanceof ManifestGroup) {
				return ((ManifestGroup) behavior).getManifestGroup();
			}
		}
		return null;
	}

	/**
	 * Returns the platform of projects to fetch. By default, this is null and
	 * repo will automatically fetch the appropriate platform.
	 *
	 * @deprecated see {@link ManifestPlatform}.
	 */
	@CheckForNull @Deprecated
	public String getManifestPlatform() {
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (behavior instanceof ManifestPlatform) {
				return ((ManifestPlatform) behavior).getManifestPlatform();
			}
		}
		return null;
	}

	/**
	 * Returns the repo url. by default, this is null and
	 * repo is fetched from aosp
	 *
	 * @deprecated see {@link RepoUrl} and {@link #getBehaviors()}
	 */
	@Exported @Deprecated @CheckForNull
	public String getRepoUrl() {
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (behavior instanceof RepoUrl) {
				return ((RepoUrl) behavior).getRepoUrl();
			}
		}
		return null;
	}

	/**
	 * Returns the repo branch. by default, this is null and
	 * repo is used from the default branch
	 *
	 * @deprecated see {@link RepoBranch}.
	 */
	@Exported @Deprecated
	public String getRepoBranch() {
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (behavior instanceof RepoBranch) {
				return ((RepoBranch) behavior).getRepoBranch();
			}
		}
		return null;
	}

	/**
	 * Returns the name of the mirror directory. By default, this is null and
	 * repo does not use a mirror.
	 * @deprecated see {@link MirrorDir} and {@link #setBehaviors(List)}.
	 */
	@Exported @Deprecated
	public String getMirrorDir() {
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (behavior instanceof MirrorDir) {
				return ((MirrorDir) behavior).getMirrorDir();
			}
		}
		return null;
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
	 *
	 * @deprecated see {@link Depth}
	 */
	@Exported @Deprecated
	public int getDepth() {
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (behavior instanceof Depth) {
				return ((Depth) behavior).getDepth();
			}
		}
		return 0;
	}
	/**
	 * Returns the contents of the local_manifests/local.xml. By default, this is null
	 * and a local_manifests/local.xml is neither created nor modified.
	 *
	 * @deprecated see {@link LocalManifest}
	 */
	@Exported @Deprecated
	public String getLocalManifest() {
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (behavior instanceof LocalManifest) {
				return ((LocalManifest) behavior).getLocalManifest();
			}
		}
		return null;
	}

	/**
	 * Returns the destination directory. By default, this is null and the
	 * source is synced to the root of the workspace.
	 */
	@Deprecated
	@CheckForNull
	@Exported
	public String getDestinationDir() {
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (behavior instanceof DestinationDirectory) {
				return ((DestinationDirectory) behavior).getDestinationDir();
			}
		}
		return null;
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
	 *
	 * @deprecated see {@link CurrentBranch}.
	 */
	@Exported @Deprecated
	public boolean isCurrentBranch() {
		return behaviors.stream().anyMatch(CurrentBranch.class::isInstance);
	}
	/**
	 * Returns the value of resetFirst.
	 *
	 * @deprecated see {@link hudson.plugins.repo.behaviors.impl.ResetFirst}
	 */
	@Exported @Deprecated
	public boolean isResetFirst() {
		return behaviors.stream().anyMatch(ResetFirst.class::isInstance);
	}

	/**
	 * Returns the value of cleanFirst.
	 *
	 * @deprecated see {@link CleanFirst}
	 */
	@Exported @Deprecated
	public boolean isCleanFirst() {
		return behaviors.stream().anyMatch(CleanFirst.class::isInstance);
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
	 * @deprecated see {@link Trace}
	 */
	@Exported @Deprecated
	public boolean isTrace() {
		return behaviors.stream().anyMatch(Trace.class::isInstance);
	}

	/**
	 * Returns the value of noTags.
	 *
	 * @deprecated see {@link NoTags}.
	 */
	@Exported @Deprecated
	public boolean isNoTags() {
		return behaviors.stream().anyMatch(NoTags.class::isInstance);
	}
	/**
	 * Returns the value of noCloneBundle.
	 *
	 * @deprecated see {@link NoCloneBundle}.
	 */
	@Exported @Deprecated
	public boolean isNoCloneBundle() {
		return behaviors.stream().anyMatch(NoCloneBundle.class::isInstance);
	}

	/**
	 * Returns the value of manifestSubmodules.
	 *
	 * @deprecated see {@link ManifestSubmodules}.
	 */
	@Exported @Deprecated
	public boolean isManifestSubmodules() {
		return behaviors.stream().anyMatch(ManifestSubmodules.class::isInstance);
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
	 * The behaviours.
	 * @return The behaviours.
	 */
	@Exported
	public List<RepoScmBehavior<?>> getBehaviors() {
		return behaviors;
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

	}

	/**
	 * The constructor takes in user parameters and sets them. Each job using
	 * the RepoSCM will call this constructor.
	 *
	 * @param manifestRepositoryUrl The URL for the manifest repository.
	 */
	@DataBoundConstructor //TODO
	public RepoScm(final String manifestRepositoryUrl) {
		this.manifestRepositoryUrl = manifestRepositoryUrl;
		jobs = 0;
		quiet = false;
		forceSync = false;
		showAllChanges = false;
		fetchSubmodules = false;
		ignoreProjects = Collections.<String>emptySet();
		behaviors = new ArrayList<>();
		//behaviors.add(new CurrentBranch());
	}

	/**
	 * Behaviours.
	 *
	 * @param behaviors the extra command line options to set.
	 */
	@DataBoundSetter
	public void setBehaviors(final List<RepoScmBehavior<?>> behaviors) {
		this.behaviors = behaviors;
		this.behaviors.sort(RepoScmBehaviorDescriptor.EXTENSION_COMPARATOR);
	}

	/**
	 * Adds a unique behaviour to the list and sorts it.
	 * @param behavior The behaviour to add.
	 *                    If an existing behaviour of the same class is already present it will first be removed.
	 */
	void addBehavior(final RepoScmBehavior<?> behavior) {
		behaviors.removeIf(b -> behavior.getClass().isInstance(b));
		this.behaviors.add(behavior);
		this.behaviors.sort(RepoScmBehaviorDescriptor.EXTENSION_COMPARATOR);
	}

	/**
	 * Set the manifest branch name.
	 *
	 * @param manifestBranch
	 *        The branch of the manifest repository. Typically this is null
	 *        or the empty string, which will cause repo to default to
	 *        "master".
	 * @deprecated see {@link ManifestBranch} and {@link #setBehaviors(List)}
     */
	@DataBoundSetter @Deprecated
	public void setManifestBranch(@CheckForNull final String manifestBranch) {
		behaviors.removeIf(ManifestBranch.class::isInstance);
		String mb = Util.fixEmptyAndTrim(manifestBranch);
		if (mb != null) {
			addBehavior(new ManifestBranch(mb));
		}
	}

	/**
	 * Set the initial manifest file name.
	 *
	 * @param manifestFile
	 *        The file to use as the repository manifest. Typically this is
	 *        null which will cause repo to use the default of "default.xml"
	 * @deprecated see {@link ManifestFile} and {@link #setBehaviors(List)}
     */
	@DataBoundSetter @Deprecated
	public void setManifestFile(@CheckForNull final String manifestFile) {
		behaviors.removeIf(ManifestFile.class::isInstance);
		String mf = Util.fixEmptyAndTrim(manifestFile);
		if (mf != null) {
			addBehavior(new ManifestFile(mf));
		}
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
		behaviors.removeIf(ManifestGroup.class::isInstance);
		String mg = Util.fixEmptyAndTrim(manifestGroup);
		if (mg != null) {
			addBehavior(new ManifestGroup(mg));
		}
	}

	/**
	 * Set the platform of projects to fetch.
	 *
	 * @param manifestPlatform
	 *        The platform for the projects that need to be fetched.
	 *        Typically, this is null and only projects for the current platform
	 *        will be fetched.
	 *
	 * @deprecated see {@link ManifestPlatform}.
	 */
	@DataBoundSetter @Deprecated
	public void setManifestPlatform(@CheckForNull final String manifestPlatform) {
		behaviors.removeIf(ManifestPlatform.class::isInstance);
		String mp = Util.fixEmptyAndTrim(manifestPlatform);
		if (mp != null) {
			addBehavior(new ManifestPlatform(mp));
		}
	}

	/**
	 * Set the name of the mirror directory.
	 *
	 * @param mirrorDir
	 *        The path of the mirror directory to reference when
	 *        initializing repo.
	 * @deprecated see {@link MirrorDir} and {@link #setBehaviors(List)}.
     */
	@DataBoundSetter @Deprecated
	public void setMirrorDir(@CheckForNull final String mirrorDir) {
		behaviors.removeIf(MirrorDir.class::isInstance);
		String md = Util.fixEmptyAndTrim(mirrorDir);
		if (md != null) {
			addBehavior(new MirrorDir(md));
		}
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
	 *
	 * @deprecated see {@link Depth}
     */
	@DataBoundSetter @Deprecated
	public void setDepth(final int depth) {
		behaviors.removeIf(Depth.class::isInstance);
		if (depth != 0) {
			addBehavior(new Depth(depth));
		}
	}

	/**
	 * Set the content of the local manifest.
	 *
	 * @param localManifest
	 *        May be null, a string containing XML, or an URL.
	 *        If XML, this string is written to .repo/local_manifests/local.xml
	 *        If an URL, the URL is fetched and the content is written
	 *        to .repo/local_manifests/local.xml
	 *
	 * @deprecated see {@link LocalManifest}
     */
	@DataBoundSetter @Deprecated
	public void setLocalManifest(@CheckForNull final String localManifest) {
		behaviors.removeIf(LocalManifest.class::isInstance);
		String lm = Util.fixEmptyAndTrim(localManifest);
		if (lm != null) {
			addBehavior(new LocalManifest(lm));
		}
	}

	/**
	 * Set the destination directory.
	 *
	 * @param destinationDir
	 *        If not null then the source is synced to the destinationDir
	 *        subdirectory of the workspace.
	 * @deprecated see {@link DestinationDirectory}
     */
	@DataBoundSetter
	@Deprecated
	public void setDestinationDir(@CheckForNull final String destinationDir) {
		behaviors.removeIf(DestinationDirectory.class::isInstance);
		String d = Util.fixEmptyAndTrim(destinationDir);
		if (d != null) {
			addBehavior(new DestinationDirectory(destinationDir));
		}
	}

	/**
	 * Set currentBranch.
	 *
	 * @param currentBranch
	 * 		  If this value is true, add the "-c" option when executing
	 *        "repo sync".
	 *
	 * @deprecated see {@link CurrentBranch}
     */
	@DataBoundSetter @Deprecated
	public void setCurrentBranch(final boolean currentBranch) {
		behaviors.removeIf(CurrentBranch.class::isInstance);
		if (currentBranch) {
			addBehavior(new CurrentBranch());
		}
	}

	/**
	 * Set resetFirst.
	 *
	 * @param resetFirst
	 *        If this value is true, do "repo forall -c 'git reset --hard'"
	 *        before syncing.
	 * @deprecated see {@link ResetFirst}.
     */
	@DataBoundSetter @Deprecated
	public void setResetFirst(final boolean resetFirst) {
		behaviors.removeIf(ResetFirst.class::isInstance);
		if (resetFirst) {
			addBehavior(new ResetFirst());
		}
	}

	/**
	 * Set cleanFirst.
	 *
	 * @param cleanFirst
	 *        If this value is true, do "repo forall -c 'git clean -fdx'"
	 *        before syncing.
	 *
	 * @deprecated see {@link CleanFirst}
     */
	@DataBoundSetter @Deprecated
	public void setCleanFirst(final boolean cleanFirst) {
		behaviors.removeIf(CleanFirst.class::isInstance);
		if (cleanFirst) {
			addBehavior(new CleanFirst());
		}
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
	 * @deprecated see {@link Trace} and {@link #setBehaviors(List)}.
     */

	@DataBoundSetter @Deprecated
	public void setTrace(final boolean trace) {
		behaviors.removeIf(Trace.class::isInstance);
		if (trace) {
			addBehavior(new Trace());
		}
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
	 *
	 * @deprecated see {@link NoCloneBundle}
     */
	@DataBoundSetter @Deprecated
	public void setNoCloneBundle(final boolean noCloneBundle) {
		behaviors.removeIf(NoCloneBundle.class::isInstance);
		if (noCloneBundle) {
			addBehavior(new NoCloneBundle());
		}
	}

	/**
	 * Set the repo url.
	 *
	 * @param repoUrl
	 *        If not null then use this url as repo base,
	 *        instead of the default
	 *
	 * @deprecated see {@link RepoUrl}
     */
	@DataBoundSetter @Deprecated
	public void setRepoUrl(@CheckForNull final String repoUrl) {
		behaviors.removeIf(RepoUrl.class::isInstance);
		String ru = Util.fixEmptyAndTrim(repoUrl);
		if (ru != null) {
			addBehavior(new RepoUrl(ru));
		}
	}

	/**
	 * Set the repo branch.
	 *
	 * @param repoBranch
	 *        If not null then use this as branch for repo itself
	 *        instead of the default.
	 *
	 * @deprecated see {@link RepoBranch}
	 */
	@DataBoundSetter @Deprecated
	public void setRepoBranch(@CheckForNull final String repoBranch) {
		behaviors.removeIf(RepoBranch.class::isInstance);
		String rb = Util.fixEmptyAndTrim(repoBranch);
		if (rb != null) {
			addBehavior(new RepoBranch(rb));
		}
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
	 * @deprecated see {@link NoTags}.
	 */
	@DataBoundSetter @Deprecated
	public final void setNoTags(final boolean noTags) {
		behaviors.removeIf(NoTags.class::isInstance);
		if (noTags) {
			addBehavior(new NoTags());
		}
	}

	/**
	 * Set manifestSubmodules.
	 *
	 * @param manifestSubmodules
	 *            If this value is true, add the "--submodules" option when
	 *            executing "repo init".
	 * @deprecated see {@link ManifestSubmodules}
	 */
	@DataBoundSetter @Deprecated
	public void setManifestSubmodules(final boolean manifestSubmodules) {
		behaviors.removeIf(ManifestSubmodules.class::isInstance);
		if (manifestSubmodules) {
			addBehavior(new ManifestSubmodules());
		}
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
		//TODO manifestBranch
		final String expandedManifestBranch = env.expand(getManifestBranch());
		final Run<?, ?> lastRun = job.getLastBuild();

		if (myBaseline == SCMRevisionState.NONE) {
			// Probably the first build, or possibly an aborted build.
			myBaseline = getLastState(lastRun, expandedManifestBranch);
			if (myBaseline == SCMRevisionState.NONE) {
				return PollingResult.BUILD_NOW;
			}
		}

		FilePath repoDir;
		String dstDir = getDestinationDir();
		if (dstDir != null) {
			repoDir = workspace.child(env.expand(dstDir));
		} else {
			repoDir = workspace;
		}

		if (!repoDir.isDirectory()) {
			repoDir.mkdirs();
		}

		if (!checkoutCode(launcher, repoDir, env, listener)) {
			// Some error occurred, try a build now so it gets logged.
			return new PollingResult(myBaseline, myBaseline,
					Change.INCOMPARABLE);
		}

		final RevisionState currentState = new RevisionState(
				getStaticManifest(launcher, repoDir, listener.getLogger(), env),
				getManifestRevision(launcher, repoDir, listener.getLogger(), env),
				expandedManifestBranch, listener.getLogger());

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
		String destDirTrait = getDestinationDir();
		if (StringUtils.isNotEmpty(destDirTrait)) {
			repoDir = workspace.child(env.expand(destDirTrait));
		} else {
			repoDir = workspace;
		}

		if (!repoDir.isDirectory()) {
			repoDir.mkdirs();
		}

		if (!checkoutCode(launcher, repoDir, env, listener)) {
			throw new IOException("Could not checkout");
		}
		final String manifest =
				getStaticManifest(launcher, repoDir, listener.getLogger(), env);
		final String manifestRevision =
				getManifestRevision(launcher, repoDir, listener.getLogger(), env);
		final String expandedBranch = env.expand(getManifestBranch());
		final RevisionState currentState =
				new RevisionState(manifest, manifestRevision, expandedBranch,
						listener.getLogger());
		build.addAction(currentState);

		final Run previousBuild = build.getPreviousBuild();
		final SCMRevisionState previousState =
				getLastState(previousBuild, expandedBranch);

		if (changelogFile != null) {
			ChangeLog.saveChangeLog(
					currentState,
					previousState == SCMRevisionState.NONE ? null : (RevisionState) previousState,
					changelogFile,
					launcher,
					repoDir,
					showAllChanges);
		}
		build.addAction(new ManifestAction(build));
	}

	private int doSync(final Launcher launcher, @Nonnull final FilePath workspace,
					   @Nonnull final TaskListener listener, final EnvVars env)
		throws IOException, InterruptedException {

		debug.log(Level.FINE, "Syncing out code in: " + workspace.getName());
		final String executable = getDescriptor().getExecutable();
		boolean preSyncSuccessful = true;
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (!behavior.preSync(executable, launcher, workspace, listener, env)) {
				preSyncSuccessful = false;
			}
		}
		if (!preSyncSuccessful) {
			return 2;
		}
		final List<String> commands = new ArrayList<String>(4);
		commands.add(executable);

		commands.add("sync");
		commands.add("-d");

		if (isQuiet()) {
			commands.add("-q");
		}
		if (isForceSync()) {
			commands.add("--force-sync");
		}
		if (jobs > 0) {
			commands.add("--jobs=" + jobs);
		}


		if (fetchSubmodules) {
			commands.add("--fetch-submodules");
		}
		return launcher.launch().stdout(listener.getLogger()).pwd(workspace)
                .cmds(commands).envs(env).join();
	}

	private boolean checkoutCode(final Launcher launcher,
			@Nonnull final FilePath workspace,
			final EnvVars env,
			@Nonnull final TaskListener listener)
			throws IOException, InterruptedException {
		final List<String> commands = new ArrayList<String>(4);

		debug.log(Level.INFO, "Checking out code in: {0}", workspace.getName());

		commands.add(getDescriptor().getExecutable());

		commands.add("init");
		commands.add("-u");
		commands.add(env.expand(manifestRepositoryUrl));

		boolean decorationSuccess = true;
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (!behavior.decorateInit(commands, env, listener)) {
				decorationSuccess = false;
			}
		}
		if (!decorationSuccess) {
			return false;
		}

		int returnCode =
				launcher.launch().stdout(listener.getLogger()).pwd(workspace)
						.cmds(commands).envs(env).join();
		if (returnCode != 0) {
			return false;
		}

		FilePath rdir = LocalManifest.dotRepo(workspace);
		FilePath lmdir = LocalManifest.localManifests(rdir);
		// Delete the legacy local_manifest.xml in case it exists from a previous build
		rdir.child("local_manifest.xml").delete();
		if (lmdir.exists()) {
			lmdir.deleteContents();
		} else {
			lmdir.mkdirs();
		}

		boolean postInitSuccess = true;
		for (RepoScmBehavior<?> behavior : behaviors) {
			if (!behavior.postInit(workspace, env, listener)) {
				postInitSuccess = false;
			}
		}
		if (!postInitSuccess) {
			return false;
		}

		returnCode = doSync(launcher, workspace, listener, env);
		if (returnCode != 0) {
			debug.log(Level.WARNING, "Sync failed. Resetting repository");
			commands.clear();
			commands.add(getDescriptor().getExecutable());
			commands.add("forall");
			commands.add("-c");
			commands.add("git reset --hard");
			launcher.launch().stdout(listener.getLogger()).pwd(workspace).cmds(commands)
				.envs(env).join();
			returnCode = doSync(launcher, workspace, listener, env);
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

	@Nonnull
	private SCMRevisionState getLastState(final Run<?, ?> lastBuild,
			final String expandedManifestBranch) {
		if (lastBuild == null) {
			return RevisionState.NONE;
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

	@Deprecated @CheckForNull private transient String destinationDir;
	@Deprecated @CheckForNull private transient String manifestBranch;
	@Deprecated @CheckForNull private transient String manifestFile;
	@Deprecated @CheckForNull private transient boolean trace;
	@Deprecated @CheckForNull private transient String mirrorDir;
	@Deprecated @CheckForNull private transient String repoUrl;
	@Deprecated @CheckForNull private transient String manifestGroup;
	@Deprecated @CheckForNull private transient String manifestPlatform;
	@Deprecated @CheckForNull private transient int depth;
	@Deprecated @CheckForNull private transient boolean noCloneBundle;
	@Deprecated @CheckForNull private transient boolean currentBranch;
	@Deprecated @CheckForNull private transient boolean noTags;
	@Deprecated @CheckForNull private transient boolean manifestSubmodules;
	@Deprecated @CheckForNull private transient String localManifest;
	@Deprecated @CheckForNull private transient String repoBranch;
	@Deprecated @CheckForNull private transient boolean resetFirst;
	@Deprecated @CheckForNull private transient boolean cleanFirst;

	/**
	 * Converts old data to new behaviour format.
	 * @return the modified object if any
	 */
	public Object readResolve() {
		if (behaviors == null) {
			List<RepoScmBehavior<?>> b = new ArrayList<>();
			if (StringUtils.isNotEmpty(destinationDir)) {
				b.add(new DestinationDirectory(destinationDir));
			}
			if (trace) {
				b.add(new Trace());
			}
			if (StringUtils.isNotEmpty(manifestBranch)) {
				b.add(new ManifestBranch(manifestBranch));
			}
			if (StringUtils.isNotEmpty(manifestFile)) {
				b.add(new ManifestFile(manifestFile));
			}
			if (StringUtils.isNotEmpty(mirrorDir)) {
				b.add(new MirrorDir(mirrorDir));
			}
			if (StringUtils.isNotEmpty(repoUrl)) {
				b.add(new RepoUrl(repoUrl));
			}
			if (StringUtils.isNotEmpty(manifestGroup)) {
				b.add(new ManifestGroup(manifestGroup));
			}
			if (StringUtils.isNotEmpty(manifestPlatform)) {
				b.add(new ManifestPlatform(manifestPlatform));
			}
			if (depth != 0) {
				b.add(new Depth(depth));
			}
			if (noCloneBundle) {
				b.add(new NoCloneBundle());
			}
			if (currentBranch) {
				b.add(new CurrentBranch());
			}
			if (noTags) {
				b.add(new NoTags());
			}
			if (manifestSubmodules) {
				b.add(new ManifestSubmodules());
			}
			if (StringUtils.isNotEmpty(localManifest)) {
				b.add(new LocalManifest(localManifest));
			}
			if (StringUtils.isNotEmpty(repoBranch)) {
				b.add(new RepoBranch(repoBranch));
			}
			if (resetFirst) {
				b.add(new ResetFirst());
			}
			if (cleanFirst) {
				b.add(new CleanFirst());
			}

			b.sort(RepoScmBehaviorDescriptor.EXTENSION_COMPARATOR);
			this.behaviors = b;
		}

		return this;
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

		/**
		 * Gets the behaviours from the instance or, if null, a list of default behaviours.
		 *
		 * @param instance the instance to get the behaviours from.
		 * @return a list of behaviours
		 * @see RepoScm#getBehaviors()
		 * @see RepoScmBehaviorDescriptor#defaultOrNull()
		 */
		public List<RepoScmBehavior<?>> getBehavioursOrDefaults(final RepoScm instance) {
			if (instance != null) {
				return instance.getBehaviors();
			}

			List<RepoScmBehavior<?>> defaults = new ArrayList<>();
			for (RepoScmBehaviorDescriptor<?> rd : RepoScmBehaviorDescriptor.all()) {
				RepoScmBehavior<?> behavior = rd.defaultOrNull();
				if (behavior != null) {
					defaults.add(behavior);
				}
			}
			return defaults;
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

		/**
		 * Lists all extensions of {@link RepoScmBehaviorDescriptor}.
		 *
		 * @return the descriptors
		 */
		public List<RepoScmBehaviorDescriptor> getBehaviourDescriptors() {
			return RepoScmBehaviorDescriptor.all();
		}

		@Override
		public boolean isApplicable(final Job project) {
			return true;
		}
	}
}
