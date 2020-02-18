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
import hudson.model.TaskListener;
import hudson.plugins.repo.behaviors.RepoScmBehavior;
import hudson.plugins.repo.behaviors.RepoScmBehaviorDescriptor;
import hudson.plugins.repo.behaviors.TraitApplicationException;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;
import java.net.URL;

/**
 * The contents of local_manifests/local.xml. By default it is neither created nor modified.
 */
@ExportedBean
public class LocalManifest extends RepoScmBehavior<LocalManifest> {

    private final String localManifest;

    /**
     * Databound constructor.
     *
     * @param localManifest the manifest or a url to that manifest
     */
    @DataBoundConstructor
    public LocalManifest(@Nonnull final String localManifest) {
        if (StringUtils.isEmpty(localManifest)) {
            throw new IllegalArgumentException("empty");
        }
        this.localManifest = localManifest.trim();
    }

    @Override
    public boolean postInit(final FilePath workspace,
                            final EnvVars env,
                            @Nonnull final TaskListener listener) throws TraitApplicationException {
        FilePath lmdir = localManifests(workspace);
        FilePath lm = lmdir.child("local.xml");
        String expandedLocalManifest = env.expand(localManifest);
        try {
            if (expandedLocalManifest.startsWith("<?xml")) {
                lm.write(expandedLocalManifest, null);
            } else {
                URL url = new URL(expandedLocalManifest);
                lm.copyFrom(url);
            }
        } catch (Exception e) {
            throw new TraitApplicationException(e, this);
        }

        return true;
    }

    /**
     * Returns the contents of the to be created local_manifests/local.xml.
     *
     * @return the user input
     */
    @Exported
    public String getLocalManifest() {
        return localManifest;
    }

    /**
     * The descriptor.
     */
    @Extension(ordinal = 140)
    public static final class DescriptorImpl extends RepoScmBehaviorDescriptor<LocalManifest> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.LocalManifest_DescriptorImpl_DisplayName();
        }
    }

    /**
     * Gives a {@link FilePath} to the <code>.repo</code> directory.
     * <p>
     * This method is just a utility method slightly smarter than a constant.
     *
     * @param workspace the current workspace
     * @return the filepath to workspace/.repo
     */
    public static FilePath dotRepo(@Nonnull final FilePath workspace) {
        return workspace.child(".repo");
    }

    /**
     * Gives a {@link FilePath} to the <code>local_manifests</code> directory.
     * <p>
     * This method is just a utility method slightly smarter than a constant.
     * If th given filepath's name is .repo the method returns the relative file path.
     * If it is not then it's assumed that the current workspace is given
     * and <code>f/.repo/local_manifests</code> is returned.
     *
     * @param f the workspace ot .repo directory.
     * @return the path to local_manifests
     */
    public static FilePath localManifests(@Nonnull final FilePath f) {
        if (".repo".equals(f.getName())) {
            return f.child("local_manifests");
        } else {
            //Assume workspace
            return dotRepo(f).child("local_manifests");
        }
    }
}
