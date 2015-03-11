/**
* Copyright (c) 2015 General Bioinformatics Limited
* Distributed under the GNU GPL v2. For full terms see the file LICENSE.
*/
package com.generalbioinformatics.cy3.internal;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JOptionPane;

import nl.helixsoft.gui.preferences.PreferenceManager;
import nl.helixsoft.util.FileUtils;

import org.cytoscape.app.swing.CySwingAppAdapter;
import org.cytoscape.application.swing.AbstractCyAction;
import org.cytoscape.application.swing.CyAction;
import org.cytoscape.application.swing.CyNodeViewContextMenuFactory;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.work.ServiceProperties;
import org.jdom.JDOMException;
import org.osgi.framework.BundleContext;
import org.xml.sax.InputSource;

import com.generalbioinformatics.rdf.gui.MarrsPreference;
import com.generalbioinformatics.rdf.gui.MarrsProject;
import com.generalbioinformatics.rdf.gui.ProjectManager;
import com.generalbioinformatics.rdf.gui.TripleStoreManager;

public class CyActivator extends AbstractCyActivator 
{
	private ProjectManager projectMgr;
	private TripleStoreManager conMgr;
	private PreferenceManager prefs; 
	private BundleContext context;
	
	private CySwingApplication cySwingApplication;
	
	private JMenu recentMenu = null;
	private final AbstractAction DUMMY = new AbstractAction("dummy") {
		@Override
		public void actionPerformed(ActionEvent e) { }
	};

	private void updateRecentMenu()
	{
		if (recentMenu == null)
		{
			registerMenu(context, "Apps.General SPARQL.Recent", DUMMY);
			recentMenu = cySwingApplication.getJMenu("Apps.General SPARQL.Recent");	
		}
		
		recentMenu.removeAll();
		
		for (AbstractAction action : projectMgr.recentActions)
		{
			recentMenu.add (action);
		}		
	}
	
	private JMenu searchMenu = null;
	void updateSearchMenu()
	{
		if (searchMenu == null)
		{
			registerMenu(context, "Apps.General SPARQL.Search", DUMMY);
			searchMenu = cySwingApplication.getJMenu("Apps.General SPARQL.Search");	
		}
		
		searchMenu.removeAll();
		
		for (AbstractAction action : projectMgr.getSearchQueries())
		{
			searchMenu.add (action);
		}

	}
	
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

			cySwingApplication = getService(context, CySwingApplication.class);
			JFrame frame = cySwingApplication.getJFrame();

			conMgr = new TripleStoreManager(frame, prefs);

			CySwingAppAdapter adapter = getService(context, CySwingAppAdapter.class);
			CyNetworkNaming cyNetworkNaming = getService(context, CyNetworkNaming.class);

			final CytoscapeV3Mapper mapper = new CytoscapeV3Mapper(this, adapter, conMgr, cyNetworkNaming, frame);
			projectMgr = new ProjectManager(frame, prefs, conMgr, mapper);
			loadPreviousProject();

			registerMenu (context, "Apps.General SPARQL", conMgr.configureAction);
			registerMenu (context, "Apps.General SPARQL", projectMgr.editAction);
//			registerMenu (context, "Apps.General SPARQL", projectMgr.downloadAction);

			registerMenu (context, "Apps.General SPARQL", projectMgr.loadAction);
//				registerMenu (context, "Apps.General SPARQL", projectMgr.saveAction);				
			updateRecentMenu();

			// Project-specific menu items

			registerNodeContextMenu(context, new MarrsNodeViewContextMenuFactory(projectMgr, mapper, frame));
			
//			registerNodeViewContextMenu(projectMgr, mapper);
			
//			registerMenuTest(adapter);

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
	
	static class MySubMenuItemAction extends AbstractCyAction 
	{
		public MySubMenuItemAction(CySwingApplication desktopApp){
			super("My Sub MenuItem...");
			setPreferredMenu("Apps.My MenuItem");
			//setMenuGravity(2.0f);

		}

		@Override
		public void actionPerformed(ActionEvent e) {
			JOptionPane.showMessageDialog(null, "It works!");
			
		}
	}	   

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

		registerService(context, new ActionWrapper(action, parentMenu), CyAction.class, properties);
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
		catch (JDOMException e) 
		{
			// couldn't auto-load, starting with empty project
		} 
		catch (IOException e) 
		{
			// couldn't auto-load, starting with empty project
		}

		
	}

}
