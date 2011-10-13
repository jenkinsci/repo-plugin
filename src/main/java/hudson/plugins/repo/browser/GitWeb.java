/*
 * The MIT License
 *
 * Copyright (c) 2011, Tim Gover.
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


package hudson.plugins.repo.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.repo.ChangeLogEntry;
import hudson.scm.RepositoryBrowser;

import hudson.scm.browsers.QueryBuilder;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.DataBoundConstructor;

/** SCM browser class for GitWeb. The code assumes the GitWeb server contains
 * every repository referenced by repo.
 */
public class GitWeb extends RepositoryBrowser<ChangeLogEntry> {

	private static final long serialVersionUID = 1L;
	private final URL url;

	/** Constructor.
	 * @param url The base url for GitWeb.
	 * @throws MalformedURLException
	 * is thrown if the URL is garbage.
	 */
	@DataBoundConstructor
	public GitWeb(final String url) throws MalformedURLException {
		this.url = new URL(url);
	}

	/**
	 * Returns the base URL for GitWeb.
	 */
	public URL getUrl() {
		return url;
	}

	@Override
	public URL getChangeSetLink(final ChangeLogEntry changeSet)
		throws IOException {
		return new URL(url,
				url.getPath()
				+ param().add("p=" + changeSet.getServerPath() + ".git")
				.add("a=commit")
				.add("h=" + changeSet.getRevision()).toString());
		}

	private QueryBuilder param() {
		return new QueryBuilder(url.getQuery());
	}

	/** Extension for fully annotating changes. */
	@Extension
	public static class GitWebDescriptor
		extends Descriptor<RepositoryBrowser<?>> {

		/** Returns the display name. */
		public String getDisplayName() {
			return "gitweb";
		}

		@Override
		public GitWeb newInstance(final StaplerRequest req,
				final JSONObject jsonObject)
			throws FormException {
			return req.bindParameters(GitWeb.class, "gitweb.");
		}
	}
}
