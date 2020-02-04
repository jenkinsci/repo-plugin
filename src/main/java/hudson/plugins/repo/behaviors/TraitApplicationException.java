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

import java.io.IOException;

/**
 * Can be thrown from a {@link RepoScmBehavior} when aplpying the trait in case of severe errors.
 * The trait's display name will be included in the message.
 */
public class TraitApplicationException extends IOException {
    private final RepoScmBehavior<? extends RepoScmBehavior<?>> trait;

    /**
     * Constructor.
     *
     * @param trait the trait that caused this
     */
    public TraitApplicationException(final RepoScmBehavior<? extends RepoScmBehavior<?>> trait) {
        super("Could not apply \"" + trait.getDescriptor().getDisplayName() + "\"");
        this.trait = trait;
    }

    /**
     * Constructor.
     *
     * @param message human readable message
     * @param trait the trait that caused this
     */
    public TraitApplicationException(final String message,
                                     final RepoScmBehavior<? extends RepoScmBehavior<?>> trait) {
        super(trait.getDescriptor().getDisplayName() + ": " + message);
        this.trait = trait;
    }

    /**
     * Constructor.
     *
     * @param message human readable message
     * @param cause the cause
     * @param trait the trait that caused this
     */
    public TraitApplicationException(final String message, final Throwable cause,
                                     final RepoScmBehavior<? extends RepoScmBehavior<?>> trait) {
        super(trait.getDescriptor().getDisplayName() + ": " + message, cause);
        this.trait = trait;
    }

    /**
     * Constructor.
     *
     * @param cause the cause
     * @param trait the trait that caused this
     */
    public TraitApplicationException(final Throwable cause,
                                     final RepoScmBehavior<? extends RepoScmBehavior<?>> trait) {
        super("Could not apply \"" + trait.getDescriptor().getDisplayName() + "\": "
                + cause.getMessage(),
                cause);
        this.trait = trait;
    }

    /**
     * The trait that caused this.
     */
    public RepoScmBehavior<? extends RepoScmBehavior<?>> getTrait() {
        return trait;
    }
}
