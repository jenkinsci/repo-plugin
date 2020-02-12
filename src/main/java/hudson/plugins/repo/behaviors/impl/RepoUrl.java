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

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.repo.behaviors.RepoScmBehavior;
import hudson.plugins.repo.behaviors.RepoScmBehaviorDescriptor;
import hudson.plugins.repo.behaviors.TraitApplicationException;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * The repo url. By default, repo is fetched from AOSP.
 */
@ExportedBean
public class RepoUrl extends RepoScmBehavior<RepoUrl> {

    @Nonnull private final String repoUrl;

    /**
     * Databound constructor.
     * @param repoUrl The repo url.
     */
    @DataBoundConstructor
    public RepoUrl(@Nonnull final String repoUrl) {
        if (StringUtils.isEmpty(repoUrl)) {
            throw new IllegalArgumentException("empty");
        }
        this.repoUrl = repoUrl;
    }

    @Override
    public boolean decorateInit(@Nonnull final List<String> commands,
                                final EnvVars env,
                                @Nonnull final TaskListener listener) throws TraitApplicationException {
        commands.add("--repo-url=" + env.expand(repoUrl));
        commands.add("--no-repo-verify");
        return true;
    }

    /**
     * The repo url.
     * @return The repo url.
     */
    @Nonnull @Exported
    public String getRepoUrl() {
        return repoUrl;
    }

    /**
     * The descriptor.
     */
    @Extension(ordinal = 60)
    public static final class DescriptorImpl extends RepoScmBehaviorDescriptor<RepoUrl> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.RepoUrl_DescriptorImpl_DisplayName();
        }
    }
}
