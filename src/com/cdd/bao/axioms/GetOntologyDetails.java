package com.cdd.bao.axioms;

import java.util.*;
import java.io.*;

import org.apache.commons.lang3.*;
import org.apache.jena.ontology.AllValuesFromRestriction;
import org.apache.jena.ontology.CardinalityRestriction;
import org.apache.jena.ontology.DatatypeProperty;
import org.apache.jena.ontology.MaxCardinalityRestriction;
import org.apache.jena.ontology.MinCardinalityRestriction;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntologyException;
import org.apache.jena.ontology.Restriction;
import org.apache.jena.ontology.SomeValuesFromRestriction;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDFS;

import com.cdd.bao.template.*;
import com.cdd.bao.util.*;
import com.cdd.bao.editor.*;

/*
	This class is to read axioms into a file and group them based on class
	so it should have the structure
	class :: {axiom_prop1 obj_1, axiom_prop2 obj2, axiom_prop3 obj3, .. , axiom_n obj_n}

    after this class is used, we should be able to get the value (from annotations) 
    based on that value, get the key , i.e. class , and get all the other values in the set
    in order to predict the rest for the annotations.
	      
*/

public class GetOntologyDetails 
{
	
	
	 static Map<String, String> propToLabel = new HashMap<String, String>();
	 static Map<String, String> propToLabel2 = new HashMap<String, String>();
	
	 
	private String nameNode(RDFNode node)
	{
		if (node == null) return "{null}";
		if (node.isLiteral()) return "\"" + node.asLiteral().getLexicalForm() + "\"";
		if (node.isAnon()) return "{anon}";
		if (node.isURIResource()) return nameURI(node.asResource().getURI());
		return "{???}";
	}
	private String nameURI(String uri)
	{
		if (uri == null) throw new NullPointerException();
		String label = propToLabel2.get(uri), abbrev = ModelSchema.collapsePrefix(uri);
		String name = label == null ? "" : label + " ";
		return name + "<" + abbrev + ">";
	}
	 public static void ontologyReader() throws OntologyException 
	 { 
	
		 OntModel ontology; 
		 String baseURI; 
		 InputStream in = null;
		 int forAllCounter = 0;
		 int forSomeCounter = 0;
		 int cardinalityCounter =0;
		 int maxCardinalityCounter =0;
		 int minCardinalityCounter =0;
		 int hasInverseCounter =0;
		 Restriction restriction = null;
	 
	 //assayKit, bioassay, bioassaySpecification, biologicalProcess, modeOfAction, molecular function
	 String irrelevantIRIs[] = {"http://www.bioassayontology.org/bao#BAO_0000248",
			 "http://www.bioassayontology.org/bao#BAO_0000015", 
			 "http://www.bioassayontology.org/bao#BAO_0000026",
			 "http://www.bioassayontology.org/bao#BAO_0000264",
			 "http://www.bioassayontology.org/bao#BAO_0000074",
			 "http://www.bioassayontology.org/bao#BAO_0002202",
			 "http://www.bioassayontology.org/bao#BAO_0003075"};
	
	 Map<String, String> uriToLabel2 = new HashMap<String, String>();//uriToLabel2 = Vocabulary.uriToLabel;
			 
	 try 
	  {  
		   //ontology path
		   String path = "/Users/handemcginty/home/"; 
		   
		
		   in = new FileInputStream(path + "AZ_BAE.owl"); 
	
		   ontology = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF); 
		   ontology = (OntModel) ontology.read(in, "http://rdf.astrazeneca.com/astrazeneca_bae.owl");  
		   in.close(); 
		   System.out.println("Read AZ ontology"); 

	   }catch (Exception e) 
	   { 
		    System.out.println("Unable to find specified file in OntologyReader"); 
		    try 
		    { 
		     in.close(); 
		    } 
		    catch (Exception ex) 
		    {} 
		    throw(new OntologyException(e.getMessage())); 
	   } 	
		
		Bag triples = new Bag();
		
		Iterator classIt2 = ontology.listClasses();
		
