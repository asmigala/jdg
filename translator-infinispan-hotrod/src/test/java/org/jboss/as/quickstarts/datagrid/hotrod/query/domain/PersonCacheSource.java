/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */
package org.jboss.as.quickstarts.datagrid.hotrod.query.domain;



import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.infinispan.client.hotrod.CacheTopologyInfo;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.MetadataValue;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.ServerStatistics;
import org.infinispan.client.hotrod.VersionedValue;
import org.infinispan.commons.util.CloseableIterator;
import org.infinispan.commons.util.concurrent.NotifyingFuture;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.config.Configuration;
import org.infinispan.protostream.descriptors.Descriptor;
import org.infinispan.protostream.descriptors.FileDescriptor;
import org.infinispan.protostream.impl.parser.SquareProtoParser;
import org.infinispan.query.dsl.Query;
import org.teiid.translator.infinispan.hotrod.InfinispanHotRodConnection;
import org.teiid.translator.infinispan.hotrod.ProtobufDataTypeManager;
import org.teiid.translator.object.ClassRegistry;
import org.teiid.translator.object.ObjectConnection;
import org.teiid.util.Version;

/**
 * Sample cache of objects
 * 
 * @author vhalbert
 * @param <K>
 * @param <V>
 *
 */
@SuppressWarnings({ "nls" })
public class PersonCacheSource<K, V>  implements RemoteCache<K, V>{
	
	public static final String PERSON_CACHE_NAME = "PersonsCache";
	
	public static final String PERSON_KEY_FIELD = "id";
	
	public static final String PERSON_CLASS_NAME = Person.class.getName();
	public static final String PHONENUMBER_CLASS_NAME = PhoneNumber.class.getName();
	public static final String PHONETYPE_CLASS_NAME = PhoneType.class.getName();
	public static final String ADDRESSTYPE_CLASS_NAME = Address.class.getName();
	
	public static Map<String, Descriptor> DESCRIPTORS = new HashMap<String, Descriptor>();
	
	public static final int NUMPERSONS = 10;
	public static final int NUMPHONES = 2;
	
	public static ClassRegistry CLASS_REGISTRY = new ClassRegistry(new ProtobufDataTypeManager());
	
	private Map<Object, Object> cache =  Collections.synchronizedMap(new HashMap<Object, Object>());
	
