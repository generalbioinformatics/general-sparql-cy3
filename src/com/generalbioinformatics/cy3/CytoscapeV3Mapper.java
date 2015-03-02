/**
* Copyright (c) 2015 General Bioinformatics Limited
* Distributed under the GNU GPL v2. For full terms see the file LICENSE.
*/
package com.generalbioinformatics.cy3;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.JFrame;

import nl.helixsoft.recordstream.Record;
import nl.helixsoft.recordstream.RecordStream;
import nl.helixsoft.recordstream.StreamException;
import nl.helixsoft.util.ObjectUtils;

import org.cytoscape.app.CyAppAdapter;
import org.cytoscape.app.swing.CySwingAppAdapter;
import org.cytoscape.event.CyEventHelper;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.presentation.property.NodeShapeVisualProperty;
import org.cytoscape.view.presentation.property.values.NodeShape;
import org.cytoscape.view.vizmap.VisualMappingFunctionFactory;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.VisualStyleFactory;
import org.cytoscape.view.vizmap.mappings.DiscreteMapping;

import com.generalbioinformatics.rdf.gui.AbstractMarrsMapper;
import com.generalbioinformatics.rdf.gui.MarrsProject;
import com.generalbioinformatics.rdf.gui.MarrsQuery;
import com.generalbioinformatics.rdf.gui.TripleStoreManager;

public class CytoscapeV3Mapper extends AbstractMarrsMapper<CyNode, CyEdge>
{
	private final CyAppAdapter adapter;

	private CyNetwork myNet;
	private CyNetworkView myView;
	private VisualStyle _vs;
	
	private Map<String, CyNode> idMap = new HashMap<String, CyNode>();

	private final CyNetworkNaming cyNetworkNaming;
	
	public CytoscapeV3Mapper(CySwingAppAdapter adapter, TripleStoreManager conMgr, CyNetworkNaming cyNetworkNaming) 
	{
		super (conMgr);
		this.adapter = adapter;
		this.cyNetworkNaming = cyNetworkNaming;
	}

	private void createNodeAttributeIfNotExists(String colName)
	{
		CyTable table = myNet.getDefaultNodeTable();
		if (table.getColumn(colName) == null)
		{
			table.createColumn(colName, String.class, true);
		}
	}

	@Override
	protected void setNodeAttribute(CyNode node, String colName, Object value)
	{
		CyTable table = myNet.getDefaultNodeTable();
		if ("label".equals(colName))
		{
			// legacy hack - where label is used in sparql, map to attribute "name"
			table.getRow(node.getSUID()).set("name", "" + value);
		}
		else
		{
			if (table.getColumn(colName) == null)
			{
				table.createColumn(colName, value.getClass(), true);
			}
			try
			{
				table.getRow(node.getSUID()).set(colName, value);
			}
			catch (IllegalStateException e)
			{
				// class cast problem...
				// TODO...
				e.printStackTrace();
			}
		}
	}

	private CyNetwork createOrGetNetwork()
	{
		if (myNet == null)
		{
			myNet = adapter.getCyNetworkFactory().createNetwork();
			myNet.getRow(myNet).set(CyNetwork.NAME, cyNetworkNaming.getSuggestedNetworkTitle("General SPARQL"));
			
			createNodeAttributeIfNotExists("id");			
			adapter.getCyNetworkManager().addNetwork(myNet);
			
			myView = null;
		}
		
		// create a network view if necessary (regardless of whether the network was just created
		if (myView == null) 
		{
			CyNetworkViewManager cyNetworkViewManager = adapter.getCyNetworkViewManager(); 
			final Collection<CyNetworkView> views = cyNetworkViewManager.getNetworkViews(myNet);
			
			if(views.size() != 0) 
			{
				myView = views.iterator().next();
			}
			else
			{
				// create a new view for my network
				myView = adapter.getCyNetworkViewFactory().createNetworkView(myNet);
				cyNetworkViewManager.addNetworkView(myView);
			}
		}
		return myNet;
	}

	private VisualStyle createOrGetVisualStyle()
	{
		if (_vs == null)
		{
			// To get references to services in CyActivator class
			VisualMappingManager vmmServiceRef = adapter.getVisualMappingManager();

			VisualStyleFactory visualStyleFactory = adapter.getVisualStyleFactory();

			VisualMappingFunctionFactory vmfFactoryC = adapter.getVisualMappingFunctionContinuousFactory();
			VisualMappingFunctionFactory vmfFactoryD = adapter.getVisualMappingFunctionDiscreteFactory();
			VisualMappingFunctionFactory vmfFactoryP = adapter.getVisualMappingFunctionPassthroughFactory();


			// To create a new VisualStyle object and set the mapping function
			_vs = visualStyleFactory.createVisualStyle("General SPARQL visual style");

			//Use pass-through mapping
//			String ctrAttrName1 = "SUID";
//			PassthroughMapping pMapping = (PassthroughMapping) vmfFactoryP.createVisualMappingFunction(ctrAttrName1, 
//					String.class, "", BasicVisualLexicon.NODE_LABEL);

			DiscreteMapping<String, NodeShape> typeMapping = (DiscreteMapping<String, NodeShape>)
					vmfFactoryD.createVisualMappingFunction("type", String.class, BasicVisualLexicon.NODE_SHAPE);
			
			typeMapping.putMapValue("protein", NodeShapeVisualProperty.ROUND_RECTANGLE);
			typeMapping.putMapValue("gene", NodeShapeVisualProperty.DIAMOND);
			typeMapping.putMapValue("reaction", NodeShapeVisualProperty.RECTANGLE);
			
			_vs.addVisualMappingFunction(typeMapping);

			// Add the new style to the VisualMappingManager
			vmmServiceRef.addVisualStyle(_vs);
		}
		return _vs;
	}
	
