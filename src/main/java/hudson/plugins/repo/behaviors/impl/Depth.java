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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * The depth used for sync.  By default repo will sync the entire history.
 */
@ExportedBean
public class Depth extends RepoScmBehavior<Depth> {

    private final int depth;

    /**
     * Databound constructor.
     *
     * @param depth the depth where 0 means full history
     */
    @DataBoundConstructor
    public Depth(final int depth) {
        this.depth = depth;
    }

    @Override
    public boolean decorateInit(@Nonnull final List<String> commands,
                                final EnvVars env,
                                @Nonnull final TaskListener listener) throws TraitApplicationException {
        if (depth != 0) {
            commands.add("--depth=" + depth);
        }
        return true;
    }

    /**
     * Returns the depth used for sync.  By default repo will sync the entire history.
     * @return the depth
     */
    public int getDepth() {
        return depth;
    }

    /**
     * The descriptor.
     */
    @Extension(ordinal = 90)
    public static final class DescriptorImpl extends RepoScmBehaviorDescriptor<Depth> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.Depth_DescriptorImpl_DisplayName();
        }
    }
}
