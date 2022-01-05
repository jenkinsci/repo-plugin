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

import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.export.ExportedBean;

import hudson.model.BuildBadgeAction;
import hudson.model.Run;
import jenkins.model.RunAction2;

import javax.annotation.CheckForNull;

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
	private RevisionState revisionState;

	/**
	 * Allow disambiguation of the action url when multiple {@link RevisionState} actions present.
	 */
	@CheckForNull
	private Integer index;

	/**
	 * Constructs the manifest action object.
	 * @param run Build whose manifest we wish to display.
	 */
	ManifestAction(final Run<?, ?> run) {
		this.run = run;
	}

	/**
	 * Sets an identifier used to disambiguate multiple {@link RevisionState} actions attached to a
	 * {@link Run}.
	 *
	 * @param index the index, indexes less than or equal to {@code 1} will be discarded.
	 */
	public void setIndex(final Integer index) {
		this.index = index == null || index <= 1 ? null : index;
		try {
			final int i = index == null ? 0 : index - 1;
			final List<RevisionState> revisionStates = run.getActions(RevisionState.class);
			if (revisionStates.size() > i && i >= 0) {
				revisionState = revisionStates.get(i);
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, "Error getting revision state {0}", e.getMessage());
			revisionState = null;
		}
	}

	/**
	 * Gets the identifier used to disambiguate multiple {@link RevisionState} actions attached to
	 * a {@link Run}.
	 *
	 * @return the index.
	 */
	@CheckForNull
	public Integer getIndex() {
		return index;
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
		return "star.gif";
	}

	/**
	 * Returns the display name to use for the action.
	 */
	public String getDisplayName() {
		return "Repo Manifest";
	}

	/**
	 * Returns the name of the Url to use for the action.
	 */
	public final String getUrlName() {
		return index == null ? "repo-manifest" : "repo-manifest-" + index;
	}

	/**
	 * Gets a String representation of the static manifest for this repo snapshot.
	 */
	public String getManifest() {
		return revisionState == null ? "" : revisionState.getManifest();
	}

	/**
	 * Returns the manifest repository's url for this repo snapshot.
	 */
	public String getUrl() {
		return revisionState == null ? "" : revisionState.getUrl();
	}

	/**
	 * Returns the manifest repository's branch name for this repo snapshot.
	 */
	public String getBranch() {
		return revisionState == null ? "" : revisionState.getBranch();
	}

	/**
	 * Returns the path to the manifest file for this repo snapshot.
	 */
	public String getFile() {
		return revisionState == null ? "" : revisionState.getFile();
	}
}
