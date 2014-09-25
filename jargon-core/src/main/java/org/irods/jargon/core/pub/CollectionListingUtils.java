/**
 *
 */
package org.irods.jargon.core.pub;

import java.util.ArrayList;
import java.util.List;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.exception.DataNotFoundException;
import org.irods.jargon.core.exception.FileDriverError;
import org.irods.jargon.core.exception.FileNotFoundException;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.packinstr.DataObjInpForObjStat;
import org.irods.jargon.core.packinstr.DataObjInpForQuerySpecColl;
import org.irods.jargon.core.packinstr.SpecColInfo;
import org.irods.jargon.core.packinstr.Tag;
import org.irods.jargon.core.pub.aohelper.CollectionAOHelper;
import org.irods.jargon.core.pub.domain.ObjStat;
import org.irods.jargon.core.pub.domain.ObjStat.SpecColType;
import org.irods.jargon.core.pub.io.IRODSFileSystemAOHelper;
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry;
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry.ObjectType;
import org.irods.jargon.core.query.GenQueryBuilderException;
import org.irods.jargon.core.query.GenQueryField.SelectFieldTypes;
import org.irods.jargon.core.query.IRODSGenQueryBuilder;
import org.irods.jargon.core.query.IRODSGenQueryFromBuilder;
import org.irods.jargon.core.query.IRODSQueryResultRow;
import org.irods.jargon.core.query.IRODSQueryResultSet;
import org.irods.jargon.core.query.JargonQueryException;
import org.irods.jargon.core.query.QueryConditionOperators;
import org.irods.jargon.core.query.QueryResultProcessingUtils;
import org.irods.jargon.core.query.RodsGenQueryEnum;
import org.irods.jargon.core.utils.CollectionAndPath;
import org.irods.jargon.core.utils.IRODSDataConversionUtil;
import org.irods.jargon.core.utils.MiscIRODSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic utils (for the package) to do collection listings
 * 
 * @author Mike Conway - DICE (www.irods.org)
 * 
 */
class CollectionListingUtils {

	private final IRODSAccount irodsAccount;
	private final IRODSAccessObjectFactory irodsAccessObjectFactory;
	public static final String QUERY_EXCEPTION_FOR_QUERY = "query exception for  query:";

	static final Logger log = LoggerFactory
			.getLogger(CollectionListingUtils.class);

	/**
	 * This is a compensating method used to deal with the top of the tree when
	 * permissions do not allow listing 'into' the tree to get to things the
	 * user actually has access to.
	 * 
	 * @param absolutePathToParent
	 * @return
	 * @throws JargonException
	 */
	List<CollectionAndDataObjectListingEntry> handleNoListingUnderRootOrHomeByLookingForPublicAndHome(
			final String absolutePathToParent) throws FileNotFoundException,
			JargonException {

		log.info("handleNoListingUnderRootOrHomeByLookingForPublicAndHome()");

		String path = absolutePathToParent;
		List<CollectionAndDataObjectListingEntry> collectionAndDataObjectListingEntries = new ArrayList<CollectionAndDataObjectListingEntry>();

		/*
		 * This is somewhat convoluted, note the return statements in the
		 * various conditions
		 */
		if (!irodsAccessObjectFactory.getJargonProperties()
				.isDefaultToPublicIfNothingUnderRootWhenListing()) {
			log.info("not configured in jargon.properties to look for public and user home, throw the FileNotFoundException");
			throw new FileNotFoundException("the collection cannot be found");
		}

		// check if under '/' and infer that there is a '/zone' subdir to return
		// this time
		if (path.equals("/")) {
			collectionAndDataObjectListingEntries
					.add(createStandInForZoneDir());
			return collectionAndDataObjectListingEntries;
		}

		// check if under '/zone' and if so infer that there is a home dir
		StringBuilder sb = new StringBuilder();
		sb.append("/");
		sb.append(irodsAccount.getZone());

		String comparePath = sb.toString();

		if (path.equals(comparePath)) {
			log.info("under zone, create stand-in home dir");
			collectionAndDataObjectListingEntries
					.add(createStandInForHomeDir());
			return collectionAndDataObjectListingEntries;
		}

		/*
		 * check if I am under /zone/home, look for public and user dir. In this
		 * situation I should be able to list them via obj stat
		 */
		sb = new StringBuilder();
		sb.append("/");
		sb.append(irodsAccount.getZone());
		sb.append("/home");

		comparePath = sb.toString();

		if (path.equals(comparePath)) {
			log.info("under home, look for public and home dir");
			sb.append("/public");
			ObjStat statForPublic;
			try {
				statForPublic = retrieveObjectStatForPath(sb.toString());
				collectionAndDataObjectListingEntries
						.add(createStandInForPublicDir(statForPublic));
			} catch (FileNotFoundException fnf) {
				log.info("no public dir");
			}

			log.info("see if a user home dir applies");

			ObjStat statForUserHome;
			try {
				statForUserHome = retrieveObjectStatForPath(MiscIRODSUtils
						.computeHomeDirectoryForIRODSAccount(irodsAccount));
				collectionAndDataObjectListingEntries
						.add(createStandInForUserDir(statForUserHome));
			} catch (FileNotFoundException fnf) {
				log.info("no home dir");
			}

		} else {

			sb.append("/");
			sb.append(IRODSAccount.PUBLIC_USERNAME);

			comparePath = sb.toString();

			if (path.equals(comparePath)) {

				log.info("see if a user home dir applies");

				ObjStat statForUserHome;
				try {
					statForUserHome = retrieveObjectStatForPath(comparePath);
					collectionAndDataObjectListingEntries
							.add(createStandInForUserDir(statForUserHome));
				} catch (FileNotFoundException fnf) {
					log.info("no home dir");
				}

			} else {
				log.info("really is a not found");
				throw new FileNotFoundException(
						"unable to find file under path");
			}
		}
		// I was under /zone/home/ looking for public and user dirs, return what
		// I have, it could be empty
		return collectionAndDataObjectListingEntries;

	}

