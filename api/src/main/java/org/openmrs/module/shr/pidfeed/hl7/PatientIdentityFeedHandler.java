package org.openmrs.module.shr.pidfeed.hl7;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.ImplementationId;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.api.PatientIdentifierException;
import org.openmrs.api.context.Context;
import org.openmrs.hl7.HL7Service;
import org.openmrs.module.rheashradapter.api.PatientMergeService;
import org.openmrs.module.shr.pidfeed.configuration.PatientIdentityFeedConfiguration;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.validator.PatientIdentifierValidator;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.app.Application;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v231.datatype.CX;
import ca.uhn.hl7v2.model.v231.datatype.TS;
import ca.uhn.hl7v2.model.v231.datatype.XPN;
import ca.uhn.hl7v2.model.v231.group.ADT_A40_PIDPD1MRGPV1;
import ca.uhn.hl7v2.model.v231.message.ACK;
import ca.uhn.hl7v2.model.v231.message.ADT_A01;
import ca.uhn.hl7v2.model.v231.message.ADT_A40;
import ca.uhn.hl7v2.model.v231.segment.PID;
import ca.uhn.hl7v2.util.Terser;

/**
 * PID Feed Handler which handles the PIF messages (ADT)
 */
public class PatientIdentityFeedHandler implements Application {
	
	private final Log log = LogFactory.getLog(this.getClass());
	
	private HL7Service m_hl7Service = null;
	private PatientMergeService m_mergeService = null;
	private PatientIdentityFeedConfiguration m_configuration = null;
	
	private static final List<String> m_handles = Arrays.asList("ADT^A01", "ADT^A02", "ADT^A04", "ADT^A08", "ADT^A40");
	
	/**
	 * Can process a message
	 * @see ca.uhn.hl7v2.app.Application#canProcess(ca.uhn.hl7v2.model.Message)
	 */
	public boolean canProcess(Message message) {
		Terser trs = new Terser(message);
        try {
    		String event = trs.get("/MSH-9-1") + "^" + trs.get("/MSH-9-2");
			return m_handles.contains(event);
        }
        catch (HL7Exception e) {
	        // TODO Auto-generated catch block
	        log.error("Error generated", e);
	        return false;
        }
	}
	
	/**
	 * Process the message
	 * @see ca.uhn.hl7v2.app.Application#processMessage(ca.uhn.hl7v2.model.Message)
	 */
	public Message processMessage(Message message) {

		try
		{
			if(this.m_configuration == null)
				this.m_configuration = PatientIdentityFeedConfiguration.getInstance();
			if(this.m_hl7Service == null)
				this.m_hl7Service = Context.getHL7Service();
			if(this.m_mergeService == null)
				this.m_mergeService = Context.getService(PatientMergeService.class);
			
			// Now determine the message
			if(message instanceof ADT_A01)
			{
				ADT_A01 adt = (ADT_A01)message;
				// TODO: Create the person or update them
				Person resolvedPerson = this.resolvePersonFromIdentifiers(adt.getPID().getPatientIdentifierList());
				
				// Person exists?
				if(resolvedPerson == null && adt.getMSH().getMessageType().getTriggerEvent().equals("A08"))
					return this.createErrorMessage("204", "Patient does not exist", adt);
				
				// Set the attributes of the person from the message
				PID pid = adt.getPID();
				this.updatedPatientFromPID(resolvedPerson, pid);
				
				return this.createAck(message);
			}
			else if(message instanceof ADT_A40)
			{
				ADT_A40 adt = (ADT_A40)message;
				
				// Merge
				for(int i = 0; i < adt.getPIDPD1MRGPV1Reps(); i++)
				{
					ADT_A40_PIDPD1MRGPV1 mrgPid = adt.getPIDPD1MRGPV1(i);
	
					// Resolve the survivor
					Person resolvedSurvivor = this.resolvePersonFromIdentifiers(mrgPid.getPID().getAlternatePatientIDPID());
					
					// Person exists?
					if(resolvedSurvivor == null )
						return this.createErrorMessage("204", "Patient does not exist", adt);
					
					// Resolve the victim
					Person victim = this.resolvePersonFromIdentifiers(mrgPid.getMRG().getPriorAlternatePatientID());
					if(victim == null)
						return this.createErrorMessage("204", "Patient in MRG does not exist", adt);
					
					// Merge the victim with survivor
					try {
		                this.m_mergeService.mergePatients(Context.getPatientService().getPatientOrPromotePerson(resolvedSurvivor.getId()), Arrays.asList(Context.getPatientService().getPatientOrPromotePerson(victim.getId())));
	                }
	                catch (Exception e) {
		                // TODO Auto-generated catch block
		                log.error("Error generated", e);
		                return this.createErrorMessage("207", e.getMessage(), message);
	                }
					
				}
				return this.createAck(message);
			}
			else
				return this.createErrorMessage("200", "Unsupported message type", message);
		}
		catch(Exception e)
		{
			log.error(e);
			return this.createErrorMessage("207", e.getMessage(), message);
		}
	}

