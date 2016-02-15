package com.contrastsecurity.serialbox;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import refutils.util.FieldHelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import junit.framework.TestCase;

/**
 * This class contains tests that prove the (lack of) abuse cases in Gson.
 */
public class GsonTest extends TestCase {
	
	@Test
	public void testGenericCollectionStuffing() throws Throwable {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		AcmeMessage msg = new AcmeMessage();
		List<AcmeRecipient> recipients = new ArrayList<AcmeRecipient>();
		recipients.add(new AcmeRecipient("contrast"));
		recipients.add(new AcmeRecipient("security"));
		recipients.add(new AcmeRecipient("contrast"));
		
		FieldHelper listHelper = new FieldHelper(recipients);
		listHelper.setValue("elementData", new Object[] { 
				new AcmeRecipient("i"), 
				"hacked", 
				new Long(1)
		});
		
		msg.setRecipients(recipients);
		msg.setSubject("test subject");
		msg.setBody("test body");
		
		String json = gson.toJson(msg);
		
		try {
			gson.fromJson(json, AcmeMessage.class);
			fail("shouldn't have been rebuilt");
		} catch (Exception e) {
			// should happen
		}
		
		listHelper = new FieldHelper(recipients);
		listHelper.setValue("elementData", new Object[] { 
				new AcmeRecipient("more"), 
				new AcmeRecipient("subtle"), 
				new NotAcmeRecipient("stuffing")
		});
		
		json = gson.toJson(msg);
		
		try {
			AcmeMessage rebuiltMessage = gson.fromJson(json, AcmeMessage.class);
			assertEquals(AcmeRecipient.class, rebuiltMessage.getRecipients().get(2).getClass());
		} catch (Exception e) {
			e.printStackTrace();
			fail("recipient deserialized into unexpected type");
		}
	}

}
