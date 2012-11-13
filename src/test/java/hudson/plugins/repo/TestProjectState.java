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
public class TestProjectState extends TestCase {

	private ProjectState projectStateA = ProjectState.constructCachedInstance("a", "a",
			"c9039e9649d133d80073e432816b9b4915776b41");


	private ProjectState projectStateB = ProjectState.constructCachedInstance("b", "b",
			"fa822eff984195ec8923718cd025fd44b77a26ef");

	private ProjectState projectStateA2 = ProjectState.constructCachedInstance("a", "a",
			"c9039e9649d133d80073e432816b9b4915776b41");


	/**
	 * Test {@link ProjectState#constructCachedInstance(String, String, String)}
	 */
	public void testCaching()
	{
		Assert.assertTrue(projectStateA == projectStateA2);
		Assert.assertFalse(projectStateA == projectStateB);
		Assert.assertFalse(projectStateB == projectStateA2);
	}

	/**
	 * Test {@link ProjectState#equals(Object)}.
	 */
	public void testEquality()
	{
		Assert.assertTrue(projectStateA.equals(projectStateA2));
		Assert.assertFalse(projectStateA.equals(projectStateB));
		Assert.assertFalse(projectStateB.equals(projectStateA2));
	}
}
