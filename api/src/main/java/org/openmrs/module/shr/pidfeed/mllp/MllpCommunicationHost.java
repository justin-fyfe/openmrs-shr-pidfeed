package org.openmrs.module.shr.pidfeed.mllp;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.shr.pidfeed.configuration.PatientIdentityFeedConfiguration;
import org.openmrs.util.OpenmrsConstants;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.protocol.ReceivingApplication;
import ca.uhn.hl7v2.protocol.ReceivingApplicationException;
import ca.uhn.hl7v2.protocol.impl.AppRoutingDataImpl;
import ca.uhn.hl7v2.protocol.impl.ApplicationRouterImpl;
import ca.uhn.hl7v2.protocol.impl.HL7Server;
import ca.uhn.hl7v2.protocol.impl.NullSafeStorage;

/**
 * A basic MLLP communications host
 */
public class MllpCommunicationHost implements Closeable {
	
	private final PatientIdentityFeedConfiguration m_configuration = PatientIdentityFeedConfiguration.getInstance();
	private final Log log = LogFactory.getLog(MllpCommunicationHost.class);
	
	private HL7Server m_server = null;
	private ApplicationRouterImpl m_router = null;
	private ServerSocket m_socket = null;
	
	/**
	 * Receiving application
	 */
	private class OpenMrsLlpReceivingApplication implements ReceivingApplication {

		// Log
		protected final Log log = LogFactory.getLog(this.getClass());
		private PatientIdentityFeedConfiguration m_configuration = PatientIdentityFeedConfiguration.getInstance();
		
		// Process message
		public Message processMessage(Message arg0, Map map) throws ReceivingApplicationException, HL7Exception {
			try
			{
				if(log.isDebugEnabled())
				{
					log.debug("Received message:");
					for(Object key : map.keySet())
						log.debug(String.format("%s = %s", key, map.get(key)));
					
					log.debug(new PipeParser().encode(arg0));
				}

				String username = this.m_configuration.getUserName();
				String password = this.m_configuration.getUserPassword();
				
				Context.openSession();
				Context.authenticate(username, password);

				Message response = Context.getHL7Service().processHL7Message(arg0);

				if(log.isDebugEnabled())
					log.debug(new PipeParser().encode(response));

				return response;
			}
			catch(Exception e)
			{
				log.error(e);
				throw new ReceivingApplicationException(e);
			}
			finally
			{
				Context.closeSession();
			}
		}
		
		// Can process
		public boolean canProcess(Message arg0) {
			return true;
		}
	}
	/**
	 * Open the MLLP communications host
	 * @throws IOException 
	 */
	public void open() throws IOException
	{

		if(this.m_server != null)
			throw new IllegalStateException("Service already open!");

		this.m_router = new ApplicationRouterImpl();
		AppRoutingDataImpl routingData = new AppRoutingDataImpl("*", "*", "*", "*");
		// Bind to a simple hl7 service 
		this.m_router.bindApplication(routingData, new OpenMrsLlpReceivingApplication());
		
		
		log.info("Starting server on " + this.m_configuration.getPort().toString());
		this.m_socket = new ServerSocket(this.m_configuration.getPort());
		//socket.setSoTimeout(1000);
		
		this.m_server = new HL7Server(this.m_socket, this.m_router, new NullSafeStorage());
		
		this.m_server.start(null);
	}

	/**
	 * Close the MLLP communicatios host
	 */
	public void close() throws IOException {
		m_server.stop();
		this.m_server = null;
		this.m_socket.close();
		this.m_socket = null;
		this.m_router = null;
    }
	
}
