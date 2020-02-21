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
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.repo.RepoScm;
import hudson.plugins.repo.behaviors.RepoScmBehavior;
import hudson.plugins.repo.behaviors.RepoScmBehaviorDescriptor;
import hudson.plugins.repo.behaviors.TraitApplicationException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Does <code>repo forall -c 'git clean -fdx'</code> before syncing.
 */
@ExportedBean
public class CleanFirst extends RepoScmBehavior<CleanFirst> {

    /**
     * Default databound constructor.
     */
    @DataBoundConstructor
    public CleanFirst() {
    }

    @Override
    public boolean preSync(@Nonnull final String executable,
                           @Nonnull final Launcher launcher,
                           @Nonnull final FilePath workspace,
                           @Nonnull final TaskListener listener,
                           final EnvVars env) throws TraitApplicationException {
        List<String> commands = Arrays.asList(
                executable,
                "forall",
                "-c",
                "git clean -fdx"
        );
        try {
            int cleanCode = launcher.launch().stdout(listener.getLogger())
                    .stderr(listener.getLogger()).pwd(workspace).cmds(commands).envs(env).join();

            if (cleanCode != 0) {
                Logger.getLogger(RepoScm.class.getName()).log(Level.WARNING, "Failed to clean first.");
            }
        } catch (Exception e) {
            throw new TraitApplicationException(e, this);
        }
        return true;
    }

    /**
     * The descriptor.
     */
    @Extension(ordinal = 160)
    public static final class DescriptorImpl extends RepoScmBehaviorDescriptor<CleanFirst> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.CleanFirst_DescriptorImpl_DisplayName();
        }
    }
}
