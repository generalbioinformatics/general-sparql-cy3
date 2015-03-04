/**
* Copyright (c) 2015 General Bioinformatics Limited
* Distributed under the GNU GPL v2. For full terms see the file LICENSE.
*/
package com.generalbioinformatics.cy3.internal;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import nl.helixsoft.gui.preferences.PreferenceManager;
import nl.helixsoft.util.FileUtils;

import org.apache.commons.codec.binary.Base64;
import org.cytoscape.app.swing.CySwingAppAdapter;
import org.cytoscape.application.swing.CyNodeViewContextMenuFactory;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.model.CyTableManager;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.task.NodeViewTaskFactory;
import org.cytoscape.work.ServiceProperties;
import org.cytoscape.work.TaskFactory;
import org.jdom.JDOMException;
import org.osgi.framework.BundleContext;
import org.xml.sax.InputSource;

import com.generalbioinformatics.rdf.gui.MarrsPreference;
import com.generalbioinformatics.rdf.gui.MarrsProject;
import com.generalbioinformatics.rdf.gui.MarrsQuery;
import com.generalbioinformatics.rdf.gui.ProjectManager;
import com.generalbioinformatics.rdf.gui.TripleStoreManager;

public class CyActivator extends AbstractCyActivator 
{
	public static final boolean SIMPLIFIED_VERSION = false;
	
	private ProjectManager projectMgr;
	private TripleStoreManager conMgr;
	private PreferenceManager prefs; 
	private BundleContext context;
	
	@Override
	public void start(BundleContext context) throws Exception 
	{
		try
		{
			this.context = context;

			//TODO: ask Cytoscape for session file. Check that location is correct on windows.
			File propsFile = new File(FileUtils.getApplicationDir(), "CytoscapeConfiguration/GeneralSparql.properties");
			// ensure directory exists.
			propsFile.getParentFile().mkdirs();
			
			Properties defaultValues = new Properties();
			defaultValues.put (MarrsPreference.MARRS_DRIVER, "Empty Jena Model");
			prefs = new PreferenceManager(propsFile, defaultValues);
			prefs.load();

			CySwingApplication cySwingApplication = getService(context, CySwingApplication.class);
			JFrame frame = cySwingApplication.getJFrame();

			conMgr = new TripleStoreManager(frame, prefs);

			CySwingAppAdapter adapter = getService(context, CySwingAppAdapter.class);
			CyNetworkNaming cyNetworkNaming = getService(context, CyNetworkNaming.class);

			final CytoscapeV3Mapper mapper = new CytoscapeV3Mapper(adapter, conMgr, cyNetworkNaming);
			projectMgr = new ProjectManager(frame, prefs, conMgr, mapper);
			loadPreviousProject();

			registerMenu (context, "Apps.General SPARQL", conMgr.configureAction);
			registerMenu (context, "Apps.General SPARQL", projectMgr.editAction);
//			registerMenu (context, "Apps.General SPARQL", projectMgr.downloadAction);

			if (!SIMPLIFIED_VERSION)
			{
				registerMenu (context, "Apps.General SPARQL", projectMgr.loadAction);
				registerMenu (context, "Apps.General SPARQL", projectMgr.saveAction);

				for (AbstractAction action : projectMgr.recentActions)
				{
					registerMenu(context, "Apps.General SPARQL.Recent", action);
				}
			}
			else
			{
				//TODO: disabled for now...
//				registerMenu(context, "Apps.MARRS", new CheckForUpdatesAction());
			}

			// Project-specific menu items
			for (AbstractAction action : projectMgr.getSearchQueries())
			{
				registerMenu (context, "Apps.General SPARQL.Search", action);
			}

			registerNodeContextMenu(context, new MarrsNodeViewContextMenuFactory(projectMgr, mapper));
			
//			registerNodeViewContextMenu(projectMgr, mapper);

		} 
		catch (Throwable t) 
		{ 
			t.printStackTrace();
			throw new IllegalStateException(t);
		}
	}

	/*
	 // EXPERIMENT using node view task factory instead of node task factory. Not working well so far.
	 
	private void registerNodeViewContextMenu(ProjectManager projectMgr, CytoscapeV3Mapper mapper)
	{
		MarrsProject project = projectMgr.getProject();
		if (project != null) 
		{				
			for (int i = 0; i < project.getRowCount(); ++i)
			{
				MarrsQuery q = project.getRow(i);
				
				RclickActions action = new RclickActions(q, projectMgr, mapper);
				Properties myNodeViewTaskFactoryProps = new Properties();
				myNodeViewTaskFactoryProps.setProperty("title", q.getTitle());
//				myNodeViewTaskFactoryProps.setProperty("preferredMenu", "SPARQL");
				registerService(context, action, NodeViewTaskFactory.class, myNodeViewTaskFactoryProps);
			}							
		}
		
	}
	 */
	
