package com.contrastsecurity.serialbox;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.junit.Test;

import refutils.util.FieldHelper;

import com.thoughtworks.xstream.XStream;

/**
 * This test case affirms abuse cases XStream.
 */
public class XStreamTest extends TestCase {
	
	private XStream xstream;
	
	@Override
	protected void setUp() throws Exception {
		xstream = new XStream();
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testGenericCollectionStuffing() throws Exception {
		List<String> listOfStrings = new ArrayList<String>();
		listOfStrings.add("contrast");
		listOfStrings.add("security");
		listOfStrings.add("foo");
		
		FieldHelper listHelper = new FieldHelper(listOfStrings);
		listHelper.setValue("elementData", new Object[]{"i","stuffed", new Long(1)});
		
		String xml = xstream.toXML(listOfStrings);
		
		List<String> rebuiltList = (List<String>)xstream.fromXML(xml);
		assertEquals("i", rebuiltList.get(0));
		assertEquals("stuffed", rebuiltList.get(1));
		assertFalse(rebuiltList.get(2) instanceof String);
	}
	
}
