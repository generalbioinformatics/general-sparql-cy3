/**
* Copyright (c) 2015 General Bioinformatics Limited
* Distributed under the GNU GPL v2. For full terms see the file LICENSE.
*/
package com.generalbioinformatics.cy3.internal;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JOptionPane;

import org.cytoscape.application.swing.CyMenuItem;
import org.cytoscape.application.swing.CyNodeViewContextMenuFactory;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyTableUtil;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.View;

import com.generalbioinformatics.rdf.gui.MarrsException;
import com.generalbioinformatics.rdf.gui.MarrsProject;
import com.generalbioinformatics.rdf.gui.MarrsQuery;
import com.generalbioinformatics.rdf.gui.ProjectDlg;
import com.generalbioinformatics.rdf.gui.ProjectManager;

import nl.helixsoft.recordstream.StreamException;
import nl.helixsoft.util.StringUtils;

public class MarrsNodeViewContextMenuFactory implements CyNodeViewContextMenuFactory //, ActionListener
{
	private class QueryAction extends AbstractAction
	{
		private String id;
		private MarrsQuery mq;
		private CyNode clickedNode;
		
		QueryAction(MarrsQuery mq, CyNode clickedNode, String id)
		{
			putValue(AbstractAction.NAME, mq.getTitle());
			this.mq = mq;
			this.id = id;
			this.clickedNode = clickedNode;
		}

		@Override
		public void actionPerformed(ActionEvent e) 
		{
			MarrsProject project = projectMgr.getProject();
			CyNetwork myNet = mapper.createOrGetNetwork();
			
			String filter = project.getQueryParameterFilter(mq, "ID");			
			if ("uri-list".equals (filter))
			{
				// active nodes is the union of selected nodes and the right-clicked node.
				List<CyNode> activeNodes = new ArrayList<CyNode>();
				activeNodes.addAll (CyTableUtil.getNodesInState(myNet,"selected",true));
				if (!activeNodes.contains (clickedNode))
				{
					activeNodes.add (clickedNode);
				}
				
				CyTable nodeTable = myNet.getDefaultNodeTable();
				
				StringBuilder builder = new StringBuilder();
				String sep = "<";
			
				for (CyNode n : activeNodes)
				{
					String nid = nodeTable.getRow(n.getSUID()).get("id", String.class);
					builder.append (sep);
					builder.append (nid);
					sep = ">, <";
				}
				builder.append (">");
				
				project.setQueryParameter("ID", builder.toString());
			}
			else if ("uri".equals(filter))
			{
				project.setQueryParameter("ID", "<" + id + ">");
			}
			else if ("uri-bracketed".equals(filter))
			{
				project.setQueryParameter("ID", "<" + id + ">");
			}
			else
			{
				project.setQueryParameter("ID", id);
			}
			
			String qtext;
			try {
				qtext = project.getSubstitutedQuery(mq);
				int count = ProjectDlg.doQuery(mapper, qtext, mq);
				if (count == 0)
					JOptionPane.showMessageDialog(frame, "Zero results");
				
			} catch (MarrsException e2) {
				JOptionPane.showMessageDialog(frame, "<html>Error fetching results<br>" + StringUtils.escapeHtml(e2.getMessage()), "Error preparing query", JOptionPane.ERROR_MESSAGE);
			}
			catch (StreamException e1) {
				JOptionPane.showMessageDialog(frame, "<html>Error fetching results<br>" + StringUtils.escapeHtml(e1.getMessage()), "Error executing query", JOptionPane.ERROR_MESSAGE);
			}
			System.out.println ("Query done");
		}
		
	}
	
	private final ProjectManager projectMgr;
	private final CytoscapeV3Mapper mapper;
	private final JFrame frame;
	
	MarrsNodeViewContextMenuFactory(ProjectManager value, CytoscapeV3Mapper mapper, JFrame frame)
	{
		this.projectMgr = value;
		this.mapper = mapper;
		this.frame = frame;
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
						submenu.add(new QueryAction(q, node, row.get("id", String.class)));
					}
				}
			}							
		}
		
//		JMenuItem menuItem = new JMenuItem("Node View Context Menu Item");
//		menuItem.addActionListener(this);
		CyMenuItem cyMenuItem = new CyMenuItem(submenu, 0);
		return cyMenuItem;
	}

}