	private CollectionAndDataObjectListingEntry createStandInForZoneDir() {
		log.info("under root, put out zone as an entry");
		CollectionAndDataObjectListingEntry entry = new CollectionAndDataObjectListingEntry();
		entry.setCount(0);
		entry.setLastResult(true);
		entry.setObjectType(ObjectType.COLLECTION);
		entry.setOwnerZone(irodsAccount.getZone());
		StringBuilder sb = new StringBuilder();
		sb.append("/");
		entry.setParentPath(sb.toString());
		sb.append(irodsAccount.getZone());
		entry.setPathOrName(sb.toString());
		entry.setSpecColType(SpecColType.NORMAL);
		return entry;
	}

	/**
	 * @param collectionAndDataObjectListingEntries
	 */
	private CollectionAndDataObjectListingEntry createStandInForHomeDir() {
		log.info("under root, put out home as an entry");
		CollectionAndDataObjectListingEntry entry = new CollectionAndDataObjectListingEntry();
		entry.setCount(0);
		entry.setLastResult(true);
		entry.setObjectType(ObjectType.COLLECTION);
		entry.setOwnerZone(irodsAccount.getZone());
		entry.setParentPath("/");
		StringBuilder sb = new StringBuilder();
		sb.append("/");
		sb.append(irodsAccount.getZone());
		sb.append("/home");
		entry.setPathOrName(sb.toString());
		entry.setSpecColType(SpecColType.NORMAL);
		return entry;
	}

	/**
	 * Create a collection and listing entry for the home dir
	 * 
	 * @return
	 */
	private CollectionAndDataObjectListingEntry createStandInForPublicDir(
			final ObjStat objStat) {
		log.info("under root, put out home as an entry");
		CollectionAndDataObjectListingEntry entry = new CollectionAndDataObjectListingEntry();
		entry.setCount(0);
		entry.setLastResult(true);
		entry.setOwnerZone(irodsAccount.getZone());
		entry.setOwnerName(objStat.getOwnerName());
		StringBuilder sb = new StringBuilder();
		sb.append("/");
		sb.append(irodsAccount.getZone());
		sb.append("/home");
		entry.setParentPath(sb.toString());
		sb.append("/public");
		entry.setPathOrName(sb.toString());
		entry.setSpecColType(objStat.getSpecColType());
		entry.setCreatedAt(objStat.getCreatedAt());
		entry.setId(objStat.getDataId());
		entry.setObjectType(objStat.getObjectType());
		return entry;
	}

	/**
	 * Create a collection and listing entry for the home dir
	 * 
	 * @return
	 */
	private CollectionAndDataObjectListingEntry createStandInForUserDir(
			final ObjStat objStat) {
		log.info("put a user dir entry out there");
		CollectionAndDataObjectListingEntry entry = new CollectionAndDataObjectListingEntry();
		entry.setCount(0);
		entry.setLastResult(true);
		entry.setObjectType(ObjectType.COLLECTION);
		entry.setOwnerZone(irodsAccount.getZone());
		StringBuilder sb = new StringBuilder();
		sb.append("/");
		sb.append(irodsAccount.getZone());
		sb.append("/home");
		entry.setParentPath(sb.toString());
		sb.append("/");
		sb.append(irodsAccount.getUserName());
		entry.setPathOrName(sb.toString());
		entry.setSpecColType(objStat.getSpecColType());
		entry.setCreatedAt(objStat.getCreatedAt());
		entry.setId(objStat.getDataId());
		entry.setObjectType(objStat.getObjectType());
		entry.setOwnerZone(irodsAccount.getZone());
		entry.setOwnerName(objStat.getOwnerName());
		return entry;
	}

