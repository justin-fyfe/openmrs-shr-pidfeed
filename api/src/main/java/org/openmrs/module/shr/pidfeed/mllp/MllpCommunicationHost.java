package org.openmrs.module.shr.pidfeed.mllp;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import org.openmrs.api.context.Context;
import org.openmrs.module.shr.pidfeed.configuration.PatientIdentityFeedConfiguration;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
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
	private HL7Server m_server = null;
	
	/**
	 * Open the MLLP communications host
	 * @throws IOException 
	 */
	public void open() throws IOException
	{

		if(this.m_server != null)
			throw new IllegalStateException("Service already open!");

		ApplicationRouterImpl router = new ApplicationRouterImpl();
		AppRoutingDataImpl routingData = new AppRoutingDataImpl("*", "*", "*", "*");
		// Bind to a simple hl7 service 
		router.bindApplication(routingData, new ReceivingApplication() {
			
			// Process message
			public Message processMessage(Message arg0, Map arg1) throws ReceivingApplicationException, HL7Exception {
				return Context.getHL7Service().processHL7Message(arg0);
			}
			
			// Can process
			public boolean canProcess(Message arg0) {
				return true;
			}
		});
		
		
		ServerSocket socket = new ServerSocket(this.m_configuration.getPort());
		this.m_server = new HL7Server(socket, router, new NullSafeStorage());
		this.m_server.start(null);
	}

	/**
	 * Close the MLLP communicatios host
	 */
	public void close() throws IOException {
		m_server.stop();
    }
	
}
