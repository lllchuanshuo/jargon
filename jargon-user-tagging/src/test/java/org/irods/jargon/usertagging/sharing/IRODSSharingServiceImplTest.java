package org.irods.jargon.usertagging.sharing;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import junit.framework.Assert;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.FileNotFoundException;
import org.irods.jargon.core.protovalues.FilePermissionEnum;
import org.irods.jargon.core.pub.DataTransferOperations;
import org.irods.jargon.core.pub.IRODSAccessObjectFactory;
import org.irods.jargon.core.pub.IRODSFileSystem;
import org.irods.jargon.core.pub.io.IRODSFile;
import org.irods.jargon.core.query.MetaDataAndDomainData.MetadataDomain;
import org.irods.jargon.testutils.TestingPropertiesHelper;
import org.irods.jargon.testutils.filemanip.FileGenerator;
import org.irods.jargon.usertagging.domain.IRODSSharedFileOrCollection;
import org.irods.jargon.usertagging.domain.ShareUser;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/*
 * This one does direct iRODS tests instead of mocks to help play with queries, etc
 * @author Mike Conway - DICE (www.irods.org)
 *
 */
public class IRODSSharingServiceImplTest {

	private static Properties testingProperties = new Properties();
	private static org.irods.jargon.testutils.TestingPropertiesHelper testingPropertiesHelper = new TestingPropertiesHelper();
	private static org.irods.jargon.testutils.filemanip.ScratchFileUtils scratchFileUtils = null;
	public static final String IRODS_TEST_SUBDIR_PATH = "IRODSSharingServiceImplTest";
	private static org.irods.jargon.testutils.IRODSTestSetupUtilities irodsTestSetupUtilities = null;
	private static IRODSFileSystem irodsFileSystem = null;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		org.irods.jargon.testutils.TestingPropertiesHelper testingPropertiesLoader = new TestingPropertiesHelper();
		testingProperties = testingPropertiesLoader.getTestProperties();
		scratchFileUtils = new org.irods.jargon.testutils.filemanip.ScratchFileUtils(
				testingProperties);
		irodsTestSetupUtilities = new org.irods.jargon.testutils.IRODSTestSetupUtilities();
		irodsTestSetupUtilities.initializeIrodsScratchDirectory();
		irodsTestSetupUtilities
				.initializeDirectoryForTest(IRODS_TEST_SUBDIR_PATH);
		irodsFileSystem = IRODSFileSystem.instance();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		irodsFileSystem.closeAndEatExceptions();
	}

	@Test
	public void testCreateShareCollection() throws Exception {
		String testDirName = "testCreateShareCollection";
		String targetIrodsCollection = testingPropertiesHelper
				.buildIRODSCollectionAbsolutePathFromTestProperties(
						testingProperties, IRODS_TEST_SUBDIR_PATH + '/'
								+ testDirName);

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem
				.getIRODSAccessObjectFactory();

		IRODSFile irodsFile = accessObjectFactory.getIRODSFileFactory(
				irodsAccount).instanceIRODSFile(targetIrodsCollection);
		irodsFile.mkdirs();

		IRODSSharingService irodsSharingService = new IRODSSharingServiceImpl(
				accessObjectFactory, irodsAccount);

		IRODSAccount secondaryAccount = testingPropertiesHelper
				.buildIRODSAccountFromSecondaryTestProperties(testingProperties);

		List<ShareUser> shareUsers = new ArrayList<ShareUser>();
		shareUsers.add(new ShareUser(secondaryAccount.getUserName(),
				secondaryAccount.getZone(), FilePermissionEnum.WRITE));
		IRODSSharedFileOrCollection irodsSharedFile = new IRODSSharedFileOrCollection(
				MetadataDomain.COLLECTION, irodsFile.getAbsolutePath(),
				testDirName, irodsAccount.getUserName(),
				irodsAccount.getZone(), shareUsers);
		irodsSharingService.createShare(irodsSharedFile);
		// need a test here to verify the share and access
		IRODSSharedFileOrCollection actual = irodsSharingService
				.findShareByAbsolutePath(targetIrodsCollection);
		Assert.assertNotNull("no sharing found that I just added", actual);
		Assert.assertEquals("wrong share name", testDirName,
				actual.getShareName());
		Assert.assertEquals("wrong path", targetIrodsCollection,
				actual.getDomainUniqueName());
		Assert.assertEquals("wrong metadataDomain", MetadataDomain.COLLECTION,
				actual.getMetadataDomain());
		Assert.assertEquals("wrong owner name", irodsAccount.getUserName(),
				actual.getShareOwner());
		Assert.assertEquals("wrong zone", irodsAccount.getZone(),
				actual.getShareOwnerZone());
		Assert.assertEquals("did not get 2 permissions", 2, actual
				.getShareUsers().size());
		boolean writeACLFound = false;

		for (ShareUser shareUser : actual.getShareUsers()) {
			if (shareUser.getUserName().equals(secondaryAccount.getUserName())
					&& shareUser.getFilePermission() == FilePermissionEnum.WRITE) {
				writeACLFound = true;
			}
		}

		Assert.assertTrue("did not find write permission for secondary user",
				writeACLFound);

	}
	
	@Test(expected=ShareAlreadyExistsException.class)
	public void testCreateShareCollectionDuplicate() throws Exception {
		String testDirName = "testCreateShareCollectionDuplicate";
		String targetIrodsCollection = testingPropertiesHelper
				.buildIRODSCollectionAbsolutePathFromTestProperties(
						testingProperties, IRODS_TEST_SUBDIR_PATH + '/'
								+ testDirName);

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem
				.getIRODSAccessObjectFactory();

		IRODSFile irodsFile = accessObjectFactory.getIRODSFileFactory(
				irodsAccount).instanceIRODSFile(targetIrodsCollection);
		irodsFile.mkdirs();

		IRODSSharingService irodsSharingService = new IRODSSharingServiceImpl(
				accessObjectFactory, irodsAccount);

		IRODSAccount secondaryAccount = testingPropertiesHelper
				.buildIRODSAccountFromSecondaryTestProperties(testingProperties);

		List<ShareUser> shareUsers = new ArrayList<ShareUser>();
		shareUsers.add(new ShareUser(secondaryAccount.getUserName(),
				secondaryAccount.getZone(), FilePermissionEnum.WRITE));
		IRODSSharedFileOrCollection irodsSharedFile = new IRODSSharedFileOrCollection(
				MetadataDomain.COLLECTION, irodsFile.getAbsolutePath(),
				testDirName, irodsAccount.getUserName(),
				irodsAccount.getZone(), shareUsers);
		irodsSharingService.createShare(irodsSharedFile);
		irodsSharingService.createShare(irodsSharedFile);
		

	}

	@Test(expected=FileNotFoundException.class)
	public void testCreateShareCollectionNotExists() throws Exception {
		String testDirName = "testCreateShareCollectionNotExists";
		String targetIrodsCollection = testingPropertiesHelper
				.buildIRODSCollectionAbsolutePathFromTestProperties(
						testingProperties, IRODS_TEST_SUBDIR_PATH + '/'
								+ testDirName);

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem
				.getIRODSAccessObjectFactory();

		IRODSFile irodsFile = accessObjectFactory.getIRODSFileFactory(
				irodsAccount).instanceIRODSFile(targetIrodsCollection);

		IRODSSharingService irodsSharingService = new IRODSSharingServiceImpl(
				accessObjectFactory, irodsAccount);

		IRODSAccount secondaryAccount = testingPropertiesHelper
				.buildIRODSAccountFromSecondaryTestProperties(testingProperties);

		List<ShareUser> shareUsers = new ArrayList<ShareUser>();
		shareUsers.add(new ShareUser(secondaryAccount.getUserName(),
				secondaryAccount.getZone(), FilePermissionEnum.WRITE));
		IRODSSharedFileOrCollection irodsSharedFile = new IRODSSharedFileOrCollection(
				MetadataDomain.COLLECTION, irodsFile.getAbsolutePath(),
				testDirName, irodsAccount.getUserName(),
				irodsAccount.getZone(), shareUsers);
		irodsSharingService.createShare(irodsSharedFile);
      
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testCreateShareCollectionNull() throws Exception {
		
		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem
				.getIRODSAccessObjectFactory();
		IRODSSharingService irodsSharingService = new IRODSSharingServiceImpl(
				accessObjectFactory, irodsAccount);
		irodsSharingService.createShare(null);

	}

	@Test
	public void testCreateShareDataObject() throws Exception {
		String testFileName = "testCreateShareDataObject.txt";

		String targetIrodsCollection = testingPropertiesHelper
				.buildIRODSCollectionAbsolutePathFromTestProperties(
						testingProperties, IRODS_TEST_SUBDIR_PATH);

		String targetIrodsDataObject = targetIrodsCollection + "/"
				+ testFileName;

		String absPath = scratchFileUtils
				.createAndReturnAbsoluteScratchPath(IRODS_TEST_SUBDIR_PATH);
		String fileNameOrig = FileGenerator.generateFileOfFixedLengthGivenName(
				absPath, testFileName, 2);

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem
				.getIRODSAccessObjectFactory();

		IRODSFile targetIrodsFile = irodsFileSystem.getIRODSFileFactory(
				irodsAccount).instanceIRODSFile(targetIrodsCollection);
		targetIrodsFile.deleteWithForceOption();
		targetIrodsFile.mkdirs();
		DataTransferOperations dataTransferOperationsAO = irodsFileSystem
				.getIRODSAccessObjectFactory().getDataTransferOperations(
						irodsAccount);
		dataTransferOperationsAO.putOperation(new File(fileNameOrig),
				targetIrodsFile, null, null);

		IRODSSharingService irodsSharingService = new IRODSSharingServiceImpl(
				accessObjectFactory, irodsAccount);

		IRODSAccount secondaryAccount = testingPropertiesHelper
				.buildIRODSAccountFromSecondaryTestProperties(testingProperties);

		List<ShareUser> shareUsers = new ArrayList<ShareUser>();
		shareUsers.add(new ShareUser(secondaryAccount.getUserName(),
				secondaryAccount.getZone(), FilePermissionEnum.WRITE));
		IRODSSharedFileOrCollection irodsSharedFile = new IRODSSharedFileOrCollection(
				MetadataDomain.DATA, targetIrodsDataObject, testFileName,
				irodsAccount.getUserName(), irodsAccount.getZone(), shareUsers);
		irodsSharingService.createShare(irodsSharedFile);
		// need a test here to verify the share and access
		IRODSSharedFileOrCollection actual = irodsSharingService
				.findShareByAbsolutePath(targetIrodsDataObject);
		Assert.assertNotNull("no sharing found that I just added", actual);
		Assert.assertEquals("wrong share name", testFileName,
				actual.getShareName());
		Assert.assertEquals("wrong path", targetIrodsDataObject,
				actual.getDomainUniqueName());
		Assert.assertEquals("wrong metadataDomain", MetadataDomain.DATA,
				actual.getMetadataDomain());
		Assert.assertEquals("wrong owner name", irodsAccount.getUserName(),
				actual.getShareOwner());
		Assert.assertEquals("wrong zone", irodsAccount.getZone(),
				actual.getShareOwnerZone());
		Assert.assertEquals("did not get 2 permissions", 2, actual
				.getShareUsers().size());
		boolean writeACLFound = false;

		for (ShareUser shareUser : actual.getShareUsers()) {
			if (shareUser.getUserName().equals(secondaryAccount.getUserName())
					&& shareUser.getFilePermission() == FilePermissionEnum.WRITE) {
				writeACLFound = true;
			}
		}

		Assert.assertTrue("did not find write permission for secondary user",
				writeACLFound);
	}
	
	@Test
	public void testDeleteShareCollection() throws Exception {
		String testDirName = "testDeleteShareCollection";
		String targetIrodsCollection = testingPropertiesHelper
				.buildIRODSCollectionAbsolutePathFromTestProperties(
						testingProperties, IRODS_TEST_SUBDIR_PATH + '/'
								+ testDirName);

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem
				.getIRODSAccessObjectFactory();

		IRODSFile irodsFile = accessObjectFactory.getIRODSFileFactory(
				irodsAccount).instanceIRODSFile(targetIrodsCollection);
		irodsFile.mkdirs();

		IRODSSharingService irodsSharingService = new IRODSSharingServiceImpl(
				accessObjectFactory, irodsAccount);

		IRODSAccount secondaryAccount = testingPropertiesHelper
				.buildIRODSAccountFromSecondaryTestProperties(testingProperties);

		List<ShareUser> shareUsers = new ArrayList<ShareUser>();
		shareUsers.add(new ShareUser(secondaryAccount.getUserName(),
				secondaryAccount.getZone(), FilePermissionEnum.WRITE));
		IRODSSharedFileOrCollection irodsSharedFile = new IRODSSharedFileOrCollection(
				MetadataDomain.COLLECTION, irodsFile.getAbsolutePath(),
				testDirName, irodsAccount.getUserName(),
				irodsAccount.getZone(), shareUsers);
		irodsSharingService.createShare(irodsSharedFile);
		
		// now delete
		
		irodsSharingService.removeShare(irodsFile.getAbsolutePath());
		// need a test here to verify the share and access
		
		IRODSSharedFileOrCollection actual = irodsSharingService
				.findShareByAbsolutePath(targetIrodsCollection);
		Assert.assertNull("should be no share", actual);
		} 
	
	@Test
	public void testDeleteShareCollectionNoShare() throws Exception {
		String testDirName = "testDeleteShareCollectionNoShare";
		String targetIrodsCollection = testingPropertiesHelper
				.buildIRODSCollectionAbsolutePathFromTestProperties(
						testingProperties, IRODS_TEST_SUBDIR_PATH + '/'
								+ testDirName);

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem
				.getIRODSAccessObjectFactory();

		IRODSFile irodsFile = accessObjectFactory.getIRODSFileFactory(
				irodsAccount).instanceIRODSFile(targetIrodsCollection);
		irodsFile.mkdirs();

		IRODSSharingService irodsSharingService = new IRODSSharingServiceImpl(
				accessObjectFactory, irodsAccount);

		// now delete
		
		irodsSharingService.removeShare(irodsFile.getAbsolutePath());
		// no error means ok
	}
	
	@Test(expected=FileNotFoundException.class)
	public void testDeleteShareCollectionNoFile() throws Exception {
		String testDirName = "testDeleteShareCollectionNoFile";
		String targetIrodsCollection = testingPropertiesHelper
				.buildIRODSCollectionAbsolutePathFromTestProperties(
						testingProperties, IRODS_TEST_SUBDIR_PATH + '/'
								+ testDirName);

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem
				.getIRODSAccessObjectFactory();

		IRODSFile irodsFile = accessObjectFactory.getIRODSFileFactory(
				irodsAccount).instanceIRODSFile(targetIrodsCollection);

		IRODSSharingService irodsSharingService = new IRODSSharingServiceImpl(
				accessObjectFactory, irodsAccount);

		// now delete
		
		irodsSharingService.removeShare(irodsFile.getAbsolutePath());
		// no error means ok
	}
	
	@Test
	public void testDeleteShareDataObject() throws Exception {
		String testFileName = "testDeleteShareDataObject.txt";

		String targetIrodsCollection = testingPropertiesHelper
				.buildIRODSCollectionAbsolutePathFromTestProperties(
						testingProperties, IRODS_TEST_SUBDIR_PATH);

		String targetIrodsDataObject = targetIrodsCollection + "/"
				+ testFileName;

		String absPath = scratchFileUtils
				.createAndReturnAbsoluteScratchPath(IRODS_TEST_SUBDIR_PATH);
		String fileNameOrig = FileGenerator.generateFileOfFixedLengthGivenName(
				absPath, testFileName, 2);

		IRODSAccount irodsAccount = testingPropertiesHelper
				.buildIRODSAccountFromTestProperties(testingProperties);
		IRODSAccessObjectFactory accessObjectFactory = irodsFileSystem
				.getIRODSAccessObjectFactory();

		IRODSFile targetIrodsFile = irodsFileSystem.getIRODSFileFactory(
				irodsAccount).instanceIRODSFile(targetIrodsCollection);
		targetIrodsFile.deleteWithForceOption();
		targetIrodsFile.mkdirs();
		DataTransferOperations dataTransferOperationsAO = irodsFileSystem
				.getIRODSAccessObjectFactory().getDataTransferOperations(
						irodsAccount);
		dataTransferOperationsAO.putOperation(new File(fileNameOrig),
				targetIrodsFile, null, null);

		IRODSSharingService irodsSharingService = new IRODSSharingServiceImpl(
				accessObjectFactory, irodsAccount);

		IRODSAccount secondaryAccount = testingPropertiesHelper
				.buildIRODSAccountFromSecondaryTestProperties(testingProperties);

		List<ShareUser> shareUsers = new ArrayList<ShareUser>();
		shareUsers.add(new ShareUser(secondaryAccount.getUserName(),
				secondaryAccount.getZone(), FilePermissionEnum.WRITE));
		IRODSSharedFileOrCollection irodsSharedFile = new IRODSSharedFileOrCollection(
				MetadataDomain.DATA, targetIrodsDataObject, testFileName,
				irodsAccount.getUserName(), irodsAccount.getZone(), shareUsers);
		irodsSharingService.createShare(irodsSharedFile);
		irodsSharingService.removeShare(irodsSharedFile.getDomainUniqueName());
		
		// need a test here to verify the share and access
		IRODSSharedFileOrCollection actual = irodsSharingService
				.findShareByAbsolutePath(targetIrodsDataObject);
		Assert.assertNull("should be no share", actual);
		
	}
		
}