	/**
	 * List the collections underneath the given path
	 * <p/>
	 * Works with soft links
	 * 
	 * @param objStat
	 *            {@link ObjStat} from iRODS that details the nature of the
	 *            collection
	 * @param partialStartIndex
	 * @return
	 * @throws FileNotFoundException
	 * @throws JargonException
	 * 
	 * 
	 */
	List<CollectionAndDataObjectListingEntry> listCollectionsUnderPath(
			final ObjStat objStat, final int partialStartIndex)
			throws FileNotFoundException, JargonException {

		log.info("listCollectionsUnderPath()");

		if (objStat == null) {
			throw new IllegalArgumentException("objStat is null");
		}

		/*
		 * See if jargon supports the given object type
		 */
		MiscIRODSUtils.evaluateSpecCollSupport(objStat);

		/*
		 * Special collections are processed in different ways.
		 * 
		 * Listing for soft links substitutes the source path for the target
		 * path in the query
		 */
		String effectiveAbsolutePath = MiscIRODSUtils
				.determineAbsolutePathBasedOnCollTypeInObjectStat(objStat);

		if (objStat.getSpecColType() == SpecColType.STRUCT_FILE_COLL
				|| objStat.getSpecColType() == SpecColType.MOUNTED_COLL) {
			return listUnderPathWhenSpecColl(objStat, effectiveAbsolutePath,
					true);
		} else {
			return listCollectionsUnderPathViaGenQuery(objStat,
					partialStartIndex, effectiveAbsolutePath);
		}

	}

	private List<CollectionAndDataObjectListingEntry> listUnderPathWhenSpecColl(
			final ObjStat objStat, final String effectiveAbsolutePath,
			final boolean isCollection) throws JargonException {

		log.info("listCollectionsUnderPathWhenSpecColl()");

		List<CollectionAndDataObjectListingEntry> entries = new ArrayList<CollectionAndDataObjectListingEntry>();

		SpecColInfo specColInfo = new SpecColInfo();
		specColInfo.setCacheDir(objStat.getCacheDir());

		if (objStat.isCacheDirty()) {
			specColInfo.setCacheDirty(1);
		}

		specColInfo.setCollClass(1);
		specColInfo.setCollection(objStat.getAbsolutePath());
		specColInfo.setObjPath(objStat.getObjectPath());
		specColInfo.setPhyPath(objStat.getObjectPath());
		specColInfo.setReplNum(objStat.getReplNumber());
		specColInfo.setType(2);

		if (irodsAccessObjectFactory.getIRODSServerProperties(irodsAccount)
				.isEirods()) {
			specColInfo.setUseResourceHierarchy(true);
		}

		DataObjInpForQuerySpecColl dataObjInp = null;

		if (isCollection) {
			dataObjInp = DataObjInpForQuerySpecColl.instanceQueryCollections(
					effectiveAbsolutePath, specColInfo);
		} else {
			dataObjInp = DataObjInpForQuerySpecColl.instanceQueryDataObj(
					effectiveAbsolutePath, specColInfo);
		}
		Tag response;

		try {
			response = irodsAccessObjectFactory.getIrodsSession()
					.currentConnection(irodsAccount).irodsFunction(dataObjInp);

			log.debug("response from function: {}", response.parseTag());

			int totalRecords = response.getTag("totalRowCount").getIntValue();
			log.info("total records:{}", totalRecords);
			int continueInx = response.getTag("continueInx").getIntValue();

			List<IRODSQueryResultRow> results = QueryResultProcessingUtils
					.translateResponseIntoResultSet(response,
							new ArrayList<String>(), 0, 0);

			int ctr = 1;
			CollectionAndDataObjectListingEntry entry = null;
			for (IRODSQueryResultRow row : results) {
				entry = createListingEntryFromResultRow(objStat, row,
						isCollection);
				entry.setCount(ctr++);
				entry.setLastResult(continueInx <= 0);
				entries.add(entry);
			}

			while (continueInx > 0) {
				dataObjInp = DataObjInpForQuerySpecColl
						.instanceQueryCollections(effectiveAbsolutePath,
								specColInfo, continueInx);

				if (isCollection) {
					dataObjInp = DataObjInpForQuerySpecColl
							.instanceQueryCollections(effectiveAbsolutePath,
									specColInfo, continueInx);
				} else {
					dataObjInp = DataObjInpForQuerySpecColl
							.instanceQueryDataObj(effectiveAbsolutePath,
									specColInfo, continueInx);
				}

				response = irodsAccessObjectFactory.getIrodsSession()
						.currentConnection(irodsAccount)
						.irodsFunction(dataObjInp);

				log.debug("response from function: {}", response.parseTag());

				totalRecords = response.getTag("totalRowCount").getIntValue();
				log.info("total records:{}", totalRecords);
				continueInx = response.getTag("continueInx").getIntValue();

				results = QueryResultProcessingUtils
						.translateResponseIntoResultSet(response,
								new ArrayList<String>(), 0, entries.size());
				for (IRODSQueryResultRow row : results) {
					entry = createListingEntryFromResultRow(objStat, row,
							isCollection);
					entry.setCount(ctr++);
					entry.setLastResult(continueInx <= 0);
					entries.add(entry);
				}
			}

			return entries;

		} catch (FileDriverError fde) {
			log.warn("file driver error listing empty spec coll is ignored, just act as no data found");
			return new ArrayList<CollectionAndDataObjectListingEntry>();
		} catch (DataNotFoundException dnf) {
			log.info("end of data");
		}

		return entries;

	}

