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

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.plugins.repo.ChangeLogEntry.ModifiedFile;
import hudson.scm.ChangeLogParser;
import hudson.util.AtomicFileWriter;
import hudson.util.IOException2;
import hudson.util.XStream2;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xml.sax.SAXException;

import com.thoughtworks.xstream.io.StreamException;

/**
 * Utility functions to generate and parse a file listing the differences
 * between builds. Differences are saved as a list of ChangeLogEntry.
 */
public class ChangeLog extends ChangeLogParser {

	private static Logger debug =
		Logger.getLogger("hudson.plugins.repo.ChangeLog");

	// TODO: Really need to add some unit tests for this class. That might
	// require creating git commits, which will be tricky. See the git plugin
	// for some possibilities.

	@SuppressWarnings("unchecked")
	@Override
	public RepoChangeLogSet parse(
			@SuppressWarnings("rawtypes") final AbstractBuild build,
			final File changelogFile) throws IOException, SAXException {
		final List<ChangeLogEntry> r;
		final XStream2 xs = new XStream2();
		final Reader reader =
				new BufferedReader(new InputStreamReader(new FileInputStream(
						changelogFile), "UTF-8"));
		try {
			final Object obj = xs.fromXML(reader);
			r = (List<ChangeLogEntry>) obj;
		} finally {
			reader.close();
		}

		return new RepoChangeLogSet(build, r);
	}

	/**
	 * Generate a change log between two specified revision states and return it
	 * as a list of change log entries.
	 *
	 * @param currentState
	 *            The current state of the repository
	 * @param previousState
	 *            The previous state of the repository
	 * @param launcher
	 *            The launcher used to run command-line programs
	 * @param workspace
	 *            The FilePath of the workspace to use when computing
	 *            differences. This path might be on a slave machine.
	 * @throws IOException
	 *             is thrown if we have problems writing to the changelogFile
	 * @throws InterruptedException
	 *             is thrown if we are interrupted while waiting on the git
	 *             commands to run in a forked process.
	 */
	public static List<ChangeLogEntry> generateChangeLog(
			final RevisionState currentState,
			final RevisionState previousState, final Launcher launcher,
			final FilePath workspace) throws IOException,
			InterruptedException {
		final List<ProjectState> changes =
				currentState.whatChanged(previousState);

		debug.log(Level.FINEST, "generateChangeLog: changes " + changes);
		if (changes == null || changes.size() == 0) {
			// No changes or the first job
			return null;
		}
		final List<String> commands = new ArrayList<String>(5);
		final List<ChangeLogEntry> logs = new ArrayList<ChangeLogEntry>();


		for (final ProjectState change : changes) {
			debug.log(Level.FINEST, "change: " + change);
			if (change.getRevision() == null) {
				// This project was just added to the manifest.
				logs.add(new ChangeLogEntry(change.getPath(), change
						.getServerPath(), null, null, null, null, null, null,
						null, "This project was added to the manifest.", null));
				continue;
			}
			String newRevision = currentState.getRevision(change.getPath());
			if (newRevision == null) {
				// This project was just removed from the manifest.
				logs.add(new ChangeLogEntry(change.getPath(), change
						.getServerPath(), null, null, null, null, null, null,
						null, "This project was removed from the manifest.",
						null));
				continue;
			}
			final FilePath gitdir = new FilePath(workspace, change.getPath());
			commands.clear();
			commands.add("git");
			commands.add("log");
			commands.add("--raw");
			commands.add("--first-parent");
			commands.add("--format=\"zzREPOzz%H%n%an<%ae>%aD"
					+ "%n%cn<%ce>%cD%n%s%n%n%byyREPOyy\"");
			// TODO: make this work with the -M flag to show copied and renamed
			// files.
			// TODO: even better, use jgit to do the diff. It would be faster,
			// more robust, etc. git was used to get this done faster, but jgit
			// is definitely preferable. Most of the code can probably be copied
			// from Gerrit.  It might be tricky with master/slave setup.
			commands.add(change.getRevision() + ".." + newRevision);
			final OutputStream gitOutput = new ByteArrayOutputStream();
			launcher.launch().stdout(gitOutput).pwd(gitdir).cmds(commands)
					.join();
			final String[] changelogs =
					gitOutput.toString().split("zzREPOzz");
			for (final String changelog : changelogs) {
				if (changelog.length() < 10) {
					// This isn't a helpful message. Skip it.
					continue;
				}
				int endLine = changelog.indexOf('\n');
				final String revision = changelog.substring(0, endLine);
				int firstEmailPos = changelog.indexOf('<', endLine);
				final String authorName =
						changelog.substring(endLine + 1, firstEmailPos);
				int endEmail = changelog.indexOf('>', firstEmailPos);
				final String authorEmail =
						changelog.substring(firstEmailPos + 1, endEmail);
				endLine = changelog.indexOf('\n', endEmail);
				final String authorDate =
						changelog.substring(endEmail + 1, endLine);
				firstEmailPos = changelog.indexOf('<', endLine);
				final String committerName =
						changelog.substring(endLine + 1, firstEmailPos);
				endEmail = changelog.indexOf('>', firstEmailPos);
				final String committerEmail =
						changelog.substring(firstEmailPos + 1, endEmail);
				endLine = changelog.indexOf('\n', endEmail);
				final String committerDate =
						changelog.substring(endEmail + 1, endLine);
				final int endComment = changelog.indexOf("yyREPOyy", endLine);
				final String commitText =
						changelog.substring(endLine + 1, endComment);

				final String[] fileLines =
						changelog.substring(endComment).split("\n");
				final List<ModifiedFile> modifiedFiles =
						new ArrayList<ModifiedFile>();
				for (final String fileLine : fileLines) {
					if (!fileLine.startsWith(":")) {
						continue;
					}
					final char action = fileLine.substring(37, 38).charAt(0);
					final String path = fileLine.substring(39);
					modifiedFiles.add(new ModifiedFile(path, action));
				}
				ChangeLogEntry nc = new ChangeLogEntry(change.getPath(), change
						.getServerPath(), revision, authorName, authorEmail,
						authorDate, committerName, committerEmail,
						committerDate, commitText, modifiedFiles);
				logs.add(nc);
				debug.log(Level.FINEST, nc.toString());
			}
		}
		return logs;
	}

