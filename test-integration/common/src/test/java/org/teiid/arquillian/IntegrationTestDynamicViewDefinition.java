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

package org.teiid.arquillian;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.util.Properties;

import org.jboss.arquillian.junit.Arquillian;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.teiid.adminapi.Admin;
import org.teiid.adminapi.AdminException;
import org.teiid.adminapi.AdminFactory;
import org.teiid.core.util.UnitTestUtil;
import org.teiid.jdbc.AbstractMMQueryTestCase;
import org.teiid.jdbc.TeiidDriver;

@RunWith(Arquillian.class)
@SuppressWarnings("nls")
public class IntegrationTestDynamicViewDefinition extends AbstractMMQueryTestCase {

	private Admin admin;
	
	@Before
	public void setup() throws Exception {
		admin = AdminFactory.getInstance().createAdmin("localhost", 9999,	"admin", "admin".toCharArray());
	}
	
	@After
	public void teardown() throws AdminException {
		AdminUtil.cleanUp(admin);
		admin.close();
	}

	@Test
    public void testViewDefinition() throws Exception {
				
		admin.deploy("dynamicview-vdb.xml",new FileInputStream(UnitTestUtil.getTestDataFile("dynamicview-vdb.xml")));
		
		Properties props = new Properties();
		props.setProperty("ParentDirectory", "../docs/teiid/examples/dynamicvdb-portfolio/data");
		props.setProperty("AllowParentPaths", "true");
		props.setProperty("class-name", "org.teiid.resource.adapter.file.FileManagedConnectionFactory");
		
		AdminUtil.createDataSource(admin, "marketdata-file", "teiid-connector-file.rar", props);
		
		assertTrue(AdminUtil.waitForVDBLoad(admin, "dynamic", 1, 3));
		
		this.internalConnection =  TeiidDriver.getInstance().connect("jdbc:teiid:dynamic@mm://localhost:31000;user=user;password=user", null);
		
		execute("SELECT * FROM Sys.Columns WHERE tablename='stock'"); //$NON-NLS-1$
		assertRowCount(2);
    }

}