	private CollectionAndDataObjectListingEntry createListingEntryFromResultRow(
			final ObjStat objStat, final IRODSQueryResultRow row,
			final boolean isCollection) throws JargonException {
		CollectionAndDataObjectListingEntry listingEntry;
		CollectionAndPath collectionAndPath;
		listingEntry = new CollectionAndDataObjectListingEntry();
		listingEntry.setCreatedAt(IRODSDataConversionUtil
				.getDateFromIRODSValue(row.getColumn(2)));
		listingEntry.setDataSize(IRODSDataConversionUtil
				.getLongOrZeroFromIRODSValue(row.getColumn(4)));
		listingEntry.setModifiedAt(IRODSDataConversionUtil
				.getDateFromIRODSValue(row.getColumn(3)));
		row.getColumn(1);

		if (isCollection) {
			listingEntry.setObjectType(ObjectType.COLLECTION);
		} else {
			listingEntry.setObjectType(ObjectType.DATA_OBJECT);
		}

		listingEntry.setOwnerName(objStat.getOwnerName());
		listingEntry.setOwnerZone(objStat.getOwnerZone());

		if (isCollection) {

			collectionAndPath = MiscIRODSUtils
					.separateCollectionAndPathFromGivenAbsolutePath(row
							.getColumn(0));
			listingEntry.setParentPath(collectionAndPath.getCollectionParent());
			listingEntry.setPathOrName(row.getColumn(0));

		} else {
			listingEntry.setParentPath(row.getColumn(0));
			listingEntry.setPathOrName(row.getColumn(1));
		}
		listingEntry.setSpecColType(objStat.getSpecColType());
		return listingEntry;
	}

	private List<CollectionAndDataObjectListingEntry> listCollectionsUnderPathViaGenQuery(
			final ObjStat objStat, final int partialStartIndex,
			final String effectiveAbsolutePath) throws JargonException {

		List<CollectionAndDataObjectListingEntry> subdirs;

		IRODSGenQueryBuilder builder = new IRODSGenQueryBuilder(true, false,
				true, null);
		try {
			IRODSFileSystemAOHelper.buildQueryListAllCollections(
					effectiveAbsolutePath, builder);
		} catch (GenQueryBuilderException e) {
			log.error("query builder exception", e);
			throw new JargonException("error building query", e);
		}

		IRODSQueryResultSet resultSet = queryForPathAndReturnResultSet(
				objStat.getAbsolutePath(), builder, partialStartIndex, objStat);

		subdirs = new ArrayList<CollectionAndDataObjectListingEntry>(resultSet
				.getResults().size());
		CollectionAndDataObjectListingEntry collectionAndDataObjectListingEntry = null;

		for (IRODSQueryResultRow row : resultSet.getResults()) {
			collectionAndDataObjectListingEntry = CollectionAOHelper
					.buildCollectionListEntryFromResultSetRowForCollectionQuery(
							row, resultSet.getTotalRecords());

			adjustEntryFromRowInCaseOfSpecialCollection(objStat,
					effectiveAbsolutePath, collectionAndDataObjectListingEntry);

			/*
			 * for some reason, a query for collections with a parent of '/'
			 * returns the root as a result, which creates weird situations when
			 * trying to show collections in a tree structure. This test papers
			 * over that idiosyncrasy and discards that extraneous result.
			 */
			if (!collectionAndDataObjectListingEntry.getPathOrName()
					.equals("/")) {
				subdirs.add(collectionAndDataObjectListingEntry);
			}
		}

		return subdirs;
	}

	IRODSQueryResultSet queryForPathAndReturnResultSet(
			final String absolutePath, final IRODSGenQueryBuilder builder,
			final int partialStartIndex, final ObjStat objStat)
			throws JargonException {

		log.info("queryForPathAndReturnResultSet for: {}", absolutePath);
		IRODSGenQueryExecutor irodsGenQueryExecutor = irodsAccessObjectFactory
				.getIRODSGenQueryExecutor(irodsAccount);

		IRODSGenQueryFromBuilder irodsQuery;
		IRODSQueryResultSet resultSet;

		try {
			irodsQuery = builder
					.exportIRODSQueryFromBuilder(irodsAccessObjectFactory
							.getJargonProperties().getMaxFilesAndDirsQueryMax());
			resultSet = irodsGenQueryExecutor
					.executeIRODSQueryWithPagingInZone(irodsQuery,
							partialStartIndex,
							MiscIRODSUtils.getZoneInPath(absolutePath));
		} catch (JargonQueryException e) {
			log.error(QUERY_EXCEPTION_FOR_QUERY, e);
			throw new JargonException(e);
		} catch (GenQueryBuilderException e) {
			log.error(QUERY_EXCEPTION_FOR_QUERY, e);
			throw new JargonException(e);
		}

		return resultSet;
	}