	/**
	 * Generate a change log file containing the differences between one build
	 * and the next and save the result as XML in a specified file. The function
	 * uses git on the command line to determine the differences between
	 * commits.
	 *
	 * @param currentState
	 *            The current state of the repository
	 * @param previousState
	 *            The previous state of the repository
	 * @param changelogFile
	 *            The file in which we will store the set of differences between
	 *            the two states
	 * @param launcher
	 *            The launcher used to run command-line programs
	 * @param workspace
	 *            The FilePath of the workspace to use when computing
	 *            differences. This path might be on a slave machine.
	 * @throws IOException
	 *             is thrown if we have problems writing to the changelogFile
	 * @throws InterruptedException
	 *             is thrown if we are interrupted while waiting on the git
	 *             commands to run in a forked process.
	 */
	public static void saveChangeLog(final RevisionState currentState,
			final RevisionState previousState, final File changelogFile,
			final Launcher launcher, final FilePath workspace)
			throws IOException, InterruptedException {
		List<ChangeLogEntry> logs =
				generateChangeLog(currentState, previousState, launcher,
						workspace);

		if (logs == null) {
			debug.info("No logs found");
			return;
		}

		final XStream2 xs = new XStream2();
		final AtomicFileWriter w = new AtomicFileWriter(changelogFile);
		try {
			w.write("<?xml version='1.0' encoding='UTF-8'?>\n");
			xs.toXML(logs, w);
			w.commit();
		} catch (final StreamException e) {
			throw new IOException2(e);
		} finally {
			w.close();
		}
	}
}
