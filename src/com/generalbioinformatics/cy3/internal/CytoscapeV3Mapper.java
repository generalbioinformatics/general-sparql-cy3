/**
* Copyright (c) 2015 General Bioinformatics Limited
* Distributed under the GNU GPL v2. For full terms see the file LICENSE.
*/
package com.generalbioinformatics.cy3.internal;

import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JFrame;

import nl.helixsoft.util.ObjectUtils;
import nl.helixsoft.util.StringUtils;

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
import org.cytoscape.view.model.View;
import org.cytoscape.view.model.VisualLexicon;
import org.cytoscape.view.model.VisualProperty;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.presentation.property.NodeShapeVisualProperty;
import org.cytoscape.view.presentation.property.values.NodeShape;
import org.cytoscape.view.vizmap.VisualMappingFunctionFactory;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.view.vizmap.VisualStyleFactory;
import org.cytoscape.view.vizmap.mappings.DiscreteMapping;
import org.cytoscape.view.vizmap.mappings.PassthroughMapping;

import com.generalbioinformatics.rdf.gui.AbstractMarrsMapper;
import com.generalbioinformatics.rdf.gui.MarrsProject;
import com.generalbioinformatics.rdf.gui.TripleStoreManager;

public class CytoscapeV3Mapper extends AbstractMarrsMapper<CyNode, CyEdge>
{
	private final CyAppAdapter adapter;
	private final JFrame frame;
	
	private CyNetwork myNet;
	private CyNetworkView myView;
	private VisualStyle _vs;
	
	private Map<String, CyNode> idMap = new HashMap<String, CyNode>();

	private final CyNetworkNaming cyNetworkNaming;
	private final CyActivator activator;
	
