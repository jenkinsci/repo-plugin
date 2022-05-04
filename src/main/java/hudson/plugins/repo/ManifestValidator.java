/*
 * The MIT License
 *
 * Copyright (c) 2010, Brad Larson
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

package hudson.plugins.repo;

import hudson.AbortException;
import jenkins.util.xml.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * to validate manifest xml file and abort when remote references a local path.
 */
public final class ManifestValidator {

    private ManifestValidator() {
        // to hide the implicit public constructor
    }

    /**
     * to validate manifest xml file and abort when remote references a local path.
     * @param manifestText byte representation of manifest file
     * @param manifestRepositoryUrl url
     * @throws IOException when remote references a local path.
     */
    public static void validate(final byte[] manifestText, final String manifestRepositoryUrl)
            throws IOException {
        if (manifestText.length > 0) {
            try {
                Document doc = XMLUtils.parse(new ByteArrayInputStream(manifestText));
                NodeList remote = doc.getElementsByTagName("remote");
                for (int i = 0; i < remote.getLength(); i++) {
                    NamedNodeMap attributes = remote.item(i).getAttributes();
                    for (int j = 0; j < attributes.getLength(); j++) {
                        if ("fetch".equals(attributes.item(j).getNodeName())
                                && attributes.item(j).getNodeValue()
                                .toLowerCase(Locale.ENGLISH).startsWith("file://")) {
                                // we don't need to check source using Files.exists because fetch
                                // attribute could resolve only local paths starting from 'file://'
                            throw new AbortException("Checkout of Repo url '"
                                    + manifestRepositoryUrl
                                    + "' aborted because manifest references a local "
                                    + "directory, which may be insecure. You can allow "
                                    + "local checkouts anyway"
                                    + " by setting the system property '"
                                    + RepoScm.ALLOW_LOCAL_CHECKOUT_PROPERTY + "' to true.");
                        }
                    }
                }
            } catch (SAXException e) {
                throw new IOException("Could not validate manifest");
            }
        }
    }
}