	/**
	 * For a collection based on a row from a collection query, evaluate against
	 * the provided objStat and decide whether to modify the resulting listing
	 * entry to reflect special collection status
	 * 
	 * @param objStat
	 * @param effectiveAbsolutePath
	 * @param collectionAndDataObjectListingEntry
	 */
	private void adjustEntryFromRowInCaseOfSpecialCollection(
			final ObjStat objStat,
			final String effectiveAbsolutePath,
			final CollectionAndDataObjectListingEntry collectionAndDataObjectListingEntry) {
		if (objStat.getSpecColType() == SpecColType.LINKED_COLL) {
			log.info("adjusting paths in entry to reflect linked collection info");
			StringBuilder sb = new StringBuilder();
			sb.append(objStat.getObjectPath());
			sb.append('/');
			sb.append(MiscIRODSUtils
					.getLastPathComponentForGiveAbsolutePath(collectionAndDataObjectListingEntry
							.getPathOrName()));
			collectionAndDataObjectListingEntry.setSpecialObjectPath(sb
					.toString());

			sb = new StringBuilder();
			sb.append(objStat.getAbsolutePath());
			sb.append('/');
			sb.append(MiscIRODSUtils
					.getLastPathComponentForGiveAbsolutePath(collectionAndDataObjectListingEntry
							.getPathOrName()));

			collectionAndDataObjectListingEntry.setPathOrName(sb.toString());

			collectionAndDataObjectListingEntry.setParentPath(objStat
					.getAbsolutePath());
			collectionAndDataObjectListingEntry
					.setSpecColType(SpecColType.LINKED_COLL);
		}
	}

	/**
	 * List the data objects underneath the given path given an already obtained
	 * <code>ObjStat</code>
	 * 
	 * @param objStat
	 * @param partialStartIndex
	 * @return
	 * @throws JargonException
	 */
	List<CollectionAndDataObjectListingEntry> listDataObjectsUnderPath(
			final ObjStat objStat, final int partialStartIndex)
			throws JargonException {

		log.info("listDataObjectsUnderPath(objStat, partialStartIndex)");

		if (objStat == null) {
			throw new IllegalArgumentException(
					"collectionAndDataObjectListingEntry is null");
		}

		String effectiveAbsolutePath = MiscIRODSUtils
				.determineAbsolutePathBasedOnCollTypeInObjectStat(objStat);
		log.info("determined effectiveAbsolutePathToBe:{}",
				effectiveAbsolutePath);

		log.info("listDataObjectsUnderPath for: {}", objStat);

		List<CollectionAndDataObjectListingEntry> files;
		if (objStat.getSpecColType() == SpecColType.STRUCT_FILE_COLL
				|| objStat.getSpecColType() == SpecColType.MOUNTED_COLL) {

			files = listUnderPathWhenSpecColl(objStat, effectiveAbsolutePath,
					false);
		} else {

			files = listDataObjectsUnderPathViaGenQuery(objStat,
					partialStartIndex, effectiveAbsolutePath);
		}

		return files;

	}

	private List<CollectionAndDataObjectListingEntry> listDataObjectsUnderPathViaGenQuery(
			final ObjStat objStat, final int partialStartIndex,
			final String effectiveAbsolutePath) throws JargonException {
		IRODSGenQueryBuilder builder = new IRODSGenQueryBuilder(true, false,
				true, null);

		IRODSFileSystemAOHelper
				.buildQueryListAllDataObjectsWithSizeAndDateInfo(
						effectiveAbsolutePath, builder);
		IRODSQueryResultSet resultSet;

		try {
			resultSet = queryForPathAndReturnResultSet(effectiveAbsolutePath,
					builder, partialStartIndex, objStat);
		} catch (JargonException e) {
			log.error("exception querying for data objects:{}", builder, e);
			throw new JargonException("error in query", e);
		}

		List<CollectionAndDataObjectListingEntry> files = new ArrayList<CollectionAndDataObjectListingEntry>(
				resultSet.getResults().size());

		/*
		 * the query that gives the necessary data will cause duplication when
		 * there are replicas, so discard duplicates. This is the nature of
		 * GenQuery.
		 */
		String lastPath = "";
		String currentPath = "";
		CollectionAndDataObjectListingEntry entry;
		for (IRODSQueryResultRow row : resultSet.getResults()) {
			entry = CollectionAOHelper
					.buildCollectionListEntryFromResultSetRowForDataObjectQuery(
							row, resultSet.getTotalRecords());

			/**
			 * Use the data in the objStat, in the case of special collections,
			 * to augment the data returned
			 */
			augmentCollectionEntryForSpecialCollections(objStat,
					effectiveAbsolutePath, entry);

			StringBuilder sb = new StringBuilder();
			sb.append(entry.getParentPath());
			sb.append('/');
			sb.append(entry.getPathOrName());
			currentPath = sb.toString();
			if (currentPath.equals(lastPath)) {
				continue;
			}

			lastPath = currentPath;
			files.add(entry);
		}
		return files;
	}

