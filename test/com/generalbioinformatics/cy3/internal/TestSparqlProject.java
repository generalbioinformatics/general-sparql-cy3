package com.generalbioinformatics.cy3.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import javax.swing.JFrame;

import org.jdom.JDOMException;
import org.xml.sax.InputSource;

import com.generalbioinformatics.rdf.TripleFile;
import com.generalbioinformatics.rdf.TripleStore;
import com.generalbioinformatics.rdf.gui.AbstractMarrsMapper;
import com.generalbioinformatics.rdf.gui.MarrsException;
import com.generalbioinformatics.rdf.gui.MarrsMapper;
import com.generalbioinformatics.rdf.gui.MarrsProject;
import com.generalbioinformatics.rdf.gui.MarrsQuery;

import junit.framework.TestCase;
import nl.helixsoft.recordstream.Supplier;

public class TestSparqlProject extends TestCase 
{
	
	class MockTripleStoreManager implements Supplier<TripleStore> {
		
		private TripleStore tf;
		MockTripleStoreManager (TripleStore tf) {
			this.tf = tf;		
		}
		
		@Override
		public TripleStore get() {
			return tf;
		}
	
	}
	
	class MockMarrsMapper extends AbstractMarrsMapper<String, String>
	{
		public int nodeCount = 0;
		public int edgeCount = 0;
		public int attributeCount = 0;
		
		public void clear()
		{
			nodeCount = 0;
			edgeCount = 0;
			attributeCount = 0;
		}
		
		protected MockMarrsMapper(Supplier<TripleStore> conMgr) {
			super(conMgr);
		}

		@Override
		protected void setNodeAttribute(String node, String key, Object val) 
		{
			attributeCount++;			
		}

		@Override
		protected void setEdgeAttribute(String edge, String key, Object val) {
						
		}

		@Override
		protected void finalizeNetworkAddition(Set<String> nodesAdded, Set<String> edgesPostPoned) 
		{
			// TODO Auto-generated method stub	
		}

		@Override
		protected String createNodeIfNotExists(String nodeId, Set<String> nodesAdded) {
			nodeCount++;			
			return null;
		}

		@Override
		protected String createEdgeIfNotExists(String nSrc, String nDest, String interaction,
				Set<String> edgesPostponed) {
			edgeCount++;
			return null;
		}

		@Override
		protected JFrame getFrame() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void setProject(MarrsProject project) {
			// TODO Auto-generated method stub
			
		}
	}
	
	private Supplier<TripleStore> tsm;
	private MockMarrsMapper mapper;
	
	public void setUp()
	{
		TripleStore tf = new TripleFile();
		tsm = new MockTripleStoreManager(tf);
		mapper = new MockMarrsMapper(tsm);
	}
	
	public void testQueries() throws JDOMException, IOException, MarrsException
	{
		InputStream is = TestSparqlProject.class.getResourceAsStream("/com/generalbioinformatics/cy3/internal/project.xml");
		MarrsProject project = MarrsProject.createFromFile(new InputSource(is));		
		
		for (int i = 0; i < project.getRowCount(); ++i)
		{
			MarrsQuery q = project.getQuery(i);
			String key = q.getTestKey();
			String val = q.getTestValue();
			if (key != null && val != null)
			{
				project.setQueryParameter(key, val);
			}

			System.out.println("Project query #" + i + ": " + q.getTitle());
			String q1 = project.getSubstitutedQuery(q);
			System.out.println (q1);

			mapper.clear();
			
			String label = "Project query #" + i + ": " + q.getTitle();
			switch (q.getQueryType())
			{
			case QUERY_BACKBONE:
				assertTrue (label + " returned 0 results", mapper.createNetwork(q1, q) > 0);
				assertTrue (label + " returned 0 results", mapper.edgeCount > 0);
				break;
			case QUERY_NODE_ATTRIBUTE:
				assertTrue (label + " returned 0 results", mapper.addAttributes(q1) > 0);
				assertTrue (label + " returned 0 results", mapper.nodeCount > 0);
				break;
			case QUERY_NODE_MATRIX:
				assertTrue (label + " returned 0 results", mapper.addAttributesMatrix(q1) > 0);
				assertTrue (label + " returned 0 results", mapper.nodeCount > 0);
				break;
			case QUERY_SEARCH:
				mapper.popupResults(q1);
				//TODO
				break;
			default:
				fail("Using unknown or deprecated query type: " + q.getQueryType());
			}
			
//			assertTrue ("Project query #" + i + ": " + q.getTitle() + " returned zero results", rs.iterator().hasNext());
		}
	}

}
