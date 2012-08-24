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

import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;
import hudson.scm.ChangeLogSet.AffectedFile;

import java.util.AbstractList;
import java.util.Collection;
import java.util.List;

/**
 * A POJO containing information about a single change (git commit) in a git
 * repository. These objects are used to build the change log page.
 */
public class ChangeLogEntry extends ChangeLogSet.Entry {

	/**
	 * A POJO containing information about a modified file. A RepoChangeLogEntry
	 * contains a list of ModifiedFiles. We track the file path and how it was
	 * modified (added, edited, removed, etc).
	 */
	public static class ModifiedFile implements AffectedFile {

		/**
		 * An EditType for a Renamed file. Most version control systems don't
		 * support file renames, so this EditType isn't in the default set
		 * provided by Hudson.
		 */
		public static final EditType RENAME = new EditType("rename",
				"The file was renamed");

		private final String path;
		private final char action;

		/**
		 * Create a new ModifiedFile object with the given path and action.
		 *
		 * @param path
		 *            the path of the file
		 * @param action
		 *            the action performed on the file, as reported by Git (A
		 *            for add, D for delete, M for modified, etc)
		 */
		public ModifiedFile(final String path, final char action) {
			this.path = path;
			this.action = action;
		}

		/**
		 * Returns the path of the file.
		 */
		public String getPath() {
			return path;
		}

		/**
		 * Returns the action performed on the file.
		 */
		public char getAction() {
			return action;
		}

		/**
		 * Returns the EditType performed on the file (based on the action).
		 */
		public EditType getEditType() {
			if (action == 'A') {
				return EditType.ADD;
			} else if (action == 'D') {
				return EditType.DELETE;
			} else if (action == 'M') {
				return EditType.EDIT;
			} else if (action == 'R') {
				return RENAME;
			} else {
				return new EditType("unknown: " + action,
						"An unknown file action");
			}
		}
	}

	private final String path;
	private final String serverPath;
	private final String revision;
	private final String authorName;
	private final String authorEmail;
	private final String authorDate;
	private final String committerName;
	private final String committerEmail;
	private final String committerDate;
	private final String commitText;
	private final List<ModifiedFile> modifiedFiles;

	/**
	 * Creates a new REpoChangeLogEntry object containing all the details about
	 * a git commit.
	 *
	 * @param path
	 *            The path to the project from the client-side
	 * @param serverPath
	 *            The path to the project on the server-side
	 * @param revision
	 *            The SHA-1 revision of the project
	 * @param authorName
	 *            The name of the author of the commit
	 * @param authorEmail
	 *            The author's email address
	 * @param authorDate
	 *            The author date string
	 * @param committerName
	 *            The name of the committer
	 * @param committerEmail
	 *            The committer's email address
	 * @param committerDate
	 *            The date of the commit
	 * @param commitText
	 *            The commit message text
	 * @param modifiedFiles
	 *            A list of ModifiedFiles impacted by the commit
	 */
	// CS IGNORE ParameterNumber FOR NEXT 16 LINES. REASON: I've got no
	// better ideas. Passing in all the variables here makes sense to me, even
	// if it is ugly.
	public ChangeLogEntry(final String path, final String serverPath,
			final String revision, final String authorName,
			final String authorEmail, final String authorDate,
			final String committerName, final String committerEmail,
			final String committerDate, final String commitText,
			final List<ModifiedFile> modifiedFiles) {
		this.path = path;
		this.serverPath = serverPath;
		this.revision = revision;
		this.authorName = authorName;
		this.authorEmail = authorEmail;
		this.authorDate = authorDate;
		this.committerName = committerName;
		this.committerEmail = committerEmail;
		this.committerDate = committerDate;
		this.commitText = commitText;
		this.modifiedFiles = modifiedFiles;
	}

	/** Converts this ChangeLogEntry to a string for debugging.
	 * @return A String of change log entry information.
	 */
	@Override
	public String toString() {
		return
			"path: " + path + "\n"
			+ "serverPath: " + serverPath + "\n"
			+ "revision: " + revision + "\n"
			+ "authorName: " + authorName + "\n"
			+ "authorEmail: " + authorEmail + "\n"
			+ "authorDate: " + authorDate + "\n"
			+ "committerName: " + committerName + "\n"
			+ "committerEmail: " + committerEmail + "\n"
			+ "committerDate: " + committerDate + "\n"
			+ "commitText: " + commitText + "\n"
			+ "modifiedFiles: " + modifiedFiles;
	}

	/**
	 * Returns the client-side project path.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Returns the server-side project path.
	 */
	public String getServerPath() {
		return serverPath;
	}

	/**
	 * Returns the SHA-1 revision.
	 */
	public String getRevision() {
		return revision;
	}

	/**
	 * Returns the author's name.
	 */
	public String getAuthorName() {
		return authorName;
	}

	/**
	 * Returns the author's email address.
	 */
	public String getAuthorEmail() {
		return authorEmail;
	}

	/**
	 * Returns the date this commit was authored.
	 */
	public String getAuthorDate() {
		return authorDate;
	}

	/**
	 * Returns the committer's name.
	 */
	public String getCommitterName() {
		return committerName;
	}

	/**
	 * Returns the committer's email address.
	 */
	public String getCommitterEmail() {
		return committerEmail;
	}

	/**
	 * Returns the date this patch was committed.
	 */
	public String getCommitterDate() {
		return committerDate;
	}

	/**
	 * Returns the commit message.
	 */
	public String getCommitText() {
		return commitText;
	}

	/**
	 * Returns a list of files modified by this change.
	 */
	public List<ModifiedFile> getModifiedFiles() {
		return modifiedFiles;
	}

	/**
	 * Returns a set of paths in the workspace that was
	 * affected by this change.
	 */
	@Override
	public List<ModifiedFile> getAffectedFiles() {
		return modifiedFiles;
	}

	@Override
	public String getMsg() {
		return getCommitText();
	}

	@Override
	public User getAuthor() {
		if (authorName == null) {
			return User.getUnknown();
		}
		return User.get(authorName);
	}

	@Override
	public void setParent(
			@SuppressWarnings("rawtypes") final ChangeLogSet parent) {
		// This is needed to fix a permission issue - the base class has this
		// method protected. The base class was written assuming that we would
		// be a subclass of RepoChangeLogSet, but the code is much cleaner if we
		// split them up.
		super.setParent(parent);
	}

	@Override
	public Collection<String> getAffectedPaths() {
		return new AbstractList<String>() {
			@Override
			public String get(final int index) {
				return modifiedFiles.get(index).getPath();
			}

			@Override
			public int size() {
				return modifiedFiles.size();
			}
		};
	}
}