	private void registerNodeContextMenu(BundleContext context,
			CyNodeViewContextMenuFactory myNodeViewContextMenuFactory) 
	{
		
		Properties myNodeViewContextMenuFactoryProps = new Properties();
		myNodeViewContextMenuFactoryProps.put("preferredMenu", "Apps");
		registerAllServices(context, myNodeViewContextMenuFactory, myNodeViewContextMenuFactoryProps);
	}
	
	public void registerMenu(BundleContext context, String parentMenu, AbstractAction action)
	{

		// Configure the service properties first.
		Properties properties = new Properties();

		// Our task should be exposed in the "Apps" menu...
		properties.put(ServiceProperties.PREFERRED_MENU,
				parentMenu);

		String title = "" + action.getValue(AbstractAction.NAME);

		// ... as a sub menu item called "Say Hello".
		properties.put(ServiceProperties.TITLE, title);

		// Our menu item should only be enabled if at least one network
		// view exists.
		//		properties.put(ServiceProperties.ENABLE_FOR, "networkAndView");

		registerService(context,
				new ActionWrapper(action), // Implementation
				TaskFactory.class, // Interface
				properties); // Service properties

	}

	private MarrsProject getBundledProject() throws JDOMException, IOException
	{
		InputStream is = CyActivator.class.getClassLoader().getResourceAsStream("com/generalbioinformatics/cy3/internal/project.xml");
		MarrsProject bundledProject = MarrsProject.createFromFile(new InputSource(is));			
		return bundledProject;
	}
	
	public void loadPreviousProject()
	{
		try 
		{

			if (SIMPLIFIED_VERSION)
			{
				MarrsProject cachedProject = null;
				MarrsProject bundledProject = getBundledProject();
				
				File cacheFile = ProjectManager.getProjectCacheFile();
				if (cacheFile.exists())
				{
					cachedProject = MarrsProject.createFromFile(cacheFile);
				}

				MarrsProject newest = null;

				if (cachedProject == null)
				{
					newest = bundledProject;
				}
				else
				{
					newest = 
							(cachedProject.isSameOrNewerThan(bundledProject))
							? cachedProject : bundledProject;
				}

				projectMgr.setProject(newest);
			}
			
			else
			{
				File projectFile = prefs.getFile(MarrsPreference.MARRS_PROJECT_FILE);
				try
				{
					if (projectFile.exists())
					{
						projectMgr.loadProject (projectFile);
					}
				}
				catch (JDOMException e) 
				{
					// couldn't auto-load, starting with empty project
				} 
				catch (IOException e) 
				{
					// couldn't auto-load, starting with empty project
				}
			
				if (projectMgr.getProject() == null)
				{
					MarrsProject bundledProject = getBundledProject();
					projectMgr.setProject(bundledProject);
				}
			}
		}
		catch (JDOMException e) 
		{
			// couldn't auto-load, starting with empty project
		} 
		catch (IOException e) 
		{
			// couldn't auto-load, starting with empty project
		}

		
	}

	private class CheckForUpdatesAction extends AbstractAction
	{
		CheckForUpdatesAction()
		{
			super ("Check for plugin updates");
		}

		@Override
		public void actionPerformed(ActionEvent e) 
		{
			CySwingApplication cySwingApplication = getService(context, CySwingApplication.class);
			JFrame frame = cySwingApplication.getJFrame();

			// check local version			
			String url = prefs.get(MarrsPreference.MARRS_PLUGIN_UPDATE_URL);
			try {
				URLConnection conn = new URL(url).openConnection();

				//TODO: here we assume that if we need authentication for currently configured sparql endpoint,
				// we can use it also for the project properties file. This may not always hold. 
				if (prefs.getBoolean(MarrsPreference.MARRS_SPARQL_AUTHENTICATE))
				{
					String authString = prefs.get(MarrsPreference.MARRS_SPARQL_USER) + ":" + prefs.get(MarrsPreference.MARRS_SPARQL_PASS);
					byte[] authEncBytes = Base64.encodeBase64(authString.getBytes());
					String authStringEnc = new String(authEncBytes);
					conn.setRequestProperty("Authorization", "Basic " + authStringEnc);
				}

				InputStream inr = conn.getInputStream();
				Properties remoteProps = new Properties();
				remoteProps.load(inr);

				InputStream inl = CyActivator.class.getClassLoader().getResourceAsStream("com/generalbioinformatics/cy3/plugin.props");
				Properties localProps = new Properties();
				localProps.load(inl);

				String info = "<html>Server version: " + remoteProps.getProperty("pluginVersion") + " built " + remoteProps.getProperty("buildtime") +  
						"<br>Local version: " + localProps.getProperty("pluginVersion")  + " built " + localProps.getProperty("buildtime");

				JOptionPane.showMessageDialog(frame, info);
			} 
			catch (MalformedURLException e1) 
			{
				JOptionPane.showMessageDialog(frame, "Malformed URL: '" + url + "' " + e1.getMessage());
				e1.printStackTrace();
			}
			catch (IOException e1) 
			{
				JOptionPane.showMessageDialog(frame, "Error reading properties file: " + e1.getMessage());
				e1.printStackTrace();
			}

		}


	}

}
