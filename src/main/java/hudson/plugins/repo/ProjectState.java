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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A ProjectState object represents the state of a project. This is used to see
 * when projects have changed. A repo manifest contains a list of projects, and
 * a build in Hudson has a list of ProjectStates.
 */
public class ProjectState {

	private final String path;
	private final String serverPath;
	private final String revision;

	private static Logger debug =
		Logger.getLogger("hudson.plugins.repo.ProjectState");

	/**
	 * Create an object representing the state of a project.
	 *
	 * @param path
	 *            The client-side path of the project
	 * @param serverPath
	 *            The server-side path of the project
	 * @param revision
	 *            The SHA-1 revision of the project
	 */
	public ProjectState(final String path, final String serverPath,
			final String revision) {
		this.path = path;
		this.serverPath = serverPath;
		this.revision = revision;

		debug.log(Level.FINE, "path: " + path + " serverPath: " + serverPath
				+ " revision: " + revision);
	}

	/**
	 * Gets the client-side path of the project.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Gets the server-side path of the project.
	 */
	public String getServerPath() {
		return serverPath;
	}

	/**
	 * Gets the revision (SHA-1) of the project.
	 */
	public String getRevision() {
		return revision;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ProjectState)) {
			return false;
		}
		final ProjectState other = (ProjectState) obj;
		return (path == null ? other.path == null : path.equals(other.path))
				&& (serverPath == null ? other.serverPath == null
						: serverPath.equals(other.serverPath))
				&& (revision == null ? other.revision == null : revision
						.equals(other.revision));
	}

	@Override
	public int hashCode() {
		return 23 + (path == null ? 37 : path.hashCode())
				+ (serverPath == null ? 97 : serverPath.hashCode())
				+ (revision == null ? 389 : revision.hashCode());
	}
}