	/**
	 * Use the data in the objStat, in the case of special collections, to
	 * augment the entry for a collection
	 * 
	 * @param objStat
	 *            {@link ObjStat} retreived for the parent directory
	 * @param effectiveAbsolutePath
	 *            <code>String</code> with the path used to query, this will be
	 *            the canonical path for the parent collection, and should
	 *            correspond to the absolute path information in the given
	 *            <code>entry</code>.
	 * @param entry
	 *            {@link CollectionAndDataObjectListingEntry} which is the raw
	 *            data returned from querying the iCat based on the
	 *            <code>effectiveAbsolutePath</code>. This information is from
	 *            the perspective of the canonical path, and the given method
	 *            will reframe the <code>entry</code> from the perspective of
	 *            the requested path This means that a query on children of a
	 *            soft link carry the data from the perspective of the soft
	 *            linked directory, even though the iCAT carries the information
	 *            based on the 'source path' of the soft link. This gets pretty
	 *            confusing otherwise.
	 */
	void augmentCollectionEntryForSpecialCollections(final ObjStat objStat,
			final String effectiveAbsolutePath,
			final CollectionAndDataObjectListingEntry entry) {

		if (objStat.getSpecColType() == SpecColType.LINKED_COLL) {
			log.info("adjusting paths in entry to reflect linked collection info");
			entry.setSpecialObjectPath(objStat.getObjectPath());
			CollectionAndPath collectionAndPathForAbsPath = MiscIRODSUtils
					.separateCollectionAndPathFromGivenAbsolutePath(entry
							.getPathOrName());

			if (entry.isCollection()) {
				entry.setPathOrName(objStat.getAbsolutePath() + "/"
						+ collectionAndPathForAbsPath.getChildName());
				entry.setParentPath(objStat.getAbsolutePath());
			} else {
				entry.setParentPath(objStat.getAbsolutePath());
			}

		}

	}

	int countDataObjectsUnderPath(final ObjStat objStat)
			throws FileNotFoundException, JargonException {

		log.info("countDataObjectsUnderPath()");

		if (objStat == null) {
			throw new IllegalArgumentException("objStat is null");
		}

		/*
		 * See if jargon supports the given object type
		 */
		MiscIRODSUtils.evaluateSpecCollSupport(objStat);

		String effectiveAbsolutePath = MiscIRODSUtils
				.determineAbsolutePathBasedOnCollTypeInObjectStat(objStat);
		log.info("determined effectiveAbsolutePathToBe:{}",
				effectiveAbsolutePath);

		// I cannot get children if this is not a directory (a file has no
		// children)
		if (!objStat.isSomeTypeOfCollection()) {
			log.error(
					"this is a file, not a directory, and therefore I cannot get a count of the children: {}",
					objStat.getAbsolutePath());
			throw new JargonException(
					"attempting to count children under a file at path:"
							+ objStat.getAbsolutePath());
		}

		return queryDataObjectCountsUnderPath(effectiveAbsolutePath);

	}

