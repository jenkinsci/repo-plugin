package hudson.plugins.repo;

import junit.framework.Assert;

import org.junit.Test;

public class EnvVarUtilsTest {

	@Test
	public void testConvertToEnvVariableName() {
		Assert.assertEquals("MY_NAME", EnvVarUtils.convertToEnvVariableName("my-name"));
		Assert.assertEquals("MY_NAME", EnvVarUtils.convertToEnvVariableName("my#name"));
		Assert.assertEquals("MY1_NAME", EnvVarUtils.convertToEnvVariableName("my1=name"));
	}

}
