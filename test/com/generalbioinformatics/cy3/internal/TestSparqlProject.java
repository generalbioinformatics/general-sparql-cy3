package com.generalbioinformatics.cy3.internal;

import java.io.IOException;
import java.io.InputStream;

import org.jdom.JDOMException;
import org.xml.sax.InputSource;

import com.generalbioinformatics.rdf.TripleFile;
import com.generalbioinformatics.rdf.TripleStore;
import com.generalbioinformatics.rdf.gui.MarrsException;
import com.generalbioinformatics.rdf.gui.MarrsProject;
import com.generalbioinformatics.rdf.gui.MarrsQuery;

import junit.framework.TestCase;
import nl.helixsoft.recordstream.Record;
import nl.helixsoft.recordstream.RecordStream;
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
	
	private TripleStore con;
	
	public void setUp()
	{
		con = new TripleFile();
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

			String label = "Project query #" + i + ": " + q.getTitle();
			
			int count = 0;
			RecordStream rs = con.sparqlSelect(q1);
			for (Record r : rs)
			{
				count++;
			}
			assertTrue (label + " returned 0 results", count > 0);			
		}
	}

}
