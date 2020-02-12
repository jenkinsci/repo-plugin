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
 * Add the "--no-clone-bundle" option when running the "repo init" and "repo sync" commands.
 */
@ExportedBean
public class NoCloneBundle extends RepoScmBehavior<NoCloneBundle> {

    /**
     * Default databound constructor.
     */
    @DataBoundConstructor
    public NoCloneBundle() {
    }

    @Override
    public boolean decorateInit(@Nonnull final List<String> commands,
                                final EnvVars env, @Nonnull
                                    final TaskListener listener) throws TraitApplicationException {
        commands.add("--no-clone-bundle");
        return true;
    }

    @Override
    public boolean decorateSync(@Nonnull final List<String> commands,
                                final EnvVars env, @Nonnull
                                    final TaskListener listener) throws TraitApplicationException {
        commands.add("--no-clone-bundle");
        return true;
    }

    /**
     * The descriptor.
     */
    @Extension(ordinal = 100)
    public static final class DescriptorImpl extends RepoScmBehaviorDescriptor<NoCloneBundle> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.NoCloneBundle_DescriptorImpl_DisplayName();
        }
    }
}
