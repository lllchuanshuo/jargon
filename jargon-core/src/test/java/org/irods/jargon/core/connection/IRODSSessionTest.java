package org.irods.jargon.core.connection;

import java.util.Properties;
import java.util.concurrent.Executor;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.irods.jargon.testutils.TestingPropertiesHelper;
import org.junit.BeforeClass;
import org.junit.Test;

public class IRODSSessionTest {

	private static Properties testingProperties = new Properties();
	private static org.irods.jargon.testutils.TestingPropertiesHelper testingPropertiesHelper = new TestingPropertiesHelper();

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		org.irods.jargon.testutils.TestingPropertiesHelper testingPropertiesLoader = new TestingPropertiesHelper();
		testingProperties = testingPropertiesLoader.getTestProperties();

	}

	@Test
	public final void testCloseSessionTwice() throws Exception {
		IRODSProtocolManager irodsConnectionManager = IRODSSimpleProtocolManager
				.instance();
		testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSSession irodsSession = IRODSSession
				.instance(irodsConnectionManager);
		irodsSession.closeSession();
		irodsSession.closeSession();

	}

	@Test
	public void testGetDefaultJargonProperties() throws Exception {
		IRODSProtocolManager irodsConnectionManager = IRODSSimpleProtocolManager
				.instance();
		testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSSession irodsSession = IRODSSession
				.instance(irodsConnectionManager);
		irodsSession.closeSession();
		JargonProperties jargonProperties = irodsSession.getJargonProperties();
		Assert.assertNotNull("null jargon properties", jargonProperties);

	}

	@Test
	public void testOverrideDefaultJargonProperties() throws Exception {
		IRODSProtocolManager irodsConnectionManager = IRODSSimpleProtocolManager
				.instance();
		testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSSession irodsSession = IRODSSession
				.instance(irodsConnectionManager);
		irodsSession.closeSession();

		SettableJargonProperties overrideJargonProperties = new SettableJargonProperties();
		overrideJargonProperties.setMaxParallelThreads(8000);
		irodsSession.setJargonProperties(overrideJargonProperties);
		JargonProperties jargonProperties = irodsSession.getJargonProperties();
		Assert.assertEquals("did not get the preset number of threads", 8000,
				jargonProperties.getMaxParallelThreads());

	}
	
	@Test
	public void testBuildTransferThreadPool() throws Exception {
		IRODSProtocolManager irodsConnectionManager = IRODSSimpleProtocolManager
				.instance();
		testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSSession irodsSession = IRODSSession
				.instance(irodsConnectionManager);
		irodsSession.closeSession();

		SettableJargonProperties overrideJargonProperties = new SettableJargonProperties();
		overrideJargonProperties.setMaxParallelThreads(8000);
		overrideJargonProperties.setUseTransferThreadsPool(true);
		overrideJargonProperties.setTransferThreadCorePoolSize(4);
		overrideJargonProperties.setTransferThreadMaxPoolSize(16);
		overrideJargonProperties.setTransferThreadPoolTimeoutMillis(60000);
		irodsSession.setJargonProperties(overrideJargonProperties);
		Executor executor = irodsSession.getParallelTransferThreadPool();
		TestCase.assertNotNull("executor was null", executor);

	}
	
	@Test
	public void testBuildTransferThreadPoolAndGetTwice() throws Exception {
		IRODSProtocolManager irodsConnectionManager = IRODSSimpleProtocolManager
				.instance();
		testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSSession irodsSession = IRODSSession
				.instance(irodsConnectionManager);
		irodsSession.closeSession();

		SettableJargonProperties overrideJargonProperties = new SettableJargonProperties();
		overrideJargonProperties.setMaxParallelThreads(8000);
		overrideJargonProperties.setUseTransferThreadsPool(true);
		overrideJargonProperties.setTransferThreadCorePoolSize(4);
		overrideJargonProperties.setTransferThreadMaxPoolSize(16);
		overrideJargonProperties.setTransferThreadPoolTimeoutMillis(60000);
		irodsSession.setJargonProperties(overrideJargonProperties);
		Executor executor = irodsSession.getParallelTransferThreadPool();
		executor = irodsSession.getParallelTransferThreadPool();
		TestCase.assertNotNull("executor was null", executor);

	}
	
	@Test
	public void testBuildTransferThreadPoolNotInProps() throws Exception {
		IRODSProtocolManager irodsConnectionManager = IRODSSimpleProtocolManager
				.instance();
		testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSSession irodsSession = IRODSSession
				.instance(irodsConnectionManager);
		irodsSession.closeSession();

		SettableJargonProperties overrideJargonProperties = new SettableJargonProperties();
		overrideJargonProperties.setMaxParallelThreads(8000);
		overrideJargonProperties.setUseTransferThreadsPool(false);
		overrideJargonProperties.setTransferThreadCorePoolSize(4);
		overrideJargonProperties.setTransferThreadMaxPoolSize(16);
		overrideJargonProperties.setTransferThreadPoolTimeoutMillis(60000);
		irodsSession.setJargonProperties(overrideJargonProperties);
		Executor executor = irodsSession.getParallelTransferThreadPool();
		TestCase.assertNull("executor should be  null", executor);

	}

}
