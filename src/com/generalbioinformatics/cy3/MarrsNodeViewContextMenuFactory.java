/**
* Copyright (c) 2015 General Bioinformatics Limited
* Distributed under the GNU GPL v2. For full terms see the file LICENSE.
*/
package com.generalbioinformatics.cy3;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import nl.helixsoft.recordstream.StreamException;
import nl.helixsoft.util.StringUtils;

import org.cytoscape.application.swing.CyMenuItem;
import org.cytoscape.application.swing.CyNodeViewContextMenuFactory;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyTableManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.View;
import org.osgi.framework.BundleContext;

import com.generalbioinformatics.marrs.plus.MarrsException;
import com.generalbioinformatics.marrs.plus.MarrsProject;
import com.generalbioinformatics.marrs.plus.MarrsQuery;
import com.generalbioinformatics.marrs.plus.ProjectManager;

public class MarrsNodeViewContextMenuFactory implements CyNodeViewContextMenuFactory //, ActionListener
{
	private class QueryAction extends AbstractAction
	{
		private String id;
		private MarrsQuery mq;
		
		QueryAction(MarrsQuery mq, String id)
		{
			putValue(AbstractAction.NAME, mq.getTitle());
			this.mq = mq;
			this.id = id;
		}

		@Override
		public void actionPerformed(ActionEvent e) 
		{
			MarrsProject project = projectMgr.getProject(); 
			project.setQueryParameter("ID", "<" + id + ">");
			String q;
			try {
				q = project.getSubstitutedQuery(mq);
				mapper.createNetwork(q, mq);
			} catch (MarrsException e2) {
				JOptionPane.showMessageDialog(null, "Error preparing query", e2.getMessage(), JOptionPane.ERROR_MESSAGE);
			}
			catch (StreamException e1) {
				JOptionPane.showMessageDialog(null, "Error executing query", e1.getMessage(), JOptionPane.ERROR_MESSAGE);
			}
		}
		
	}
	
	private final ProjectManager projectMgr;
	private final CytoscapeV3Mapper mapper;
	
	MarrsNodeViewContextMenuFactory(ProjectManager value, CytoscapeV3Mapper mapper)
	{
		this.projectMgr = value;
		this.mapper = mapper;
	}
	
	@Override
	public CyMenuItem createMenuItem(CyNetworkView netView,
			View<CyNode> nodeView) 
	{		
		JMenu submenu = new JMenu ("SPARQL");
		
		MarrsProject project = projectMgr.getProject();
		if (project != null) 
		{				
			CyTable tab = netView.getModel().getDefaultNodeTable();
			CyNode node = nodeView.getModel();
			CyRow row = tab.getRow(node.getSUID());
			
			for (int i = 0; i < project.getRowCount(); ++i)
			{
				MarrsQuery q = project.getRow(i);
				if (q.isContextQuery())
				{
					Map<String, String> queryContext = q.getContext();
					
					boolean nodeMatchesContext = true;
					
					for (String key : queryContext.keySet())
					{
						String expectedValue = queryContext.get(key);
						String actualValue = row.get(key, String.class);
						
						if (!expectedValue.equals(actualValue))
						{
							nodeMatchesContext = false;
						}
					}
					
					if (nodeMatchesContext)
					{
						submenu.add(new QueryAction(q, row.get("id", String.class)));
					}
				}
			}							
		}
		
//		JMenuItem menuItem = new JMenuItem("Node View Context Menu Item");
//		menuItem.addActionListener(this);
		CyMenuItem cyMenuItem = new CyMenuItem(submenu, 0);
		return cyMenuItem;
	}

//	public void actionPerformed(ActionEvent e) {
//		// Write your own function here.
//		JOptionPane.showMessageDialog(null, "MyNodeViewContextMenuFactory action worked.");
//	}
}
