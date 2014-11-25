package org.openmrs.module.shr.pidfeed.configuration;

import org.marc.everest.formatters.FormatterUtil;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;

/**
 * Configuration for the OnDemandDocument module
 */
public final class PatientIdentityFeedConfiguration {
	
	// Singleton objects
	private static final Object s_lockObject = new Object();
	private static PatientIdentityFeedConfiguration s_instance;
	
	// Configuration constants
    public static final String PROP_LLP_PORT = "hl7.llp.port";
    public static final String PROP_LLP_TLS = "hl7.llp.tls";
    public static final String PROP_USER_NAME = "hl7.llp.user";
    public static final String PROP_USER_PASS = "hl7.llp.pass";
	public static final String PROP_ECID_ROOT = "shr.id.ecidRoot";
	
    // Id regex
    private Integer m_repositoryPort = 9100;
    private Boolean m_tls = false;
    private String m_user = "admin";
    private String m_password = "password";
    private String m_defaultEcidRoot = "1.2.3.4.5.6.7.8";
    
	// Private Ctor
	private PatientIdentityFeedConfiguration()
	{
		
	}

	/**
     * Creates or gets the instance of the configuration
     */
    public static final PatientIdentityFeedConfiguration getInstance()
    {
    	if(s_instance == null)
    		synchronized (s_lockObject) {
    			if(s_instance == null)
    			{
    				s_instance = new PatientIdentityFeedConfiguration();
    				s_instance.initialize();
    			}
            }
    	return s_instance;
    }

	/**
     * Read a global property
     */
    private <T> T getOrCreateGlobalProperty(String propertyName, T defaultValue)
    {
		String propertyValue = Context.getAdministrationService().getGlobalProperty(propertyName);
		if(propertyValue != null && !propertyValue.isEmpty())
			return (T)FormatterUtil.fromWireFormat(propertyValue, defaultValue.getClass());
		else
		{
			Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(propertyName, defaultValue.toString()));
			return defaultValue;
		}
    }    
    
    /**
     * Initialize the singleton
     */
    private void initialize()
    {
    	this.m_repositoryPort = this.getOrCreateGlobalProperty(PROP_LLP_PORT, this.m_repositoryPort);
    	this.m_tls = this.getOrCreateGlobalProperty(PROP_LLP_TLS, this.m_tls);
    	this.m_user = this.getOrCreateGlobalProperty(PROP_USER_NAME, this.m_user);
    	this.m_password = this.getOrCreateGlobalProperty(PROP_USER_PASS, this.m_password);
    }

    /**
     * Get the port to listen on
     * Auto generated method comment
     * 
     * @return
     */
    public Integer getPort() { 
    	return this.m_repositoryPort;
    }

    /**
     * True if TLS should be used
     */
	public Boolean getTls() {
		return this.m_tls;
    }

	/**
	 * Get user name of the user with which data should be import
	 * @return
	 */
	public String getUserName() {
		return this.m_user;
	}

	/**
	 * Get the password of the user
	 * @return
	 */
	public String getUserPassword() {
		return this.m_password;
	}

	/**
	 * Get the ecid root
	 * @return
	 */
	public String getEcidRoot() {
		return this.getOrCreateGlobalProperty(PROP_ECID_ROOT, this.m_defaultEcidRoot);
	}
}