	/**
	 * Resolve a person from the patient identifiers
	 * @param patientIdentifierList
	 * @return
	 */
	private Person resolvePersonFromIdentifiers(CX[] patientIdentifierList) {
		for(CX id : patientIdentifierList)
		{
			PatientIdentifierType pidType = Context.getPatientService().getPatientIdentifierTypeByName(id.getAssigningAuthority().getUniversalID().getValue());
			if(pidType == null)
				continue; // skip
			
			List<Patient> candidatePatients = Context.getPatientService().getPatientsByIdentifier(id.getID().getValue(), false);
			if(candidatePatients.size() != 1)
				continue;
			Patient pat = candidatePatients.get(0);
			if(pat != null && 
					pat.getPatientIdentifier(pidType) != null && 
					pat.getPatientIdentifier(pidType).getId().equals(id.getID().getValue()))
				return pat;
		}
		return null;
	}

	/**
	 * Create an acknowledgement message
	 * Auto generated method comment
	 * 
	 * @param request
	 * @return
	 */
	private ACK createAck(Message request) {

		ACK retVal = new ACK();
		Terser terser = new Terser(retVal),
				inboundTerser = new Terser(request);
		ImplementationId impl = Context.getAdministrationService().getImplementationId();
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmm");

        try {
	        terser.set("/MSH-10", UUID.randomUUID().toString());
	        
	        if(impl != null && impl.getName() != null)
	        	terser.set("/MSH-3", impl.getName());
	        else 
	        	terser.set("/MSH-3", "OHIE_OSHR"); // TODO: Find a better default or config place to put this
	        terser.set("/MSH-4", Context.getLocationService().getDefaultLocation().getName());
	        terser.set("/MSH-5", inboundTerser.get("/MSH-3"));
	        terser.set("/MSH-6", inboundTerser.get("/MSH-4"));
	        terser.set("/MSH-7", dateFormat.format(new Date()));
	        terser.set("/MSA-2", inboundTerser.get("/MSH-10"));
	        if(terser.get("/MSH-9-2") != null)
	            terser.set("/MSH-9-2", inboundTerser.get("/MSH-9-2"));
	        terser.set("/MSH-11", inboundTerser.get("/MSH-11"));
	        terser.set("/MSA-1", "AA");
        }
        catch (HL7Exception e) {
	        // TODO Auto-generated catch block
	        log.error("Error generated", e);
        }
        return retVal;
    }