	public CytoscapeV3Mapper(CyActivator activator, CySwingAppAdapter adapter, TripleStoreManager conMgr, CyNetworkNaming cyNetworkNaming, JFrame frame) 
	{
		super (conMgr);
		this.adapter = adapter;
		this.cyNetworkNaming = cyNetworkNaming;
		this.frame = frame;
		this.activator = activator;
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
				if (colName.endsWith("_list"))
				{
					table.createListColumn(colName, String.class, true);
				}
				else
				{
					table.createColumn(colName, String.class, true);
				}
			}
			try
			{
				if (colName.endsWith("_list"))
				{
					List<String> data = table.getRow(node.getSUID()).getList(colName, String.class);
					if (data == null)
					{
						data = new ArrayList<String>();
						data.add(StringUtils.safeToString(value));
						table.getRow(node.getSUID()).set(colName, data);
					}
					else
					{
						data.add(StringUtils.safeToString(value));
					}
				}
				else
				{
					table.getRow(node.getSUID()).set(colName, value);
				}
			}
			catch (IllegalStateException e)
			{
				// class cast problem...
				// TODO...
				e.printStackTrace();
			}
		}
	}

	/* package */ CyNetwork createOrGetNetwork()
	{
		if (myNet == null)
		{
			myNet = adapter.getCyNetworkFactory().createNetwork();
			myNet.getRow(myNet).set(CyNetwork.NAME, cyNetworkNaming.getSuggestedNetworkTitle("General SPARQL"));
			adapter.getCyNetworkManager().addNetwork(myNet);
			createNodeAttributeIfNotExists("id");
			
			myView = null;
		}
		
		// create a network view if necessary (regardless of whether the network was just created
		if (myView == null) 
		{
			CyNetworkViewManager cyNetworkViewManager = adapter.getCyNetworkViewManager(); 
			myView = adapter.getCyNetworkViewFactory().createNetworkView(myNet);
			cyNetworkViewManager.addNetworkView(myView);
		}
		return myNet;
	}

	private VisualStyle createOrGetVisualStyle()
	{
		VisualMappingManager vmmServiceRef = adapter.getVisualMappingManager();
		
		if (_vs == null)
		{
			VisualStyleFactory visualStyleFactory = adapter.getVisualStyleFactory();

			VisualMappingFunctionFactory vmfFactoryC = adapter.getVisualMappingFunctionContinuousFactory();
			VisualMappingFunctionFactory vmfFactoryD = adapter.getVisualMappingFunctionDiscreteFactory();
			VisualMappingFunctionFactory vmfFactoryP = adapter.getVisualMappingFunctionPassthroughFactory();

			// To create a new VisualStyle object and set the mapping function
			_vs = visualStyleFactory.createVisualStyle(vmmServiceRef.getDefaultVisualStyle());
			_vs.setTitle ("General SPARQL visual style");

			_vs.setDefaultValue(BasicVisualLexicon.NODE_LABEL_FONT_SIZE, 18);
			_vs.setDefaultValue(BasicVisualLexicon.NODE_LABEL_COLOR, Color.GRAY);
			
			_vs.setDefaultValue(BasicVisualLexicon.NODE_FILL_COLOR, Color.GRAY);
//			_vs.setDefaultValue(BasicVisualLexicon.EDGE_PAINT, new Color (153, 153, 153));
			_vs.setDefaultValue(BasicVisualLexicon.EDGE_TRANSPARENCY, 128);
			_vs.setDefaultValue(BasicVisualLexicon.EDGE_WIDTH, 5.0);
			_vs.setDefaultValue(BasicVisualLexicon.NODE_LABEL_FONT_FACE, new Font(Font.MONOSPACED, Font.BOLD, 18));
			
			DiscreteMapping<String, NodeShape> typeMapping = (DiscreteMapping<String, NodeShape>)
					vmfFactoryD.createVisualMappingFunction("type", String.class, BasicVisualLexicon.NODE_SHAPE);
			
			typeMapping.putMapValue("Protein", NodeShapeVisualProperty.ROUND_RECTANGLE);
			typeMapping.putMapValue("gene", NodeShapeVisualProperty.PARALLELOGRAM);
			typeMapping.putMapValue("reaction", NodeShapeVisualProperty.RECTANGLE);
			typeMapping.putMapValue("SmallMolecule", NodeShapeVisualProperty.ELLIPSE);
			typeMapping.putMapValue("disease", NodeShapeVisualProperty.HEXAGON);
			typeMapping.putMapValue("pathway", NodeShapeVisualProperty.TRIANGLE);
			typeMapping.putMapValue("GO", NodeShapeVisualProperty.DIAMOND);
			
			_vs.addVisualMappingFunction(typeMapping);

			DiscreteMapping<String, Paint> colorMapping = (DiscreteMapping<String, Paint>)
					vmfFactoryD.createVisualMappingFunction("type", String.class, BasicVisualLexicon.NODE_FILL_COLOR);
			
			colorMapping.putMapValue("Protein", new Color (153, 153, 255));
			colorMapping.putMapValue("gene", new Color (153, 153, 255));
			colorMapping.putMapValue("reaction", Color.BLUE);
			colorMapping.putMapValue("SmallMolecule", new Color (255, 153, 153));
			colorMapping.putMapValue("disease", new Color (153, 255, 255));
			colorMapping.putMapValue("pathway", new Color (153, 255, 153));
			colorMapping.putMapValue("GO", new Color (255, 255, 153));
			
			_vs.addVisualMappingFunction(colorMapping);

			DiscreteMapping<String, Double> sizeMapping = (DiscreteMapping<String, Double>)
					vmfFactoryD.createVisualMappingFunction("type", String.class, BasicVisualLexicon.NODE_SIZE);
			
			sizeMapping.putMapValue("Protein", 40.0);
			sizeMapping.putMapValue("gene", 40.0);
			sizeMapping.putMapValue("reaction", 20.0);
			sizeMapping.putMapValue("SmallMolecule", 40.0);
			sizeMapping.putMapValue("disease", 30.0);
			sizeMapping.putMapValue("pathway", 30.0);
			sizeMapping.putMapValue("GO", 30.0);
			
			_vs.addVisualMappingFunction(sizeMapping);

			DiscreteMapping<String, Double> borderWidthMapping = (DiscreteMapping<String, Double>)
					vmfFactoryD.createVisualMappingFunction("type", String.class, BasicVisualLexicon.NODE_BORDER_WIDTH);
			
			borderWidthMapping.putMapValue("Protein", 10.0);
			borderWidthMapping.putMapValue("gene", 10.0);
			borderWidthMapping.putMapValue("reaction", 0.1);
			borderWidthMapping.putMapValue("SmallMolecule", 0.1);
			borderWidthMapping.putMapValue("disease", 0.1);
			borderWidthMapping.putMapValue("pathway", 0.1);
			borderWidthMapping.putMapValue("GO", 0.1);
			
			_vs.addVisualMappingFunction(borderWidthMapping);

			DiscreteMapping<String, Paint> borderColorMapping = (DiscreteMapping<String, Paint>)
					vmfFactoryD.createVisualMappingFunction("species", String.class, BasicVisualLexicon.NODE_BORDER_PAINT);
			
			borderColorMapping.putMapValue("Homo sapiens", Color.MAGENTA);
			borderColorMapping.putMapValue("Mus musculus", Color.CYAN);
			
			_vs.addVisualMappingFunction(borderColorMapping);

			
			PassthroughMapping<String, String> labelMapping = (PassthroughMapping<String, String>)vmfFactoryP.createVisualMappingFunction("name", String.class, BasicVisualLexicon.NODE_LABEL);
			_vs.addVisualMappingFunction(labelMapping);

			// the following code positions the label south-east of the node.
			// See: https://groups.google.com/forum/#!topic/cytoscape-discuss/xnWYwIbU4eo
			VisualLexicon lex = adapter.getRenderingEngineManager().getDefaultVisualLexicon();
		    VisualProperty prop = lex.lookup(CyNode.class, "NODE_LABEL_POSITION");
		    if (prop != null)
		    {
			    Object value = prop.parseSerializableString("SE,N,c,0.0,5.0"); // Put the north of the label on the southeast corner of the node
			    _vs.setDefaultValue(prop, value);
		    }
//			PassthroughMapping<String, String> edgeLabelMapping = (PassthroughMapping<String, String>)vmfFactoryP.createVisualMappingFunction("provenance", String.class, BasicVisualLexicon.EDGE_LABEL);
//			_vs.addVisualMappingFunction(edgeLabelMapping);

			// Add the new style to the VisualMappingManager
			vmmServiceRef.addVisualStyle(_vs);
			
		}
		vmmServiceRef.setVisualStyle(_vs, myView);
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
	
	private void flushView()
	{
		CyEventHelper eventHelper = adapter.getCyEventHelper();
		eventHelper.flushPayloadEvents(); // will cause node views to be created...
		
		if (myView != null) // myView could be null if the first query returned zero results, so nodes were created. This is not a bug.
		{
			VisualStyle vs = createOrGetVisualStyle();
			vs.apply(myView);
			myView.updateView();
		}
	}

	@Override
	public JFrame getFrame()
	{
		return null; //TODO - what is the root frame?
	}
	
	@Override
	public void setProject(MarrsProject newProject)
	{
		activator.updateSearchMenu();
	}

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
	protected void finalizeNetworkAddition(final Set<CyNode> nodesAdded,
			final Set<CyEdge> edgesPostPoned) 
	{
		if (nodesAdded.size() == 0) return;
		
		// necessary to flush events, otherwise there are no views to lay out.
		CyEventHelper eventHelper = adapter.getCyEventHelper();
		eventHelper.flushPayloadEvents(); // will cause node views to be created...
		
		// two possible layout methods. Hand-coded or using official cytoscape layouts. 
		// tried several official layouts, the only that seems to work well enough is "grid".
		
		doSimpleGridLayout(nodesAdded);
		// doOffficialLayout(nodesAdded);
		copyNodeCoordinates(); // TODO - merge with simple grid layout... 
		
		myView.updateView();
		// TODO Auto-generated method stub

		flushView();
	}

	/*
	private void doOffficialLayout(Set<CyNode> nodesAdded) 
	{
		Set<View<CyNode>> viewSet = new HashSet<View<CyNode>>();
		
		for (CyLayoutAlgorithm i : adapter.getCyLayoutAlgorithmManager().getAllLayouts())
		{
			System.out.println (i.getName());
		}
		
		CyLayoutAlgorithm layout = adapter.getCyLayoutAlgorithmManager().getLayout("grid");
		if (layout == null) layout = adapter.getCyLayoutAlgorithmManager().getDefaultLayout();
		
		assert (layout != null);
		for (CyNode node : nodesAdded)
		{
			View<CyNode> view = myView.getNodeView(node);
			viewSet.add(view);
		}
		TaskIterator itr = layout.createTaskIterator(myView, layout.getDefaultLayoutContext(), viewSet, null);

		adapter.getTaskManager().execute(itr);
		
		SynchronousTaskManager<?> synTaskMan = adapter.getCyServiceRegistrar().getService(SynchronousTaskManager.class);
		synTaskMan.execute(itr);
	}
	*/
	
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

	private void doSimpleGridLayout(Set<CyNode> nodesAdded) 
	{		
		double x = 0;
		double y = 0;
		
		int rowSize = (int)Math.ceil(Math.sqrt (nodesAdded.size()));
		int count = 0;
		
		final double INTERDISTANCE = 50;
		
		int missingCount = 0;
		
		for (CyNode node : nodesAdded)
		{
			View<CyNode> nodeView = myView.getNodeView(node);

			if (nodeView != null)
			{
				nodeView.setVisualProperty(BasicVisualLexicon.NODE_X_LOCATION, x);
				nodeView.setVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION, y);
			}
			else
			{
				missingCount++;
			}
			
			if ((++count) % rowSize == 0)
			{
				x = 0;
				y += INTERDISTANCE;
			}
			else
			{
				x += INTERDISTANCE;
			}
		}

		// TODO what strange magic is this? - even though we always call flushPayloadEvents, some
		// node views seem not to be created yet at this time.
		if (missingCount > 0) System.out.println (missingCount + " node views were not yet created...");
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
