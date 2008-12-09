/*
 * JBoss, Home of Professional Open Source.
 * Copyright (C) 2008 Red Hat, Inc.
 * Copyright (C) 2000-2007 MetaMatrix, Inc.
 * Licensed to Red Hat, Inc. under one or more contributor 
 * license agreements.  See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package com.metamatrix.server;

import java.io.File;
import java.util.Properties;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.metamatrix.common.buffer.BufferManager;
import com.metamatrix.common.config.CurrentConfiguration;
import com.metamatrix.common.config.ResourceNames;
import com.metamatrix.common.config.api.Host;
import com.metamatrix.common.config.api.HostType;
import com.metamatrix.common.config.api.exceptions.ConfigurationException;
import com.metamatrix.common.log.DbLogListener;
import com.metamatrix.common.log.DbWriterException;
import com.metamatrix.common.log.LogManager;
import com.metamatrix.common.messaging.MessageBus;
import com.metamatrix.common.util.LogCommonConstants;
import com.metamatrix.common.util.PropertiesUtils;
import com.metamatrix.common.util.VMNaming;
import com.metamatrix.core.util.FileUtils;
import com.metamatrix.core.util.StringUtil;
import com.metamatrix.dqp.ResourceFinder;
import com.metamatrix.internal.core.log.PlatformLog;
import com.metamatrix.platform.PlatformPlugin;
import com.metamatrix.platform.config.ConfigurationChangeListener;
import com.metamatrix.platform.config.event.ConfigurationChangeEvent;
import com.metamatrix.platform.util.LogMessageKeys;
import com.metamatrix.platform.vm.api.controller.VMControllerInterface;
import com.metamatrix.platform.vm.util.VMUtils;

/**
 * This is main server starter class.
 */
public class Main {

	@Inject
	MessageBus messageBus;
	
	@Inject 
	BufferManager bufferManager;
	
	@Inject
	VMControllerInterface vmController;
	
	public static void main(String[] args) throws Exception{
        
		if (args.length < 2 || args.length > 4) {
            System.out.println("Usage: java com.metamatrix.server.Main <vm_name> <host_name>"); //$NON-NLS-1$
            System.exit(1);        	
        }

        String vmName = args[0];
        String hostName = args[1];

        Host host = null;
        try {
			host = CurrentConfiguration.findHost(hostName);        
		} catch (ConfigurationException e) {
		}
		if (host == null) {
		    System.err.println(PlatformPlugin.Util.getString("SocketVMController.5", hostName)); //$NON-NLS-1$
		    System.exit(-1);
		}
		
		VMNaming.setVMName(vmName);
        VMNaming.setLogicalHostName(host.getFullName());
        VMNaming.setBindAddress(host.getBindAddress());
        VMNaming.setHostAddress(host.getHostAddress());
		
        // write info log
        writeInfoLog(host, vmName);
        
        // Start the log file
        VMUtils.startLogFile(host.getLogDirectory(), buildPrefix(host.getName(), vmName) +  ".log"); //$NON-NLS-1$
        
        createTempDirectory();                    
        
        // wire up guice modules
        Main main = loadMain(host, vmName);
        
        // launch the server
        
		main.launchServer();
		
        // stop logging
        LogManager.stop();		
	}
	
	
	private static Main loadMain(Host host, String vmName) {
		Injector injector = Guice.createInjector(new ServerGuiceModule(host, vmName));
		// Until we get the all the DI working we have to resort to this kind of stuff..
		ResourceFinder.setInjector(injector); 
		return injector.getInstance(Main.class);
	}


	private void launchServer() {
        
        try {          
            DbLogListener dbListener = startDbLogging();
            ConfigurationChangeListener configListener = new ConfigurationChangeListener(dbListener);
            this.messageBus.addListener(ConfigurationChangeEvent.class, configListener);
        	
            // start the VM
            this.vmController.startVM();
            
            configListener.shutdown();
            
            this.messageBus.shutdown();
            
            this.bufferManager.stop();
            
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }


	private static void createTempDirectory() {
		// If the java-io-tmp directory doesn't exist, it needs to be created
		// because extension jars class loading will fail because
		// java internals can' write to a non-existent directory.
		// It's added here, in addition to the host controller because if the
		// vm starter command is changed and the vm is bounced, the hostcontroller
		// would never ensure this tmp directory is created.
		if (FileUtils.TEMP_DIRECTORY != null) {
		    File tf = new File(FileUtils.TEMP_DIRECTORY);
		    if (!tf.exists()) {
		        tf.mkdirs();
		    }
		}
	}		

	private static String buildPrefix(String hostName, String vmName){
	    String hostFileName = StringUtil.replaceAll(hostName, ".", "_"); //$NON-NLS-1$ //$NON-NLS-2$
	    return hostFileName + "_" + vmName; //$NON-NLS-1$
	}   
	
    private static DbLogListener startDbLogging() throws Exception, DbWriterException {
        Properties resourceProps = CurrentConfiguration.getResourceProperties(ResourceNames.LOGGING);
        Properties currentProps = CurrentConfiguration.getProperties();
        Properties resultsProps = PropertiesUtils.clone(currentProps, resourceProps, true, false);
        
        // write a db log listener
        DbLogListener dll = new DbLogListener(resultsProps);
        
        // start the logger
        PlatformLog.getInstance().addListener(dll);
        LogManager.logInfo(LogCommonConstants.CTX_CONTROLLER, PlatformPlugin.Util.getString(LogMessageKeys.VM_0052));
        
        return dll;
    }
	

    private static void writeInfoLog(Host host, String vmName) {
        // trigger the logging of the current application info to a log file for debugging        
        LogApplicationInfo logApplInfo = new LogApplicationInfo(host.getFullName(), vmName, host.getLogDirectory(), buildPrefix(host.getFullName(), vmName) + "_info.log"); //$NON-NLS-1$
        logApplInfo.start();        	
    }
}
