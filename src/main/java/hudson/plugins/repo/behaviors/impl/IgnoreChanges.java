/*
 * The MIT License
 *
 * Copyright (c) 2010, Brad Larson
 * Copyright (c) 2020, CloudBees Inc.
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
package hudson.plugins.repo.behaviors.impl;

import hudson.Extension;
import hudson.plugins.repo.ProjectState;
import hudson.plugins.repo.RevisionState;
import hudson.plugins.repo.behaviors.RepoScmBehavior;
import hudson.plugins.repo.behaviors.RepoScmBehaviorDescriptor;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * List of projects which changes will be ignored when calculating whether job needs to be rebuild.
 */
@ExportedBean
public class IgnoreChanges extends RepoScmBehavior<IgnoreChanges> {

    @Nonnull
    private final Set<String> ignoreProjects;

    /**
     * Databound constructor.
     *
     * @param ignoreProjects space separated list of projects. This field corresponds
     *                       to serverpath i.e. "name" section of the manifest.
     */
    @DataBoundConstructor
    public IgnoreChanges(@Nonnull final String ignoreProjects) {
        if (StringUtils.isEmpty(ignoreProjects)) {
            throw new IllegalArgumentException("empty");
        }
        this.ignoreProjects = new LinkedHashSet<>(
                Arrays.asList(ignoreProjects.split("\\s+")));
    }

    /**
     * Internal constructor.
     *
     * @param ignoreProjects list of projects.
     * @see hudson.plugins.repo.RepoScm#readResolve()
     */
    @Restricted(NoExternalUse.class)
    public IgnoreChanges(@Nonnull final Set<String> ignoreProjects) {
        this.ignoreProjects = ignoreProjects;
    }

    @Override
    public boolean shouldIgnoreChanges(final List<ProjectState> changedProjects,
                                       final RevisionState current,
                                       final RevisionState baseline) {
        // Check for every changed item if it is not contained in the
        // ignored setting .. project must be rebuilt
        for (ProjectState changed : changedProjects) {
            if (!ignoreProjects.contains(changed.getServerPath())) {
                return false;
            }
        }
        return true;
    }

    /**
     * The list of projects to ignore separated by new line.
     * <p>
     * This field corresponds to serverpath i.e. "name" section of the manifest.
     *
     * @return the projects configured to be ignored
     */
    @Nonnull
    @Exported
    public String getIgnoreProjects() {
        return StringUtils.join(ignoreProjects, '\n');
    }

    /**
     * The descriptor.
     */
    @Extension(ordinal = 200)
    public static final class DescriptorImpl extends RepoScmBehaviorDescriptor<IgnoreChanges> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.IgnoreChanges_DescriptorImpl_DisplayName();
        }
    }
}