	static {
		try {
			createDescriptors();

			CLASS_REGISTRY.registerClass(Person.class);
			CLASS_REGISTRY.registerClass(PhoneNumber.class);
			CLASS_REGISTRY.registerClass(PhoneType.class);
			CLASS_REGISTRY.registerClass(Address.class);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	
	public static InfinispanHotRodConnection createConnection(final boolean useKeyClass) {
		return createConnection(useKeyClass, null);
	}
	
	public static InfinispanHotRodConnection createConnection(final boolean useKeyClass, Version version) {
		return createConnection("id", useKeyClass, version);
	}
	
	public static InfinispanHotRodConnection createConnection(final String keyField, final boolean useKeyClass, Version version) {
		final RemoteCache objects = PersonCacheSource.loadCache();

		return PersonCacheConnection.createConnection(objects, keyField, useKeyClass, version);

	}
	public static ObjectConnection createConnection(final boolean useKeyClass, boolean staging, Version version) {
		final RemoteCache objects = PersonCacheSource.loadCache();

		return PersonCacheConnection.createConnection(objects, "id", useKeyClass, staging, version);
	}
	
	public static void loadCache(Map<Object, Object> cache) {
		PhoneType[] types = new PhoneType[] {PhoneType.HOME, PhoneType.MOBILE, PhoneType.WORK};
		int t = 0;
		
		for (int i = 1; i <= NUMPERSONS; i++) {
			
			ArrayList<PhoneNumber> pns = new ArrayList<PhoneNumber>();
			double d = 0;
			for (int j = 1; j <= NUMPHONES; j++) {
				PhoneNumber pn = new PhoneNumber("(111)222-345" + j, types[t++]);
				if (t > 2) t = 0;				
				pns.add(pn);
			}
			
			Person p = new Person();
			p.setId(i);
			p.setName("Person " + i);
			p.setPhones(pns);
			
			Address a = new Address();
			a.setAddress(p.getName() + " address");
			a.setCity(p.getName() + " city");
			a.setState("VA");	
			p.setAddress(a);
			
			cache.put(new Integer(i), p);

		}
	}

	public static RemoteCache loadCache() {
		PersonCacheSource tcs = new PersonCacheSource();
		PersonCacheSource.loadCache(tcs);
		return tcs;
	}
	
	public List<Object> get(int key) {
		List<Object> objs = new ArrayList<Object>(1);
		objs.add(this.get(key));
		return objs;
	}	
	
	private static void createDescriptors() throws Exception {
		Configuration config = new Configuration.Builder().build();
		
		try {
			FileDescriptorSource fileDescriptorSource = FileDescriptorSource.fromResources(
	              "addressbook.proto");

	      Map<String, FileDescriptor> files = new SquareProtoParser(config).parseAndResolve(fileDescriptorSource);

	      FileDescriptor descriptor = files.get("addressbook.proto");
	      
	      Map<String, Descriptor> messages = new HashMap<String, Descriptor>();
	      for (Descriptor m : descriptor.getMessageTypes()) {
	         messages.put(m.getFullName(), m);
	      }

	      String descriptorName = "quickstart.Person";
	      Descriptor personDesc = messages.get(descriptorName);
	      
	      if (personDesc == null) {
	    	  throw new Exception("Did not get descriptor: " + descriptorName );
	      }
	      
	      DESCRIPTORS.put(Person.class.getName(), personDesc);
	      
	      Descriptor addressDesc = messages.get("quickstart.Address");
	      
	      if (addressDesc == null) {
	    	  throw new Exception("Did not get descriptor: " + "quickstart.Address" );
	      }
	      
	      Descriptor phoneDesc = messages.get("quickstart.PhoneNumber");
	      
	      if (phoneDesc == null) {
	    	  throw new Exception("Did not get descriptor: " + "quickstart.PhoneNumber" );
	      }

	      
	  } catch (Exception e) {
    	throw e;
	  }
		
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.BasicCache#getName()
	 */
	@Override
	public String getName() {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.BasicCache#getVersion()
	 */
	@Override
	public String getVersion() {
		return null;
	}

	/**
	 * 
	 *
	 * @param arg0 
	 * @param arg1 
	 * @return V
	 * @see org.infinispan.commons.api.BasicCache#put(java.lang.Object, java.lang.Object)
	 */
	@Override
	public V put(K arg0, V arg1) {
		cache.put(arg0, arg1);
		return arg1;
	}
	

	/**
	 *
	 *
	 * @see org.infinispan.commons.api.BasicCache#put(java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public V put(K arg0, V arg1, long arg2, TimeUnit arg3) {
		return null;
	}

	/**
	 *
	 *
	 * @see org.infinispan.commons.api.BasicCache#put(java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public V put(K arg0, V arg1, long arg2, TimeUnit arg3, long arg4,
			TimeUnit arg5) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.BasicCache#putIfAbsent(java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public V putIfAbsent(K arg0, V arg1, long arg2, TimeUnit arg3) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.BasicCache#putIfAbsent(java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public V putIfAbsent(K arg0, V arg1, long arg2, TimeUnit arg3, long arg4,
			TimeUnit arg5) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.BasicCache#remove(java.lang.Object)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public V remove(Object arg0) {
		return (V) cache.remove(arg0);
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.BasicCache#replace(java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public V replace(K arg0, V arg1, long arg2, TimeUnit arg3) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.BasicCache#replace(java.lang.Object, java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public boolean replace(K arg0, V arg1, V arg2, long arg3, TimeUnit arg4) {
		return false;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.BasicCache#replace(java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public V replace(K arg0, V arg1, long arg2, TimeUnit arg3, long arg4,
			TimeUnit arg5) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.BasicCache#replace(java.lang.Object, java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public boolean replace(K arg0, V arg1, V arg2, long arg3, TimeUnit arg4,
			long arg5, TimeUnit arg6) {
		return false;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#clearAsync()
	 */
	@Override
	public NotifyingFuture<Void> clearAsync() {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#getAsync(java.lang.Object)
	 */
	@Override
	public NotifyingFuture<V> getAsync(K arg0) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#putAsync(java.lang.Object, java.lang.Object)
	 */
	@Override
	public NotifyingFuture<V> putAsync(K arg0, V arg1) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#putAsync(java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public NotifyingFuture<V> putAsync(K arg0, V arg1, long arg2, TimeUnit arg3) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#putAsync(java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public NotifyingFuture<V> putAsync(K arg0, V arg1, long arg2,
			TimeUnit arg3, long arg4, TimeUnit arg5) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#putIfAbsentAsync(java.lang.Object, java.lang.Object)
	 */
	@Override
	public NotifyingFuture<V> putIfAbsentAsync(K arg0, V arg1) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#putIfAbsentAsync(java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public NotifyingFuture<V> putIfAbsentAsync(K arg0, V arg1, long arg2,
			TimeUnit arg3) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#putIfAbsentAsync(java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public NotifyingFuture<V> putIfAbsentAsync(K arg0, V arg1, long arg2,
			TimeUnit arg3, long arg4, TimeUnit arg5) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#removeAsync(java.lang.Object)
	 */
	@Override
	public NotifyingFuture<V> removeAsync(Object arg0) {
		this.cache.remove(arg0);
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#removeAsync(java.lang.Object, java.lang.Object)
	 */
	@Override
	public NotifyingFuture<Boolean> removeAsync(Object arg0, Object arg1) {
		this.cache.remove(arg0);
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#replaceAsync(java.lang.Object, java.lang.Object)
	 */
	@Override
	public NotifyingFuture<V> replaceAsync(K arg0, V arg1) {
		this.cache.put(arg0, arg1);
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#replaceAsync(java.lang.Object, java.lang.Object, java.lang.Object)
	 */
	@Override
	public NotifyingFuture<Boolean> replaceAsync(K arg0, V arg1, V arg2) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#replaceAsync(java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public NotifyingFuture<V> replaceAsync(K arg0, V arg1, long arg2,
			TimeUnit arg3) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#replaceAsync(java.lang.Object, java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public NotifyingFuture<Boolean> replaceAsync(K arg0, V arg1, V arg2,
			long arg3, TimeUnit arg4) {
		return null;
	}

	/**
	 *  
	 *
	 * @param arg0 
	 * @param arg1 
	 * @param arg2 
	 * @param arg3 
	 * @param arg4 
	 * @param arg5 
	 * @return NotifyingFuture
	 * @see org.infinispan.commons.api.AsyncCache#replaceAsync(java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public NotifyingFuture<V> replaceAsync(K arg0, V arg1, long arg2,
			TimeUnit arg3, long arg4, TimeUnit arg5) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.AsyncCache#replaceAsync(java.lang.Object, java.lang.Object, java.lang.Object, long, java.util.concurrent.TimeUnit, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public NotifyingFuture<Boolean> replaceAsync(K arg0, V arg1, V arg2,
			long arg3, TimeUnit arg4, long arg5, TimeUnit arg6) {
		return null;
	}

	/**
	 *  
	 *
	 * @see java.util.concurrent.ConcurrentMap#putIfAbsent(java.lang.Object, java.lang.Object)
	 */
	@Override
	public V putIfAbsent(K arg0, V arg1) {
		return null;
	}

	/**
	 *  
	 *
	 * @see java.util.concurrent.ConcurrentMap#remove(java.lang.Object, java.lang.Object)
	 */
	@Override
	public boolean remove(Object arg0, Object arg1) {
		boolean exist = cache.containsKey(arg0);
		if (exist) cache.remove(arg0);
		return exist;
	}

	/**
	 *  
	 *
	 * @see java.util.concurrent.ConcurrentMap#replace(java.lang.Object, java.lang.Object)
	 */
	@Override
	public V replace(K arg0, V arg1) {
		cache.put(arg0, arg1);
		return arg1;
	}

	/**
	 *  
	 *
	 * @see java.util.concurrent.ConcurrentMap#replace(java.lang.Object, java.lang.Object, java.lang.Object)
	 */
	@Override
	public boolean replace(K arg0, V arg1, V arg2) {
		return false;
	}

	/**
	 *  
	 *
	 * @see java.util.Map#clear()
	 */
	@Override
	public void clear() {
		cache.clear();
	}

	/**
	 *  
	 *
	 * @see java.util.Map#containsKey(java.lang.Object)
	 */
	@Override
	public boolean containsKey(Object arg0) {
		return cache.containsKey(arg0);
	}

	/**
	 *  
	 *
	 * @see java.util.Map#get(java.lang.Object)
	 */
	@Override
	public V get(Object arg0) {
		return (V) cache.get(arg0);
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.Lifecycle#start()
	 */
	@Override
	public void start() {
	}

	/**
	 *  
	 *
	 * @see org.infinispan.commons.api.Lifecycle#stop()
	 */
	@Override
	public void stop() {
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#removeWithVersion(java.lang.Object, long)
	 */
	@Override
	public boolean removeWithVersion(K key, long version) {
		return false;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#removeWithVersionAsync(java.lang.Object, long)
	 */
	@Override
	public NotifyingFuture<Boolean> removeWithVersionAsync(K key, long version) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#replaceWithVersion(java.lang.Object, java.lang.Object, long)
	 */
	@Override
	public boolean replaceWithVersion(K key, V newValue, long version) {
		return false;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#replaceWithVersion(java.lang.Object, java.lang.Object, long, int)
	 */
	@Override
	public boolean replaceWithVersion(K key, V newValue, long version,
			int lifespanSeconds) {
		return false;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#replaceWithVersion(java.lang.Object, java.lang.Object, long, int, int)
	 */
	@Override
	public boolean replaceWithVersion(K key, V newValue, long version,
			int lifespanSeconds, int maxIdleTimeSeconds) {
		return false;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#replaceWithVersionAsync(java.lang.Object, java.lang.Object, long)
	 */
	@Override
	public NotifyingFuture<Boolean> replaceWithVersionAsync(K key, V newValue,
			long version) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#replaceWithVersionAsync(java.lang.Object, java.lang.Object, long, int)
	 */
	@Override
	public NotifyingFuture<Boolean> replaceWithVersionAsync(K key, V newValue,
			long version, int lifespanSeconds) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#replaceWithVersionAsync(java.lang.Object, java.lang.Object, long, int, int)
	 */
	@Override
	public NotifyingFuture<Boolean> replaceWithVersionAsync(K key, V newValue,
			long version, int lifespanSeconds, int maxIdleSeconds) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#getVersioned(java.lang.Object)
	 */
	@Override
	public VersionedValue<V> getVersioned(K key) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#getWithMetadata(java.lang.Object)
	 */
	@Override
	public MetadataValue<V> getWithMetadata(K key) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#size()
	 */
	@Override
	public int size() {
		return cache.size();
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return cache.isEmpty();
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#containsValue(java.lang.Object)
	 */
	@Override
	public boolean containsValue(Object value) {
		return cache.containsValue(value);
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#keySet()
	 */
	@Override
	public Set<K> keySet() {
		return (Set<K>) cache.keySet();
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#values()
	 */
	@Override
	public Collection<V> values() {
		return (Collection<V>) cache.values();
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#entrySet()
	 */
	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {

		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#putAll(java.util.Map, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> map, long lifespan,
			TimeUnit unit) {
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#putAll(java.util.Map, long, java.util.concurrent.TimeUnit, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> map, long lifespan,
			TimeUnit lifespanUnit, long maxIdleTime, TimeUnit maxIdleTimeUnit) {
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#putAllAsync(java.util.Map)
	 */
	@Override
	public NotifyingFuture<Void> putAllAsync(Map<? extends K, ? extends V> data) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#putAllAsync(java.util.Map, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public NotifyingFuture<Void> putAllAsync(
			Map<? extends K, ? extends V> data, long lifespan, TimeUnit unit) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#putAllAsync(java.util.Map, long, java.util.concurrent.TimeUnit, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public NotifyingFuture<Void> putAllAsync(
			Map<? extends K, ? extends V> data, long lifespan,
			TimeUnit lifespanUnit, long maxIdle, TimeUnit maxIdleUnit) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#putAll(java.util.Map)
	 */
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#stats()
	 */
	@Override
	public ServerStatistics stats() {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#withFlags(org.infinispan.client.hotrod.Flag[])
	 */
	@Override
	public RemoteCache<K, V> withFlags(Flag... flags) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#getRemoteCacheManager()
	 */
	@Override
	public RemoteCacheManager getRemoteCacheManager() {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#getBulk()
	 */
	@Override
	public Map<K, V> getBulk() {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#getBulk(int)
	 */
	@Override
	public Map<K, V> getBulk(int size) {
		return null;
	}

	/**
	 *  
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#getProtocolVersion()
	 */
	@Override
	public String getProtocolVersion() {
		return null;
	}

	@Override
	public void addClientListener(Object arg0, Object[] arg1, Object[] arg2) {
	}

	@Override
	public void addClientListener(Object arg0) {
	}

	@Override
	public Set<Object> getListeners() {
		return null;
	}

	@Override
	public void removeClientListener(Object arg0) {
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#replaceWithVersion(java.lang.Object, java.lang.Object, long, long, java.util.concurrent.TimeUnit, long, java.util.concurrent.TimeUnit)
	 */
	@Override
	public boolean replaceWithVersion(K key, V newValue, long version,
			long lifespan, TimeUnit lifespanTimeUnit, long maxIdle,
			TimeUnit maxIdleTimeUnit) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#getAll(java.util.Set)
	 */
	@Override
	public Map<K, V> getAll(Set<? extends K> keys) {
		return null;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#execute(java.lang.String, java.util.Map)
	 */
	@Override
	public <T> T execute(String arg0, Map<String, ?> arg1) {
		return null;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#getCacheTopologyInfo()
	 */
	@Override
	public CacheTopologyInfo getCacheTopologyInfo() {
		return null;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#retrieveEntries(java.lang.String, int)
	 */
	@Override
	public CloseableIterator<java.util.Map.Entry<Object, Object>> retrieveEntries(String arg0, int arg1) {
		return null;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#retrieveEntries(java.lang.String, java.util.Set, int)
	 */
	@Override
	public CloseableIterator<java.util.Map.Entry<Object, Object>> retrieveEntries(String arg0, Set<Integer> arg1,
			int arg2) {
		return null;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#retrieveEntries(java.lang.String, java.lang.Object[], java.util.Set, int)
	 */
	@Override
	public CloseableIterator<java.util.Map.Entry<Object, Object>> retrieveEntries(String arg0, Object[] arg1,
			Set<Integer> arg2, int arg3) {
		return null;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#retrieveEntriesByQuery(org.infinispan.query.dsl.Query, java.util.Set, int)
	 */
	@Override
	public CloseableIterator<java.util.Map.Entry<Object, Object>> retrieveEntriesByQuery(Query arg0, Set<Integer> arg1,
			int arg2) {
		return null;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @see org.infinispan.client.hotrod.RemoteCache#retrieveEntriesWithMetadata(java.util.Set, int)
	 */
	@Override
	public CloseableIterator<java.util.Map.Entry<Object, MetadataValue<Object>>> retrieveEntriesWithMetadata(
			Set<Integer> arg0, int arg1) {
		return null;
	}
	
	
	
}