	/**
	 * Given an objStat, get the count of collections under the path
	 * 
	 * @param objStat
	 *            {@link ObjStat}
	 * @return <code>int</code> with the total collections under a given path
	 * @throws FileNotFoundException
	 * @throws JargonException
	 */
	int countCollectionsUnderPath(final ObjStat objStat)
			throws FileNotFoundException, JargonException {

		log.info("countCollectionsUnderPath()");

		if (objStat == null) {
			throw new IllegalArgumentException("objStat is null");
		}

		/*
		 * See if jargon supports the given object type
		 */
		MiscIRODSUtils.evaluateSpecCollSupport(objStat);

		String effectiveAbsolutePath = MiscIRODSUtils
				.determineAbsolutePathBasedOnCollTypeInObjectStat(objStat);
		log.info("determined effectiveAbsolutePathToBe:{}",
				effectiveAbsolutePath);

		// I cannot get children if this is not a directory (a file has no
		// children)
		if (!objStat.isSomeTypeOfCollection()) {
			log.error(
					"this is a file, not a directory, and therefore I cannot get a count of the children: {}",
					objStat.getAbsolutePath());
			throw new JargonException(
					"attempting to count children under a file at path:"
							+ objStat);
		}

		IRODSGenQueryExecutor irodsGenQueryExecutor = irodsAccessObjectFactory
				.getIRODSGenQueryExecutor(irodsAccount);

		IRODSGenQueryBuilder builder = new IRODSGenQueryBuilder(true, null);
		IRODSQueryResultSet resultSet;

		try {
			builder.addSelectAsAgregateGenQueryValue(
					RodsGenQueryEnum.COL_COLL_TYPE, SelectFieldTypes.COUNT)
					.addSelectAsAgregateGenQueryValue(
							RodsGenQueryEnum.COL_COLL_NAME,
							SelectFieldTypes.COUNT)
					.addConditionAsGenQueryField(
							RodsGenQueryEnum.COL_COLL_PARENT_NAME,
							QueryConditionOperators.EQUAL,
							effectiveAbsolutePath);

			IRODSGenQueryFromBuilder irodsQuery = builder
					.exportIRODSQueryFromBuilder(1);
			resultSet = irodsGenQueryExecutor
					.executeIRODSQueryAndCloseResultInZone(irodsQuery, 0,
							MiscIRODSUtils.getZoneInPath(effectiveAbsolutePath));
		} catch (JargonQueryException e) {
			log.error(CollectionListingUtils.QUERY_EXCEPTION_FOR_QUERY, e);
			throw new JargonException("error in exists query", e);
		} catch (GenQueryBuilderException e) {
			log.error(CollectionListingUtils.QUERY_EXCEPTION_FOR_QUERY, e);
			throw new JargonException("error in exists query", e);
		}

		int collCtr = 0;
		if (resultSet.getResults().size() > 0) {
			collCtr = IRODSDataConversionUtil
					.getIntOrZeroFromIRODSValue(resultSet.getFirstResult()
							.getColumn(0));
		}

		return collCtr;

	}

	int queryDataObjectCountsUnderPath(final String effectiveAbsolutePath)
			throws JargonException, DataNotFoundException {
		IRODSGenQueryExecutor irodsGenQueryExecutor = irodsAccessObjectFactory
				.getIRODSGenQueryExecutor(irodsAccount);

		IRODSGenQueryBuilder builder = new IRODSGenQueryBuilder(true, null);
		IRODSQueryResultSet resultSet;

		try {
			builder.addSelectAsAgregateGenQueryValue(
					RodsGenQueryEnum.COL_COLL_NAME, SelectFieldTypes.COUNT)
					.addSelectAsAgregateGenQueryValue(
							RodsGenQueryEnum.COL_DATA_NAME,
							SelectFieldTypes.COUNT)
					.addConditionAsGenQueryField(
							RodsGenQueryEnum.COL_COLL_NAME,
							QueryConditionOperators.EQUAL,
							effectiveAbsolutePath)
					.addConditionAsGenQueryField(
							RodsGenQueryEnum.COL_DATA_REPL_NUM,
							QueryConditionOperators.EQUAL, "0");

			;
			IRODSGenQueryFromBuilder irodsQuery = builder
					.exportIRODSQueryFromBuilder(1);

			resultSet = irodsGenQueryExecutor
					.executeIRODSQueryAndCloseResultInZone(irodsQuery, 0,
							MiscIRODSUtils.getZoneInPath(effectiveAbsolutePath));
		} catch (JargonQueryException e) {
			log.error(CollectionListingUtils.QUERY_EXCEPTION_FOR_QUERY, e);
			throw new JargonException("error in exists query", e);
		} catch (GenQueryBuilderException e) {
			log.error(CollectionListingUtils.QUERY_EXCEPTION_FOR_QUERY, e);
			throw new JargonException("error in exists query", e);
		}

		int fileCtr = 0;

		if (resultSet.getResults().size() > 0) {
			fileCtr = IRODSDataConversionUtil
					.getIntOrZeroFromIRODSValue(resultSet.getFirstResult()
							.getColumn(0));
		}

		return fileCtr;
	}

	/**
	 * @param irodsAccount
	 * @param irodsAccessObjectFactory
	 * @throws JargonException
	 */
	CollectionListingUtils(IRODSAccount irodsAccount,
			IRODSAccessObjectFactory irodsAccessObjectFactory)
			throws JargonException {
		super();

		if (irodsAccount == null) {
			throw new IllegalArgumentException("null irodsAccount");
		}

		if (irodsAccessObjectFactory == null) {
			throw new IllegalArgumentException("null irodsAccessObjectFactory");
		}

		this.irodsAccount = irodsAccount;
		this.irodsAccessObjectFactory = irodsAccessObjectFactory;

	}

