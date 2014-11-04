/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.shr.pidfeed;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.Activator;
import org.openmrs.module.ModuleActivator;
import org.openmrs.module.shr.pidfeed.mllp.MllpCommunicationHost;

/**
 * This class contains the logic that is run every time this module is either started or shutdown
 */
public class PatientIdentityFeedActivator implements ModuleActivator {
	
	private Log log = LogFactory.getLog(this.getClass());
	private MllpCommunicationHost m_server = new MllpCommunicationHost();
	
	/**
	 * @see ModuleActivator#contextRefreshed()
	 */
	public void contextRefreshed() {
		log.info("SHR PID Feed Module refreshed");
	}
	
	/**
	 * @see ModuleActivator#started()
	 */
	public void started() {
		try {
	        this.m_server.open();
        }
        catch (IOException e) {
	        // TODO Auto-generated catch block
	        log.error("Error generated", e);
        }
		log.info("SHR PID Feed  Module started");
	}
	
	/**
	 * @see ModuleActivator#stopped()
	 */
	public void stopped() {
		try {
	        this.m_server.close();
        }
        catch (IOException e) {
	        // TODO Auto-generated catch block
	        log.error("Error generated", e);
        }
		log.info("SHR PID Feed  Module stopped");
	}
	
	/**
	 * @see ModuleActivator#willRefreshContext()
	 */
	public void willRefreshContext() {
		log.info("Refreshing SHR PID Feed  Module");
	}
	
	/**
	 * @see ModuleActivator#willStart()
	 */
	public void willStart() {
		log.info("Starting SHR PID Feed  Module");
	}
	
	/**
	 * @see ModuleActivator#willStop()
	 */
	public void willStop() {
		log.info("Stopping SHR PID Feed  Module");
	}
	
}
