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
package hudson.plugins.repo.behaviors;

import hudson.EnvVars;
import hudson.model.AbstractDescribableImpl;
import hudson.model.TaskListener;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Extension point for implementing commandline additions to the repo command.
 *
 * @param <T> the type
 */
public abstract class RepoScmBehavior<T extends RepoScmBehavior<T>> extends AbstractDescribableImpl<T> {

    /**
     * Decorate the <code>repo init</code> commandline.
     *
     * @param commandline the current commandline
     * @param env the environment
     * @param listener for logging
     * @return true if continue with the call false if abort
     * @throws TraitApplicationException if something happened that needs the user's attention
     */
    public boolean decorateInit(@Nonnull final List<String> commandline,
                                final EnvVars env,
                                @Nonnull final TaskListener listener)
            throws TraitApplicationException {
        return true;
    }

    /**
     * Decorate the <code>repo sync</code> commandline.
     *
     * @param commandline the current commandline
     * @param env the environment
     * @param listener for logging
     * @return true if continue with the call false if abort
     * @throws TraitApplicationException if something happened that needs the user's attention
     */
    public boolean decorateSync(@Nonnull final List<String> commandline,
                                final EnvVars env,
                                @Nonnull final TaskListener listener)
            throws TraitApplicationException {
        return true;
    }
}
