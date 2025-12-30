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

import hudson.model.BuildBadgeAction;
import hudson.model.Run;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A Manifest Action displays the static manifest information needed
 * to recreate the exact state of the repository when the build was run.
 */
@ExportedBean(defaultVisibility = 999)
public class ManifestAction implements RunAction2, Serializable, BuildBadgeAction {
	private static Logger debug = Logger
		.getLogger("hudson.plugins.repo.ManifestAction");
	private static final long serialVersionUID = 1;

	private transient Run<?, ?> run;

	/**
	 * Constructs the manifest action object.
	 * @param run Build whose manifest we wish to display.
	 */
	ManifestAction(final Run<?, ?> run) {
		this.run = run;
	}

	@Override
	public void onAttached(final Run<?, ?> r) {
		this.run = r;
	}

	@Override
	public void onLoad(final Run<?, ?> r) {
		this.run = r;
	}

	/**
	 * Getter for the run property.
	 */
	public Run<?, ?> getRun() {
		return run;
	}

	/**
	 * Returns the filename to use as the badge.
	 */
	public String getIconFileName() {
		return "star";
	}

	/**
	 * Returns the display name to use for the action.
	 */
	public String getDisplayName() {
		return "Repo Manifest ’" + getFile() + "’";
	}

	/**
	 * Returns the name of the Url to use for the action.
	 */
	public final String getUrlName() {
		return "repo-manifest";
	}

	/**
	 * Gets a String representation of the static manifest for this repo snapshot.
	 */
	public List<StaticManifest> getManifests() {
		List<StaticManifest> manifests = new ArrayList<StaticManifest>();

		final List<RevisionState> revisionStates = getRun().getActions(RevisionState.class);
		if (revisionStates != null) {
			for (RevisionState revisionState : revisionStates) {
				manifests.add(new StaticManifest(revisionState.getFile(), revisionState.getBranch(),
						revisionState.getUrl(), revisionState.getManifest()));
			}
		}
		return manifests;
	}
}
