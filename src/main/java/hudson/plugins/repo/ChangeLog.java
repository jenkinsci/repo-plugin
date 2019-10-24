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
import hudson.model.Run;
import hudson.plugins.repo.ChangeLogEntry.ModifiedFile;
import hudson.scm.ChangeLogParser;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.util.AtomicFileWriter;
import hudson.util.XStream2;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xml.sax.SAXException;

import com.thoughtworks.xstream.io.StreamException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility functions to generate and parse a file listing the differences
 * between builds. Differences are saved as a list of ChangeLogEntry.
 */
class ChangeLog extends ChangeLogParser {

	private static Logger debug =
		Logger.getLogger("hudson.plugins.repo.ChangeLog");

	// TODO: Really need to add some unit tests for this class. That might
	// require creating git commits, which will be tricky. See the git plugin
	// for some possibilities.

	@Override
	@SuppressWarnings("unchecked")
	public RepoChangeLogSet parse(
			final Run build, final RepositoryBrowser<?> browser, final File changelogFile)
			throws IOException, SAXException {
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

		return new RepoChangeLogSet(build, browser, r);
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
	 * @param showAllChanges
	 *            Add --first-parent to "git log"
	 * @throws IOException
	 *             is thrown if we have problems writing to the changelogFile
	 * @throws InterruptedException
	 *             is thrown if we are interrupted while waiting on the git
	 *             commands to run in a forked process.
	 */
	private static List<ChangeLogEntry> generateChangeLog(
			@Nonnull final RevisionState currentState,
			@Nullable final RevisionState previousState, final Launcher launcher,
			final FilePath workspace, final boolean showAllChanges)
			throws IOException,
			InterruptedException {
		final List<ProjectState> changes =
				currentState.whatChanged(previousState);
		final List<ChangeLogEntry> logs = new ArrayList<ChangeLogEntry>();

		debug.log(Level.FINEST, "generateChangeLog: changes " + changes);
		if (changes == null || changes.size() == 0) {
			// No changes or the first job
			debug.log(Level.FINEST, "No changes, or first job, returning empty log");
			return logs;
		}
		final List<String> commands = new ArrayList<String>(5);


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
			if (!showAllChanges) {
				commands.add("--first-parent");
			}

            final String format = "[[<as7d9m1R_MARK_A>]]"
                                + "%H[[<as7d9m1R_MARK_B>]"
                                + "%an[[<as7d9m1R_MARK_B>]"
                                + "%ae[[<as7d9m1R_MARK_B>]"
                                + "%aD[[<as7d9m1R_MARK_B>]"
                                + "%cn[[<as7d9m1R_MARK_B>]"
                                + "%ce[[<as7d9m1R_MARK_B>]"
                                + "%cD[[<as7d9m1R_MARK_B>]"
                                + "%s\n%b[[<as7d9m1R_MARK_B>]";


			commands.add("--format=\"" + format + "\"");
			// TODO: make this work with the -M flag to show copied and renamed
			// files.
			// TODO: even better, use jgit to do the diff. It would be faster,
			// more robust, etc. git was used to get this done faster, but jgit
			// is definitely preferable. Most of the code can probably be copied
			// from Gerrit.  It might be tricky with master/slave setup.
			commands.add(change.getRevision() + ".." + newRevision);
			final ByteArrayOutputStream gitOutput = new ByteArrayOutputStream();
			launcher.launch().stdout(gitOutput).pwd(gitdir).cmds(commands)
					.join();
            final String o = new String(gitOutput.toByteArray(), Charset.defaultCharset());
			final String[] changelogs = o.split(
                            "\\[\\[<as7d9m1R_MARK_A>\\]\\]");
            debug.log(Level.INFO, o);
			for (final String changelog : changelogs) {
                final String[] parts = changelog.split(
                        "\\[\\[<as7d9m1R_MARK_B>\\]");
				if (parts.length  < 9) {
                    // this is broken
					continue;
				}
				final String revision       = parts[0];
				final String authorName     = parts[1];
				final String authorEmail    = parts[2];
				final String authorDate     = parts[3];
				final String committerName  = parts[4];
				final String committerEmail = parts[5];
				final String committerDate  = parts[6];
				final String commitText     = parts[7];
				final String[] fileLines    = parts[8].split("\n");

				final List<ModifiedFile> modifiedFiles =
						new ArrayList<ModifiedFile>();
				for (final String fileLine : fileLines) {
					// Format of these lines is described in the "RAW OUTPUT
					// FORMAT" section of "git diff --help"...
					//
					// An output line is formatted this way:
					//     in-place edit  :100644 100644 bcd1234... 0123456... M file0
					//     copy-edit      :100644 100644 abcd123... 1234567... C68 file1 file2
					//     rename-edit    :100644 100644 abcd123... 1234567... R86 file1 file3
					//     create         :000000 100644 0000000... 1234567... A file4
					//     delete         :100644 000000 1234567... 0000000... D file5
					//     unmerged       :000000 000000 0000000... 0000000... U file6
					//
					// Note that the filenames are preceded by tabs rather than spaces.

					if (!fileLine.startsWith(":")) {
						continue;
					}

					final String[] spaceParts = fileLine.split(" ", 5);
					if (spaceParts.length != 5) {
						continue;
					}

					final String[] tabParts = spaceParts[4].split("\t");
					if (tabParts[0].isEmpty()) {
						continue;
					}
					final char action = tabParts[0].charAt(0);
					final int expectedLen = ((action == 'C') || (action == 'R')) ? 3 : 2;
					if (tabParts.length != expectedLen) {
						continue;
					}

					switch (action) {
						case 'M':
							modifiedFiles.add(new ModifiedFile(tabParts[1], EditType.EDIT));
							break;
						case 'C':
							modifiedFiles.add(new ModifiedFile(tabParts[2], EditType.ADD));
							break;
						case 'R':
							modifiedFiles.add(new ModifiedFile(tabParts[1], EditType.DELETE));
							modifiedFiles.add(new ModifiedFile(tabParts[2], EditType.ADD));
							break;
						case 'A':
							modifiedFiles.add(new ModifiedFile(tabParts[1], EditType.ADD));
							break;
						case 'D':
							modifiedFiles.add(new ModifiedFile(tabParts[1], EditType.DELETE));
							break;
						default:
							continue;
					}
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
	 * @param showAllChanges
	 *            Add --first-parent to "git log"
	 * @throws IOException
	 *             is thrown if we have problems writing to the changelogFile
	 * @throws InterruptedException
	 *             is thrown if we are interrupted while waiting on the git
	 *             commands to run in a forked process.
	 */
	static void saveChangeLog(@Nonnull final RevisionState currentState,
							  @Nullable final RevisionState previousState, final File changelogFile,
							  final Launcher launcher, final FilePath workspace,
							  final boolean showAllChanges)
			throws IOException, InterruptedException {
		List<ChangeLogEntry> logs =
				generateChangeLog(currentState, previousState, launcher,
						workspace, showAllChanges);

		final XStream2 xs = new XStream2();
		final AtomicFileWriter w = new AtomicFileWriter(changelogFile);
		try {
			w.write("<?xml version='1.0' encoding='UTF-8'?>\n");
			xs.toXML(logs, w);
			w.commit();
		} catch (final StreamException e) {
			throw new IOException("Could not save changelog", e);
		} finally {
			w.close();
		}
	}
}
