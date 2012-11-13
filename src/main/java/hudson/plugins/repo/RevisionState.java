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

import hudson.Util;
import hudson.scm.SCMRevisionState;

import java.io.PrintStream;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * A RevisionState records the state of the repository for a particular build.
 * It is used to see what changed from build to build.
 */
@SuppressWarnings("serial")
public class RevisionState extends SCMRevisionState implements Serializable {

	private final String manifest;
	private final Map<String, ProjectState> projects =
			new TreeMap<String, ProjectState>();
	private final String branch;

	private static Logger debug =
		Logger.getLogger("hudson.plugins.repo.RevisionState");

	/**
	 * Creates a new RepoRevisionState.
	 *
	 * @param manifest
	 *            A string representation of the static manifest XML file
	 * @param branch
	 *            The branch of the manifest project
	 * @param logger
	 *            A PrintStream for logging errors
	 */
	public RevisionState(final String manifest, final String branch,
			final PrintStream logger) {
		this.manifest = manifest;
		this.branch = branch;
		try {
			final InputSource xmlSource = new InputSource();
			xmlSource.setCharacterStream(new StringReader(manifest));
			final Document doc =
					DocumentBuilderFactory.newInstance().newDocumentBuilder()
							.parse(xmlSource);

			if (!doc.getDocumentElement().getNodeName().equals("manifest")) {
				logger.println("Error - malformed manifest");
				return;
			}
			final NodeList projectNodes = doc.getElementsByTagName("project");
			final int numProjects = projectNodes.getLength();
			for (int i = 0; i < numProjects; i++) {
				final Element projectElement = (Element) projectNodes.item(i);
				String path =
						Util.fixEmptyAndTrim(projectElement
								.getAttribute("path"));
				final String serverPath =
						Util.fixEmptyAndTrim(projectElement
								.getAttribute("name"));
				final String revision =
						Util.fixEmptyAndTrim(projectElement
								.getAttribute("revision"));
				if (path == null) {
					// 'repo manifest -o' doesn't output a path if it is the
					// same as the server path, even if the path is specified.
					path = serverPath;
				}
				if (path != null && serverPath != null && revision != null) {
					projects.put(path, ProjectState.constructCachedInstance(
							path, serverPath, revision));
					if (logger != null) {
						logger.println("Added a project: " + path
								+ " at revision: " + revision);
					}
				}
			}
		} catch (final Exception e) {
			logger.println(e);
			return;
		}
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof RevisionState) {
			final RevisionState other = (RevisionState) obj;
			if (branch == null) {
				if (other.branch != null) {
					return false;
				}
				return projects.equals(other.projects);
			}
			return branch.equals(other.branch)
					&& projects.equals(other.projects);
		}
		return super.equals(obj);
	}

	@Override
	public int hashCode() {
		return (branch != null ? branch.hashCode() : 0)
			^ (manifest != null ? manifest.hashCode() : 0)
			^ projects.hashCode();
	}

	/**
	 * Returns the manifest repository's branch name when this state was
	 * created.
	 */
	public String getBranch() {
		return branch;
	}

	/**
	 * Returns the static XML manifest for this repository state in String form.
	 */
	public String getManifest() {
		return manifest;
	}

	/**
	 * Returns the revision for the repository at the specified path.
	 *
	 * @param path
	 *            The path to the repository in which we are interested.
	 * @return the SHA1 revision of the repository.
	 */
	public String getRevision(final String path) {
		ProjectState project = projects.get(path);
		return project == null ? null : project.getRevision();
	}

	/**
	 * Calculate what has changed from a specified previous repository state.
	 *
	 * @param previousState
	 *            The previous repository state in which we are interested
	 * @return A List of ProjectStates from the previous repo state which have
	 *         since been updated.
	 */
	public List<ProjectState> whatChanged(final RevisionState previousState) {
		final List<ProjectState> changes = new ArrayList<ProjectState>();
		if (previousState == null) {
			// Everything is new. The change log would include every change,
			// which might be a little unwieldy (and take forever to
			// generate/parse). Instead, we will return null (no changes)
			debug.log(Level.FINE, "Everything is new");
			return null;
		}
		final Set<String> keys = projects.keySet();
		HashMap<String, ProjectState> previousStateCopy =
				new HashMap<String, ProjectState>(previousState.projects);
		for (final String key : keys) {
			final ProjectState status = previousStateCopy.get(key);
			if (status == null) {
				// This is a new project, just added to the manifest.
				final ProjectState newProject = projects.get(key);
				debug.log(Level.FINE, "New project: " + key);
				changes.add(ProjectState.constructCachedInstance(
						newProject.getPath(), newProject.getServerPath(),
						null));
			} else if (!status.equals(projects.get(key))) {
				changes.add(previousStateCopy.get(key));
			}
			previousStateCopy.remove(key);
		}
		changes.addAll(previousStateCopy.values());
		return changes;
	}
}