	/**
	 * Update patient from the data in the PID segment
	 * @throws HL7Exception 
	 */
	private void updatedPatientFromPID(Person resolvedPerson, PID pid) throws HL7Exception {
		// TODO: Create the person or update them
		Patient patient = null;
		
		// Person exists?
		if(resolvedPerson == null)
			patient = new Patient();
		else
			patient = Context.getPatientService().getPatientOrPromotePerson(resolvedPerson.getId());

		// Identifiers
		for (CX id : pid.getPatientIdentifierList()) {
			
			String assigningAuthority = id.getAssigningAuthority().getUniversalID().getValue();
			String hl7PatientId = id.getID().getValue();
			
			log.debug("identifier has id=" + hl7PatientId + " assigningAuthority=" + assigningAuthority);
			
			if (assigningAuthority != null && assigningAuthority.length() > 0) {
				
				try {
					PatientIdentifierType pit = Context.getPatientService().getPatientIdentifierTypeByName(
					    assigningAuthority);
					
					PatientIdentifier pi = new PatientIdentifier();
					pi.setIdentifierType(pit);
					pi.setIdentifier(hl7PatientId);
					
					// This is the ECID?
					if(pit != null && pit.getName().equals(this.m_configuration.getEcidRoot()))
						pi.setPreferred(true);
					
					// Get default location
					Location location = Context.getLocationService().getDefaultLocation();
					if (location == null) {
						throw new HL7Exception("Cannot find default location");
					}
					pi.setLocation(location);
					
					try {
						PatientIdentifierValidator.validateIdentifier(pi);
						if(patient.getPatientIdentifier(pit) == null) // add
							patient.getIdentifiers().add(pi);
					}
					catch (PatientIdentifierException ex) {
						log.warn("Patient identifier in PID is invalid: " + pi, ex);
					}
					
				}
				catch (Exception e) {
					log.error("Uncaught error parsing/creating patient identifier '" + hl7PatientId
					        + "' for assigning authority '" + assigningAuthority + "'", e);
				}
			} else {
				log.debug("PID contains identifier with no assigning authority");
				continue;
			}
		}
		
		// Names?
		if(pid.getPatientName().length > 0)
		{
			patient.getNames().clear();
			for (XPN patientNameX : pid.getPatientName()) {
				PersonName name = new PersonName();
				name.setFamilyName(patientNameX.getFamilyLastName().getFamilyName().getValue());
				name.setGivenName(patientNameX.getGivenName().getValue());
				name.setMiddleName(patientNameX.getMiddleInitialOrName().getValue());
				if(patientNameX.getNameTypeCode().getValue().equals("L"))
					name.setPreferred(true);
				patient.addName(name);
			}
		}
		
		// Gender
		String gender = pid.getSex().getValue();
		if (gender == null) {
			throw new HL7Exception("Missing gender in an PID segment");
		}
		gender = gender.toUpperCase();
		if (!OpenmrsConstants.GENDER().containsKey(gender)) {
			throw new HL7Exception("Unrecognized gender: " + gender);
		}
		patient.setGender(gender);
		
		// DOB
		TS dateOfBirth = pid.getDateTimeOfBirth();
		if (dateOfBirth == null || dateOfBirth.getTimeOfAnEvent() == null || dateOfBirth.getTimeOfAnEvent().getValue() == null) {
			throw new HL7Exception("Missing birth date in an PID segment");
		}

		
		patient.setBirthdate(org.marc.everest.datatypes.TS.valueOf(dateOfBirth.getTimeOfAnEvent().getValue()).getDateValue().getTime());
		
		
		Context.getPatientService().savePatient(patient);
    }

	/**
	 * Create an error message (NACK)
	 */
	private Message createErrorMessage(String errorCode, String error, Message request) {
		ACK retVal = this.createAck(request);
		Terser terser = new Terser(retVal);
		
		try
		{
	        terser.set("/MSA-1", "AE");
            terser.set("/MSA-3", "Error occurred");
            terser.set("/MSA-6-1", errorCode);
            terser.set("/MSA-6-2", error);
		}
		catch(Exception e)
		{
			log.error(e);
			return retVal;
		}
		return retVal;
    }

	
}
