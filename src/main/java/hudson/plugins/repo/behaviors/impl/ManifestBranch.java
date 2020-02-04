/*
 * The MIT License
 *
 * Copyright (c) 2010, Brad Larson
 * Copyright (c) 2019, CloudBees Inc.
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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Adds the manifest's branch parameter.
 */
@ExportedBean
public class ManifestBranch extends RepoScmBehavior<ManifestBranch> {

    @Nonnull
    private final String manifestBranch;

    /**
     * Standard databound constructor.
     *
     * @param manifestBranch The manifest's branch
     */
    @DataBoundConstructor
    public ManifestBranch(@Nonnull final String manifestBranch) {
        this.manifestBranch = manifestBranch;
    }

    /**
     * The manifest's branch.
     *
     * @return
     */
    @Nonnull
    @Exported
    public String getManifestBranch() {
        return manifestBranch;
    }

    @Override
    public boolean decorateInit(@Nonnull final List<String> commands,
                                final EnvVars env,
                                @Nonnull final TaskListener listener)
            throws TraitApplicationException {
        commands.add("-b");
        commands.add(env.expand(manifestBranch));
        return true;
    }

    /**
     * The descriptor.
     */
    @Extension(ordinal = 30)
    public static class DescriptorImpl extends RepoScmBehaviorDescriptor<ManifestBranch> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.ManifestBranch_DescriptorImpl_DisplayName();
        }
    }
}