	/*
	@Override
	public int addAttributes(String q) throws StreamException 
	{
		RecordStream rs = conMgr.getConnection().sparqlSelect(q);

		CyNetwork myNet = createOrGetNetwork();
		CyTable table = myNet.getDefaultNodeTable();
		Set<CyNode> nodesAdded = new HashSet<CyNode>();
		
		int count = 0;
		for (Record r : rs)
		{
			count++;
			String src = "" + r.get("src");
			CyNode nodeSrc = createNodeIfNotExists(src, nodesAdded);
			table.getRow(nodeSrc.getSUID()).set("id", src);
			
			for (int i = 0; i < r.getMetaData().getNumCols(); ++i)
			{
				String colName = r.getMetaData().getColumnName(i);
				if ("src".equals(colName)) continue;				
				setNodeAttribute (nodeSrc, colName, r.get(i));
			}
		}

		flushView();
		
		return count;
	}
	 */
	
	@Override
	protected void flushView()
	{
		CyEventHelper eventHelper = adapter.getCyEventHelper();
		eventHelper.flushPayloadEvents(); // will cause node views to be created...
		
		VisualStyle vs = createOrGetVisualStyle();
		vs.apply(myView);
		
		myView.updateView();	
	}
	
	@Override
	public int addAttributesMatrix(String q) throws StreamException 
	{
		RecordStream rs = conMgr.getConnection().sparqlSelect(q);
		int count = 0;
		for (Record r : rs)
		{
			count++;
			//TODO .. create nodes... add attributes...
		}
		return count;
	}

	@Override
	public JFrame getFrame()
	{
		return null; //TODO - what is the root frame?
	}
	
	@Override
	public void setProject(MarrsProject arg0) {
		// TODO Auto-generated method stub
	}

	/*
	@Override
	public int createNetwork(String q, MarrsQuery mq)
			throws StreamException 
	{
		RecordStream rs = conMgr.getConnection().sparqlSelect(q);

		CyNetwork myNet = createOrGetNetwork();
		CyTable table = myNet.getDefaultNodeTable();
		CyTable edgeTable = myNet.getDefaultEdgeTable();
		Set<CyNode> nodesAdded = new HashSet<CyNode>();
		Set<CyEdge> edgesPostponed = new HashSet<CyEdge>();
		
		int count = 0;
		for (Record r : rs)
		{
			String src = "" + r.get("src");
			String dest = "" + r.get("dest");

			CyNode nodeSrc = createNodeIfNotExists(src, nodesAdded);
			CyNode nodeDest = createNodeIfNotExists(dest, nodesAdded);

			// set name attribute for new nodes
			table.getRow(nodeSrc.getSUID()).set("id", src);
			table.getRow(nodeDest.getSUID()).set("id", dest);

			String interaction = "pp";
			if (r.getMetaData().hasColumnName("interaction"))
			{
				interaction = "" + r.get("interaction"); 
			}

			CyEdge edge = createEdgeIfNotExists (nodeSrc, nodeDest, interaction, edgesPostponed);					
			count++;
			
			createNetworkSecondPass(mq, r, nodeSrc, nodeDest, edge);					

		}
		
		flushView();
		
		return count;
	}
	 */
	
	@Override
	protected void setEdgeAttribute(CyEdge edge, String colName, Object value) 
	{
		if (value == null) return; //ignore
		
		CyTable table = myNet.getDefaultEdgeTable();
		if (table.getColumn(colName) == null)
		{
			table.createColumn(colName, value.getClass(), true);
		}
		
		try
		{
			table.getRow(edge.getSUID()).set(colName, value);
		}
		catch (IllegalStateException e)
		{
			// class cast problem when sparql result does not match column class.
			// TODO...
			e.printStackTrace();
		}

	}

	@Override
	protected void finalizeNetworkAddition(Set<CyNode> nodesAdded,
			Set<CyEdge> edgesPostPoned) 
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void copyNodeCoordinates() 
	{
		// TODO Auto-generated method stub		
	}

	@Override
	protected CyNode createNodeIfNotExists(String key, Set<CyNode> nodesAdded) 
	{
		createOrGetNetwork();
		
		/** returns the node for a given key, or creates a new one if it doesn't exist. */
		if (idMap.containsKey(key))
		{
			return idMap.get(key);
		}
		else
		{
			CyNode node = myNet.addNode();
			idMap.put (key, node);
			setNodeAttribute(node, "id", key);
			nodesAdded.add (node);
			return node;
		}
	}

	@Override
	protected CyEdge createEdgeIfNotExists(CyNode nodeSrc, CyNode nodeDest, String interaction, Set<CyEdge> edgesPostponed) 
	{
		createOrGetNetwork();
		
		CyTable table = myNet.getDefaultEdgeTable();
		CyEdge edge = null;
		
		// check existing edges between src and dest, to make sure we don't create duplicate edges.
		/* CyEdge.Type.INCOMING or CyEdge.Type.OUTGOING do not seem to find any edges at all. //TODO: report bug */
		for (CyEdge i : myNet.getConnectingEdgeList(nodeSrc, nodeDest, CyEdge.Type.ANY))
		{
			String actualInteraction = table.getRow(i.getSUID()).get("interaction", String.class);
			if (ObjectUtils.safeEquals(interaction, actualInteraction))
			{
				// edge already exists
				edge = i;
				break;
			}
		}
		
		if (edge == null)
		{
			edge = myNet.addEdge(nodeSrc, nodeDest, true);
			setEdgeAttribute(edge, "interaction", interaction);
		}
		return edge;
	}

}