	/**
	 * Retrieve an iRODS ObjStat object for the given iRODS path
	 * 
	 * @param irodsAbsolutePath
	 *            <code>String</code> with an absolute path to an irods object
	 * @return {@link ObjStat} from iRODS
	 * @throws FileNotFoundException
	 *             if the file does not exist
	 * @throws JargonException
	 */
	ObjStat retrieveObjectStatForPath(final String irodsAbsolutePath)
			throws FileNotFoundException, JargonException {

		if (irodsAbsolutePath == null || irodsAbsolutePath.isEmpty()) {
			throw new IllegalArgumentException(
					"irodsAbsolutePath is null or empty");
		}

		/*
		 * StopWatch stopWatch = null;
		 * 
		 * if (this.isInstrumented()) { stopWatch = new
		 * Log4JStopWatch("retrieveObjectStatForPath"); }
		 */

		MiscIRODSUtils.checkPathSizeForMax(irodsAbsolutePath);

		DataObjInpForObjStat dataObjInp = DataObjInpForObjStat
				.instance(irodsAbsolutePath);
		Tag response;
		try {
			response = irodsAccessObjectFactory.getIrodsSession()
					.currentConnection(irodsAccount).irodsFunction(dataObjInp);
		} catch (DataNotFoundException e) {
			log.info("rethrow DataNotFound as FileNotFound per contract");
			throw new FileNotFoundException(e);
		}

		log.debug("response from objStat: {}", response.parseTag());

		/**
		 * For spec cols - soft link - phyPath = parent canonical dir -objPath =
		 * canonical path
		 */
		ObjStat objStat = new ObjStat();
		objStat.setAbsolutePath(irodsAbsolutePath);
		objStat.setChecksum(response.getTag("chksum").getStringValue());
		objStat.setDataId(response.getTag("dataId").getIntValue());
		int objType = response.getTag("objType").getIntValue();
		objStat.setObjectType(ObjectType.values()[objType]);
		objStat.setObjSize(response.getTag("objSize").getLongValue());
		objStat.setOwnerName(response.getTag("ownerName").getStringValue());
		objStat.setOwnerZone(response.getTag("ownerZone").getStringValue());
		objStat.setSpecColType(SpecColType.NORMAL);
		Tag specColl = response.getTag("SpecColl_PI");

		/*
		 * Look for the specColl tag (it is expected to be there) and see if
		 * there are any special collection types (e.g. mounted or soft links)
		 * to deal with
		 */
		if (specColl != null) {

			Tag tag = specColl.getTag("collection");

			if (tag != null) {
				objStat.setCollectionPath(tag.getStringValue());
			}

			tag = specColl.getTag("cacheDir");

			if (tag != null) {
				objStat.setCacheDir(tag.getStringValue());
			}

			tag = specColl.getTag("cacheDirty");

			if (tag != null) {
				objStat.setCacheDirty(tag.getStringValue().equals("1"));
			}

			int collClass = specColl.getTag("collClass").getIntValue();
			objStat.setReplNumber(specColl.getTag("replNum").getIntValue());

			switch (collClass) {
			case 0:
				objStat.setSpecColType(SpecColType.NORMAL);
				objStat.setObjectPath(specColl.getTag("phyPath")
						.getStringValue());
				break;
			case 1:
				objStat.setSpecColType(SpecColType.STRUCT_FILE_COLL);
				break;
			case 2:
				objStat.setSpecColType(SpecColType.MOUNTED_COLL);
				break;
			case 3:
				objStat.setSpecColType(SpecColType.LINKED_COLL);

				/*
				 * physical path will hold the canonical source dir where it was
				 * linked. The collection path will hold the top level of the
				 * soft link target. This does not 'follow' by incrementing the
				 * path as you descend into subdirs, so I use the collection
				 * path to chop off the absolute path, and use the remainder
				 * appended to the collection path to arrive at equivalent
				 * canonical source path fo rthis soft linked directory. This is
				 * all rather confusing, so instead of worrying about it, Jargon
				 * has the headache, you can just trust the objStat objectPath
				 * to point to the equivalent canonical source path to the soft
				 * linked path.
				 */
				String canonicalSourceDirForSoftLink = specColl.getTag(
						"phyPath").getStringValue();
				String softLinkTargetDir = specColl.getTag("collection")
						.getStringValue();
				if (softLinkTargetDir.length() > objStat.getAbsolutePath()
						.length()) {
					throw new JargonException(
							"cannot properly compute path for soft link");
				}

				String additionalPath = objStat.getAbsolutePath().substring(
						softLinkTargetDir.length());
				StringBuilder sb = new StringBuilder();
				sb.append(canonicalSourceDirForSoftLink);
				sb.append(additionalPath);
				objStat.setObjectPath(sb.toString());

				break;
			default:
				throw new JargonException("unknown special coll type:");
			}

		}

		String createdDate = response.getTag("createTime").getStringValue();
		String modifiedDate = response.getTag("modifyTime").getStringValue();
		objStat.setCreatedAt(IRODSDataConversionUtil
				.getDateFromIRODSValue(createdDate));
		objStat.setModifiedAt(IRODSDataConversionUtil
				.getDateFromIRODSValue(modifiedDate));

		log.info(objStat.toString());
		return objStat;

	}

}