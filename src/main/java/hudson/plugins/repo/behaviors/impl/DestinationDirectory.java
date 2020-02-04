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

import hudson.Extension;
import hudson.plugins.repo.behaviors.RepoScmBehaviorDescriptor;
import hudson.plugins.repo.behaviors.RepoScmBehavior;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;

/**
 * The destination directory.
 *
 * By default, the source is synced to the root of the workspace.
 */
@ExportedBean
public class DestinationDirectory extends RepoScmBehavior<DestinationDirectory> {
    @Nonnull
    private final String destinationDir;

    /**
     * DataBound Constructor.
     *
     * @param destinationDir The destination directory
     */
    @DataBoundConstructor
    public DestinationDirectory(@Nonnull final String destinationDir) {
        if (StringUtils.isEmpty(destinationDir)) {
            throw new IllegalArgumentException("destinationDir may not be empty.");
        }
        this.destinationDir = destinationDir;
    }

    /**
     * The destination directory.
     *
     * @return The destination directory
     */
    @Nonnull @Exported
    public String getDestinationDir() {
        return destinationDir;
    }

    /**
     * The descriptor.
     */
    @Extension(ordinal = 10)
    public static class DescriptorImpl extends RepoScmBehaviorDescriptor<DestinationDirectory> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.DestinationDirectory_DescriptorImpl_DisplayName();
        }
    }
}
