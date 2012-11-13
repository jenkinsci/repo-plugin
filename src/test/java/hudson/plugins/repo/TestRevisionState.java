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
public class TestRevisionState extends TestCase {

	// CS IGNORE LineLength FOR NEXT 100 LINES. REASON: unit test data.
	private RevisionState stateOne;
	private RevisionState stateOneCopy;
	private RevisionState stateTwo;
	private RevisionState stateThree;

	private String manifestOne =
			"<manifest>"
					+ "<project name=\"a\" path=\"a\" revision=\"c9039e9649d133d80073e432816b9b4915776b41\"/>"
					+ "<project name=\"b\" path=\"b\" revision=\"c27d6b02c859b291878db67f256cefac3adb26df\"/>"
					+ "<project name=\"c\" path=\"c\" revision=\"fa822eff984195ec8923718cd025fd44b77a26ef\"/>"
					+ "</manifest>";
	/**
	 * manifestTwo has a new commit for projects a and c, and adds a new project
	 * d.
	 */
	private String manifestTwo =
			"<manifest>"
					+ "<project name=\"a\" path=\"a\" revision=\"9297f42afa37eaabf1328b44f9f583fc12638c58\"/>"
					+ "<project name=\"b\" path=\"b\" revision=\"c27d6b02c859b291878db67f256cefac3adb26df\"/>"
					+ "<project name=\"c\" path=\"c\" revision=\"7086d7305fa6c7c1930de1e7d96fffc9c819b479\"/>"
					+ "<project name=\"d\" path=\"d\" revision=\"a9def1a887d12c9a63df1d47a77d4cf4baeb7867\"/>"
					+ "</manifest>";
	/**
	 * manifestThree removes project c and has a new commit for project b.
	 */
	private String manifestThree =
			"<manifest>"
					+ "<project name=\"a\" path=\"a\" revision=\"9297f42afa37eaabf1328b44f9f583fc12638c58\"/>"
					+ "<project name=\"b\" path=\"b\" revision=\"2943f21d673d102f580efb9d8fe52770a57d2632\"/>"
					+ "<project name=\"d\" path=\"d\" revision=\"a9def1a887d12c9a63df1d47a77d4cf4baeb7867\"/>"
					+ "</manifest>";

	@Override
	protected void setUp() throws Exception {
		super.setUp();

		stateOne = new RevisionState(manifestOne, "master", null);
		stateOneCopy = new RevisionState(manifestOne, "master", null);
		stateTwo = new RevisionState(manifestTwo, "master", null);
		stateThree = new RevisionState(manifestThree, "master", null);
	}

	/**
	 * Test {@link RevisionState#equals}.
	 */
	public void testEquality() {
		Assert.assertTrue(stateOne.equals(stateOneCopy));
		Assert.assertFalse(stateOne.equals(stateTwo));
		Assert.assertFalse(stateTwo.equals(stateThree));
	}

	/**
	 * Test {@link RevisionState#whatChanged(RevisionState)}.
	 */
	public void testChangeDetection() {
		Assert.assertTrue(stateOneCopy.whatChanged(stateOne).isEmpty());

		List<ProjectState> changes = stateTwo.whatChanged(stateOne);
		List<ProjectState> expectedChanges = new ArrayList<ProjectState>();
		expectedChanges.add(ProjectState.constructCachedInstance("a", "a", "c9039e9649d133d80073e432816b9b4915776b41"));
		expectedChanges.add(ProjectState.constructCachedInstance("c", "c", "fa822eff984195ec8923718cd025fd44b77a26ef"));
		expectedChanges.add(ProjectState.constructCachedInstance("d", "d", null));
		Assert.assertEquals(expectedChanges, changes);
		
		changes = stateThree.whatChanged(stateTwo);
		expectedChanges.clear();
		expectedChanges.add(ProjectState.constructCachedInstance("b", "b", "c27d6b02c859b291878db67f256cefac3adb26df"));
		expectedChanges.add(ProjectState.constructCachedInstance("c", "c", "7086d7305fa6c7c1930de1e7d96fffc9c819b479"));
		Assert.assertEquals(expectedChanges, changes);
	}
}
