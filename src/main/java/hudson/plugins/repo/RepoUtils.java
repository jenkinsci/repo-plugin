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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.StreamBuildListener;
import hudson.model.TaskListener;
import hudson.slaves.NodeProperty;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Copied from git plugin GitUtils.
 * @author Rainer Burgstaller
 */
public final class RepoUtils {

    /**
     * Private constructor to prevent construction.
     */
    private RepoUtils() {
    }

    /**
     * An attempt to generate at least semi-useful EnvVars for polling calls,
     * based on previous build. Cribbed from various places.
     * <p>
     * Copied from GitUtils from the Git plugin. I did not want to add a
     * dependency on that plugin.
     *
     * @param p project
     * @param ws	workspace
     * @param launcher	the launcher
     * @param listener	the listener
     * @param reuseLastBuildEnv	true for reusing
     * @return the environment variables
     * @throws IOException if something bad happens
     * @throws InterruptedException if something bad happens
     */
    public static EnvVars getPollEnvironment(final AbstractProject p,
	    final FilePath ws,
	    final Launcher launcher, final TaskListener listener,
	    final boolean reuseLastBuildEnv)
	    throws IOException, InterruptedException {
	EnvVars env;
	StreamBuildListener buildListener = new StreamBuildListener(
		(OutputStream) listener.getLogger());
	AbstractBuild b = (AbstractBuild) p.getLastBuild();

	if (reuseLastBuildEnv && b != null) {
	    Node lastBuiltOn = b.getBuiltOn();

	    if (lastBuiltOn != null) {
		env = lastBuiltOn.toComputer().getEnvironment()
			.overrideAll(b.getCharacteristicEnvVars());
		for (NodeProperty nodeProperty : lastBuiltOn
			.getNodeProperties()) {
		    Environment environment = nodeProperty.setUp(b, launcher,
			    (BuildListener) buildListener);
		    if (environment != null) {
			environment.buildEnvVars(env);
		    }
		}
	    } else {
		env = new EnvVars(System.getenv());
	    }

	    p.getScm().buildEnvVars(b, env);

//	    if (lastBuiltOn != null) {
//
//	    }

	} else {
	    env = new EnvVars(System.getenv());
	}

	String rootUrl = Hudson.getInstance().getRootUrl();
	if (rootUrl != null) {
	    env.put("HUDSON_URL", rootUrl); // Legacy.
	    env.put("JENKINS_URL", rootUrl);
	    if (b != null) {
		env.put("BUILD_URL", rootUrl + b.getUrl());
	    }
	    env.put("JOB_URL", rootUrl + p.getUrl());
	}

	if (!env.containsKey("HUDSON_HOME")) {
	    // Legacy
	    env.put("HUDSON_HOME",
		    Hudson.getInstance().getRootDir().getPath());
	}

	if (!env.containsKey("JENKINS_HOME")) {
	    env.put("JENKINS_HOME",
		    Hudson.getInstance().getRootDir().getPath());
	}

	if (ws != null) {
	    env.put("WORKSPACE", ws.getRemote());
	}

	for (NodeProperty nodeProperty : Hudson.getInstance()
		.getGlobalNodeProperties()) {
	    Environment environment = nodeProperty.setUp(b, launcher,
		    (BuildListener) buildListener);
	    if (environment != null) {
		environment.buildEnvVars(env);
	    }
	}

	EnvVars.resolve(env);

	return env;
    }
}
