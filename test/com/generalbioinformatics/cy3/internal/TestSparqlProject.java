package com.generalbioinformatics.cy3.internal;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.jdom.JDOMException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.xml.sax.InputSource;

import com.generalbioinformatics.rdf.TripleFile;
import com.generalbioinformatics.rdf.TripleStore;
import com.generalbioinformatics.rdf.gui.MarrsException;
import com.generalbioinformatics.rdf.gui.MarrsProject;
import com.generalbioinformatics.rdf.gui.MarrsQuery;

import nl.helixsoft.recordstream.Record;
import nl.helixsoft.recordstream.RecordStream;
import nl.helixsoft.recordstream.Supplier;

@RunWith(Parameterized.class)
public class TestSparqlProject
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
	
	private TripleStore con;
	private final MarrsProject project;
	private final MarrsQuery q;
	private final int i;
	
	public TestSparqlProject (Integer i, MarrsProject project, MarrsQuery query, String title)
	{
		this.project = project;
		this.q = query;
		this.i = i;
	}
	
	@Before
	public void setUp() throws JDOMException, IOException
	{
		con = new TripleFile();
	}

	private static MarrsProject getMarrsProject() throws JDOMException, IOException
	{
		InputStream is = TestSparqlProject.class.getResourceAsStream("/com/generalbioinformatics/cy3/internal/project.xml");
		MarrsProject result = MarrsProject.createFromFile(new InputSource(is));
		return result;
	}
	
	@Parameters(name = "{index}: {3}")
	public static Iterable<Object[]> parameters() throws JDOMException, IOException 
	{
		List<Object[]> result = new ArrayList<Object[]>();
		MarrsProject project = getMarrsProject();
		for (int i = 0; i < project.getRowCount(); ++i)
		{
			result.add(new Object[] { i, project, project.getRow(i), project.getRow(i).getTitle() } );
		}
		return result;
	}
	
	@Test
	public void testQuery() throws JDOMException, IOException, MarrsException
	{
		String key = q.getTestKey();
		String val = q.getTestValue();
		if (key != null && val != null)
		{
			project.setQueryParameter(key, val);
		}

		System.out.println("Project query #" + i + ": " + q.getTitle());
		String q1 = project.getSubstitutedQuery(q);
		System.out.println (q1);

		String label = "Project query #" + i + ": " + q.getTitle();
		
		int count = 0;
		RecordStream rs = con.sparqlSelect(q1);
		for (Record r : rs)
		{
			System.out.println (r);
			count++;
		}
		assertTrue (label + " returned 0 results", count > 0);			

	}

}