		while (classIt2.hasNext()) {	
			 OntClass ontClass1 = (OntClass) classIt2.next();
			 NodeIterator labels = ontClass1.listPropertyValues(RDFS.label);
			 while (labels.hasNext()){
				 RDFNode labelNode = labels.next();
				 Literal label = labelNode.asLiteral();
				 uriToLabel2.put(ontClass1.getURI(), label.toString());
		     } 
		}
		

		int numObjProp = 0;
	
		 for (Iterator<OntProperty> it = ontology.listOntProperties(); it.hasNext();)
			{
				OntProperty ontProp = it.next();
				
				long timeNow = new Date().getTime();
				propToLabel2.put(ontProp.getURI(), ontProp.getLabel(ontProp.getURI()));

				if (ontProp.hasInverse()) hasInverseCounter++;

				for (NodeIterator labels = ontProp.listPropertyValues(RDFS.label); labels.hasNext();)
				{
					RDFNode labelNode = labels.next();
					Literal label = labelNode.asLiteral();
					propToLabel.put(ontProp.getURI(), label.getString());
				}
				numObjProp++;
			}
		 
		 int numDataProp = 0;
		 for (Iterator<DatatypeProperty> it = ontology.listDatatypeProperties(); it.hasNext();)
			{
			 DatatypeProperty ontProp = it.next();
			 propToLabel2.put(ontProp.getURI(), ontProp.getLabel(ontProp.getURI()));
				
				long timeNow = new Date().getTime();
				

				if (ontProp.hasInverse()) hasInverseCounter++;

				for (NodeIterator labels = ontProp.listPropertyValues(RDFS.label); labels.hasNext();)
				{
					RDFNode labelNode = labels.next();
					Literal label = labelNode.asLiteral();
					propToLabel.put(ontProp.getURI(), label.getString());
				}
				numDataProp++;
			}
		 Property propLabel = ontology.createProperty(ModelSchema.PFX_RDFS + "label");
			for (StmtIterator iter = ontology.listStatements(null, propLabel, (RDFNode)null); iter.hasNext();)
			{
				Statement stmt = iter.next();
				Resource subject = stmt.getSubject();
				RDFNode object = stmt.getObject();
				if (subject.isURIResource() && object.isLiteral()) propToLabel.put(propLabel.getURI(), propLabel.toString());
			}
		 
		

