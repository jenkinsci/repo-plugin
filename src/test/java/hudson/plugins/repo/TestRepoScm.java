/*
 * The MIT License
 *
 * Copyright (c) 2011, Brad Larson
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

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;

import junit.framework.TestCase;

/**
 * Test cases for the {@link RevisionState} class.
 */
public class TestRepoScm extends TestCase {


	public void testSetIgnoredProjects() {
		RepoScm scm = new RepoScm("http://manifesturl");
		scm.setIgnoreProjects("");
		assertEquals("", scm.getIgnoreProjects());

	}

	public void testSetIgnoredProjectsKeepsOrder() {
		RepoScm scm = new RepoScm("http://manifesturl");
		scm.setIgnoreProjects("projecta projectb");
		assertEquals("projecta\nprojectb", scm.getIgnoreProjects());
		scm.setIgnoreProjects("projectb projecta");
		assertEquals("projectb\nprojecta", scm.getIgnoreProjects());
	}

	public void testResetFirst() {
		RepoScm scm = new RepoScm("http://manifesturl");
		assertEquals(false, scm.isResetFirst());
		scm.setResetFirst(true);
		assertEquals(true, scm.isResetFirst());
	}

	public void testPollManifest() {
		RepoScm scm = new RepoScm("http://manifesturl");
		assertEquals(false, scm.isPollManifest());
		scm.setPollManifest(true);
		assertEquals(true, scm.isPollManifest());
	}

	public void testCleanFirst() {
		RepoScm scm = new RepoScm("http://manifesturl");
		assertEquals(false, scm.isCleanFirst());
		scm.setCleanFirst(true);
		assertEquals(true, scm.isCleanFirst());
	}

	public void testWorktree() {
		RepoScm scm = new RepoScm("http://manifesturl");
		assertEquals(false, scm.isWorktree());
		scm.setWorktree(true);
		assertEquals(true, scm.isWorktree());
	}
}
