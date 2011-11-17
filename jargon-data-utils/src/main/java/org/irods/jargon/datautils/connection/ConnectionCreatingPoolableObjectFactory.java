package org.irods.jargon.datautils.connection;

import org.apache.commons.pool.PoolableObjectFactory;
import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.connection.IRODSCommands;
import org.irods.jargon.core.connection.IRODSProtocolManager;
import org.irods.jargon.core.connection.IRODSSession;
import org.irods.jargon.core.connection.IRODSSimpleProtocolManager;
import org.irods.jargon.core.connection.PipelineConfiguration;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.exception.JargonRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for a pool-able object that is an iRODS connection.  In the current implementation,
 * this is a pool of 1 iRODS connection (and <code>IRODSCommands</code> instance) that will
 * block waiting to get a handle.  This is intended for clients that are sharing a single connection to iRODS,
 * and specifically for clients sharing a temporary password connection.  
 * <p/>
 * In the future, more generalized pooling implementations may be developed, but for now, this is narrowly focused.
 * 
 * @author Mike Conway - DICE (www.irods.org)
 *
 */
public class ConnectionCreatingPoolableObjectFactory implements
		PoolableObjectFactory {
	
	private final IRODSAccount cachedIRODSAccount;
	private final IRODSProtocolManager irodsProtocolManager;
	private final PipelineConfiguration pipelineConfiguration;
	
	private Logger log = LoggerFactory
	.getLogger(ConnectionCreatingPoolableObjectFactory.class);

	/**
	 * Constructor will build a connection source based on the given <code>cachedIRODSAccount</code>
	 * and return an open connection on demand.
	 * 
	 * @param cachedIRODSAccount {@link IRODSAccount} that will describe the source of the connection
	 * to iRODS
	 */
	public ConnectionCreatingPoolableObjectFactory(final IRODSAccount cachedIRODSAccount) {
		if (cachedIRODSAccount == null) {
			throw new IllegalArgumentException("null cachedIRODSAccount");
		}
		this.cachedIRODSAccount = cachedIRODSAccount;
		log.info("caching iRODS account:{}", cachedIRODSAccount);
		irodsProtocolManager = IRODSSimpleProtocolManager.instance();
		try {
			IRODSSession irodsSession = IRODSSession.instance(irodsProtocolManager);
			pipelineConfiguration = irodsSession.buildPipelineConfigurationBasedOnJargonProperties();
		} catch (JargonException e) {
			throw new JargonRuntimeException("unable to create an iRODS session");
		}
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.pool.PoolableObjectFactory#activateObject(java.lang.Object)
	 */
	@Override
	public void activateObject(Object objectToActivate) throws Exception {
		log.info("activateObject:{}", objectToActivate);
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.pool.PoolableObjectFactory#destroyObject(java.lang.Object)
	 */
	@Override
	public void destroyObject(Object objectToDestroy) throws Exception {
		log.info("destroyObject:{}", objectToDestroy);
		if (!(objectToDestroy instanceof IRODSCommands)) {
			throw new UnsupportedOperationException("cannot destroy unknown object, expecting an IRODSCommands");
		}
		IRODSCommands irodsCommands = (IRODSCommands) objectToDestroy;
		log.info("disconnecting");
		irodsCommands.shutdown();
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.pool.PoolableObjectFactory#makeObject()
	 */
	@Override
	public Object makeObject() throws Exception {
		log.info("makeObject returns a new iRODS connection");
		return irodsProtocolManager.getIRODSProtocol(cachedIRODSAccount, pipelineConfiguration);
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.pool.PoolableObjectFactory#passivateObject(java.lang.Object)
	 */
	@Override
	public void passivateObject(Object arg0) throws Exception {
		log.info("passivateObject()");
		// nothing done here
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.pool.PoolableObjectFactory#validateObject(java.lang.Object)
	 */
	@Override
	public boolean validateObject(Object arg0) {
		return true;
	}

}