		Iterator classIt = ontology.listClasses();
		while (classIt.hasNext()) {
	
			OntClass ontClass1 = (OntClass) classIt.next(); 
			
			for(Iterator<OntClass> i = ontClass1.listSuperClasses();i.hasNext();){
				 OntClass c = i.next();
				 if(c.isRestriction()){ //go over each axiom of a particular class and put the class and axioms to the bag
					 Restriction r = c.asRestriction(); //restriction == axiom
					 if(r.isAllValuesFromRestriction()){ // only axioms
						 AllValuesFromRestriction av = r.asAllValuesFromRestriction();
						 OntProperty p = (OntProperty)av.getOnProperty();
						 OntClass c3 = (OntClass)av.getAllValuesFrom();
						 if(uriToLabel2.get(p.getURI()) != null && c3.getURI() != null){
						  triples.put("\n"+"\t " +"class " + uriToLabel2.get(ontClass1.getURI()), " \n" +" on property " + uriToLabel2.get(p.getURI()) + "\n "+ " all values from class " +  uriToLabel2.get(c3.getURI()) +"\n");
						  forAllCounter++;
						 }else if(uriToLabel2.get(p.getURI()) == null || c3.getURI() == null)
						  {
						  triples.put("\n"+"\t " +"class " + uriToLabel2.get(ontClass1.getURI()), " \n" +" on property " + p.getURI() + "\n "+ " all values from class " +  c3.getURI() +"\n");
						  forAllCounter++;
						  }
						
						 // System.out.println("All values from class" + av.getAllValuesFrom().getURI() + "on property" + av.getOnProperty().getURI());
					  }else if(r.isSomeValuesFromRestriction()){
						 SomeValuesFromRestriction av = r.asSomeValuesFromRestriction();
						 OntProperty p = (OntProperty)av.getOnProperty();
						 OntClass c2 = (OntClass)av.getSomeValuesFrom();
						 if(c2.getURI() != null){
							/* triples.put("class " + uriToLabel2.get(ontClass1.getURI()), 
									 "on property " + uriToLabel2.get(p.getURI())+ 
									 " some values from class " + uriToLabel2.get(c2.getURI()) + "\n");*/
							 forSomeCounter++;
							 
					     }
					  }else if(r.isMaxCardinalityRestriction()){
						 MaxCardinalityRestriction av = r.asMaxCardinalityRestriction();
						 OntProperty p = (OntProperty)av.getOnProperty();
						 
						
							 triples.put("class " + uriToLabel2.get(ontClass1.getURI()), 
									 "on property " + uriToLabel2.get(p.getURI())+ 
									  "\n");
							 maxCardinalityCounter++;
							 
						 
					   }else if(r.isMinCardinalityRestriction()){
							 MinCardinalityRestriction av = r.asMinCardinalityRestriction();
							 OntProperty p = (OntProperty)av.getOnProperty();
							 
							
								 triples.put("class " + uriToLabel2.get(ontClass1.getURI()), 
										 "on property " + uriToLabel2.get(p.getURI())+ 
										  "\n");
								 minCardinalityCounter++;
								 
							 
						   }
					  else if(r.isCardinalityRestriction()){
						 CardinalityRestriction av = r.asCardinalityRestriction();
						 OntProperty p = (OntProperty)av.getOnProperty();
						// OntClass c2 = (OntResoure)av.getCardinality(p);
						 int cardinality = av.getCardinality();
						// if(c2.getURI() != null){
							 triples.put("class " + uriToLabel2.get(ontClass1.getURI()), 
									 "on property " + uriToLabel2.get(p.getURI())+ 
									 /*" some values from class " + uriToLabel2.get(c2.getURI()) + */ "\n");
							 cardinalityCounter++;
							 
						// }
						 /*triples.put("class " + ontClass1.getURI(), 
								 "on property " + p.getLocalName()+ 
								 " some values from class " + c2.getLocalName() + "\n");*/
						// System.out.println("Some values from class" + av.getSomeValuesFrom().getURI() + "on property" + av.getOnProperty().getURI());
					 }
				 }
			   }
			 
			 
			 
			 }
			 
			System.out.println(""+triples.toString());

			System.out.println("the triples: " + triples.toString());
			System.out.println("for all axioms: " + forAllCounter);
			System.out.println("for some axioms: " + forSomeCounter);
			System.out.println("for max axioms: " + maxCardinalityCounter);
			System.out.println("for min axioms: " + minCardinalityCounter);
			System.out.println("for exactly axioms: " + cardinalityCounter);
			System.out.println("properties with inverse: " + hasInverseCounter);
			System.out.println("Properties and Labels:" + propToLabel.toString()+"\n numObjProp = "+numObjProp + "numDataProp = " + numDataProp);
			System.out.println("Properties and Labels2:" + propToLabel2.toString());
			try{
			    PrintWriter writer = new PrintWriter("axiomsAndCounts.txt", "UTF-8");
			    writer.println(""+triples.toString());

				//writer.println("the triples: " + triples.toString());
			    Iterator axiomIter = triples.keySetIter();
			    while(axiomIter.hasNext()){
			    	String key = (String) axiomIter.next();
			    	writer.println("\n" +"\t"+ key + "\n\n" + (triples.getValues(key)).toString()+"\n");
			    }
				writer.println("for all axioms: " + forAllCounter);
				writer.println("for some axioms: " + forSomeCounter);
				writer.println("for max axioms: " + maxCardinalityCounter);
				writer.println("for min axioms: " + minCardinalityCounter);
				writer.println("for exactly axioms: " + cardinalityCounter);
				writer.println("properties with inverse: " + hasInverseCounter);
				writer.println("Properties and Labels:" + propToLabel.toString());
			    
			    writer.close();
			} catch (IOException e) {
			   // do something
			}
	
	 }
}
