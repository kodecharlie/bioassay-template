/*
 * BioAssay Ontology Annotator Tools
 * 
 * (c) 2014-2015 Collaborative Drug Discovery Inc.
 */

package com.cdd.bao.template;

import com.cdd.bao.*;
import com.cdd.bao.template.Schema.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.*;

/*
	Model Schema: serialisation and deserialisation of a Schema using RDF triples. It can be used either to wrap the
	datastructure in a local file (using Turtle format), or to push & pull between a SPARQL endpoint.
*/

public class ModelSchema
{
	public static final String PFX_BAO = "http://www.bioassayontology.org/bao#"; // BioAssay Ontology
	public static final String PFX_BAT = "http://www.bioassayontology.org/bat#"; // BioAssay Template
	public static final String PFX_BAS = "http://www.bioassayontology.org/bas#"; // BioAssay Schema (used as the default)
	
	public static final String PFX_OBO = "http://purl.obolibrary.org/obo/";
	public static final String PFX_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	public static final String PFX_RDFS = "http://www.w3.org/2000/01/rdf-schema#";
	public static final String PFX_XSD = "http://www.w3.org/2001/XMLSchema#";
	public static final String PFX_OWL = "http://www.w3.org/2002/07/owl#";

	public static final String BAT_ROOT = "BioAssayTemplate"; // root should be one of these, as well as a group
	public static final String BAT_ASSAY = "BioAssayDescription"; // there should be zero-or-more of these in the schema file
	
	// a group is made up of groups & assignments
	public static final String BAT_GROUP = "Group";
	public static final String HAS_GROUP = "hasGroup";
	public static final String BAT_ASSIGNMENT = "Assignment";
	public static final String HAS_ASSIGNMENT = "hasAssignment";

	public static final String HAS_DESCRIPTION = "hasDescription"; // longwinded version of rdf:label
	public static final String IN_ORDER = "inOrder"; // each group/assignment can have one of these
	
	public static final String HAS_PROPERTY = "hasProperty"; // maps to predicate (one per assignment)
	public static final String HAS_VALUE = "hasValue"; // contains a value option (many per assignment)	
	public static final String MAPS_TO = "mapsTo";

	public static final String HAS_PARAGRAPH = "hasParagraph"; // text description of the assay, if available
	public static final String HAS_ORIGIN = "hasOrigin"; // origin URI: where the assay came from
	public static final String USES_TEMPLATE = "usesTemplate"; // linking an assay description to a template
	
	public static final String HAS_ANNOTATION = "hasAnnotation"; // connecting an annotation to an assay
	public static final String IS_ASSIGNMENT = "isAssignment"; // connecting an annotation to an assignment
	public static final String HAS_LITERAL = "hasLiteral"; // used for annotations

	private Vocabulary vocab; // local instance of the BAO ontology: often initialised on demand/background thread
	private int watermark = 1; // autogenned next editable identifier

	private Property rdfLabel, rdfType;
	private Resource batRoot, batAssay;
	private Resource batGroup, batAssignment;
	private Property hasGroup, hasAssignment;
	private Property hasDescription, inOrder, hasParagraph, hasOrigin, usesTemplate;
	private Property hasProperty, hasValue;
	private Property mapsTo;
	private Property hasAnnotation, isAssignment, hasLiteral;

	// data used only during serialisation
	private Map<String, Integer> nameCounts; // ensures no name clashes
	private Map<Assignment, Resource> assignmentToResource; // stashes the model resource per assignment
	private Map<Resource, Assignment> resourceToAssignment; // or vice versa for loading

	private Schema schema;
	private Model model;

	// ------------ private data: content ------------	

	private ModelSchema(Schema schema, Model model)
	{
		this.schema = schema;
		this.model = model;

		setupResources();
	}
	
	// access to the content
	public Schema getSchema() {return schema;}
	
	// load in previously saved file
	public static Schema deserialise(File file) throws IOException
	{
		FileInputStream istr = new FileInputStream(file);
		Schema schema = deserialise(istr);
		istr.close();
		return schema;
	}
	public static Schema deserialise(InputStream istr) throws IOException
	{
		Model model = ModelFactory.createDefaultModel();
		try {RDFDataMgr.read(model, istr, Lang.TTL);}
		catch (Exception ex) {throw new IOException("Failed to parse schema", ex);}

		ModelSchema thing = new ModelSchema(new Schema(), model);
		thing.parseFromModel();
		return thing.getSchema();
	}
	
	// serialisation: writes the schema using RDF "turtle" format, using OWL classes
	public static void serialise(Schema schema, File file) throws IOException
	{
		BufferedOutputStream ostr = new BufferedOutputStream(new FileOutputStream(file));
		serialise(schema, ostr);
		ostr.close();
	}
	public static void serialise(Schema schema, OutputStream ostr) throws IOException
	{
		Model model = ModelFactory.createDefaultModel();
		ModelSchema thing = new ModelSchema(schema, model);
		thing.exportToModel();
		
		RDFDataMgr.write(ostr, model, RDFFormat.TURTLE);
	}
	
	// ------------ private methods ------------	

	private void setupResources()
	{
		model.setNsPrefix("bao", ModelSchema.PFX_BAO);
		model.setNsPrefix("bat", ModelSchema.PFX_BAT);
		model.setNsPrefix("bas", schema.getSchemaPrefix());
		model.setNsPrefix("obo", ModelSchema.PFX_OBO);
		model.setNsPrefix("rdfs", ModelSchema.PFX_RDFS);
		model.setNsPrefix("xsd", ModelSchema.PFX_XSD);
		model.setNsPrefix("rdf", ModelSchema.PFX_RDF);

		rdfLabel = model.createProperty(PFX_RDFS + "label");
		rdfType = model.createProperty(PFX_RDF + "type");

		batRoot = model.createResource(PFX_BAT + BAT_ROOT);
		batAssay = model.createResource(PFX_BAT + BAT_ASSAY);
		batGroup = model.createResource(PFX_BAT + BAT_GROUP);
		batAssignment = model.createResource(PFX_BAT + BAT_ASSIGNMENT);
		hasGroup = model.createProperty(PFX_BAT + HAS_GROUP);
		hasAssignment = model.createProperty(PFX_BAT + HAS_ASSIGNMENT);
		hasDescription = model.createProperty(PFX_BAT + HAS_DESCRIPTION);
		inOrder = model.createProperty(PFX_BAT + IN_ORDER);
		hasProperty = model.createProperty(PFX_BAT + HAS_PROPERTY);
		hasValue = model.createProperty(PFX_BAT + HAS_VALUE);
		mapsTo = model.createProperty(PFX_BAT + MAPS_TO);
		hasParagraph = model.createProperty(PFX_BAT + HAS_PARAGRAPH);
		hasOrigin = model.createProperty(PFX_BAT + HAS_ORIGIN);
		usesTemplate = model.createProperty(PFX_BAT + USES_TEMPLATE);
		hasAnnotation = model.createProperty(PFX_BAT + HAS_ANNOTATION);
		isAssignment = model.createProperty(PFX_BAT + IS_ASSIGNMENT);
		hasLiteral = model.createProperty(PFX_BAT + HAS_LITERAL);
		
		nameCounts = new HashMap<>();
		assignmentToResource = new HashMap<>();
	}

	private void exportToModel()
	{
		Group root = schema.getRoot();
		String pfx = schema.getSchemaPrefix();
	
		Resource objRoot = model.createResource(pfx + turnLabelIntoName(root.name));
		model.add(objRoot, rdfType, batRoot);
		model.add(objRoot, rdfType, batGroup);
		model.add(objRoot, rdfLabel, root.name);
		if (root.descr.length() > 0) model.add(objRoot, hasDescription, root.descr);
		
		formulateGroup(objRoot, root);

		for (int n = 0; n < schema.numAssays(); n++)
		{
			Assay assay = schema.getAssay(n);
			Resource objAssay = model.createResource(pfx + turnLabelIntoName(assay.name));
			model.add(objAssay, rdfType, batAssay);
			model.add(objAssay, rdfLabel, assay.name);
			model.add(objAssay, usesTemplate, objRoot);
			if (assay.descr.length() > 0) model.add(objAssay, hasDescription, assay.descr);
			if (assay.para.length() > 0) model.add(objAssay, hasParagraph, assay.para);
			if (assay.originURI.length() > 0) model.add(objAssay, hasOrigin, assay.originURI);
			model.add(objAssay, inOrder, model.createTypedLiteral(n + 1));
			
			for (int i = 0; i < assay.annotations.size(); i++)
			{
				Annotation annot = assay.annotations.get(i);
			
				Resource blank = model.createResource();
				model.add(objAssay, hasAnnotation, blank);

				// looks up the assignment in the overall hierarchy, to obtain the recently-created URI
				Assignment assn = schema.findAssignment(annot);
				if (assn != null) model.add(blank, isAssignment, assignmentToResource.get(assn));

				// emits either value or literal, with any accompanying decoration
				if (annot.value != null)
				{
					model.add(blank, hasProperty, model.createResource(annot.assn.propURI)); // note: using propURI stored in its own linear branch
					model.add(blank, hasValue, model.createResource(annot.value.uri));
					if (annot.value.name.length() > 0) model.add(blank, rdfLabel, model.createLiteral(annot.value.name));
					if (annot.value.descr.length() > 0) model.add(blank, hasDescription, model.createLiteral(annot.value.descr));
				}
				else
				{
					model.add(blank, hasLiteral, model.createLiteral(annot.literal));
				}
			}
		}
	}

	private void formulateGroup(Resource objParent, Group group)
	{
		int order = 0;
		String pfx = schema.getSchemaPrefix();
		
 		for (Assignment assn : group.assignments)
		{
			String name = turnLabelIntoName(assn.name);
						
			Resource objAssn = model.createResource(pfx + name);
			model.add(objParent, hasAssignment, objAssn);
			model.add(objAssn, rdfType, batAssignment);
			model.add(objAssn, rdfLabel, assn.name);
			if (assn.descr.length() > 0) model.add(objAssn, hasDescription, assn.descr);
			model.add(objAssn, inOrder, model.createTypedLiteral(++order));
			model.add(objAssn, hasProperty, model.createResource(assn.propURI));
			
			int vorder = 0;
			for (Value val : assn.values)
			{
				Resource blank = model.createResource();		
				model.add(objAssn, hasValue, blank);
				
				Resource objValue = val.uri == null ? null : model.createResource(val.uri);
				if (objValue != null) model.add(blank, mapsTo, objValue);
				model.add(blank, rdfLabel, model.createLiteral(val.name));
				if (val.descr.length() > 0) model.add(blank, hasDescription, model.createLiteral(val.descr));
				model.add(blank, inOrder, model.createTypedLiteral(++vorder));
			}
			
			assignmentToResource.put(assn,  objAssn); // for subsequent retrieval
		}
		
		// recursively emit any subgroups
		String parentName = turnLabelIntoName(group.name);
		for (Group subgrp : group.subGroups)
		{
    		Resource objGroup = model.createResource(pfx + turnLabelIntoName(subgrp.name));
    		model.add(objParent, hasGroup, objGroup);
    		model.add(objGroup, rdfType, batGroup);
    		model.add(objGroup, rdfLabel, subgrp.name);
    		if (subgrp.descr.length() > 0) model.add(objGroup, hasDescription, subgrp.descr);
			model.add(objGroup, inOrder, model.createTypedLiteral(++order));
    		
    		formulateGroup(objGroup, subgrp);
		}
	}

	// pull in an RDF-compatible file, and pull out the model information
	private void parseFromModel() throws IOException
	{
		// extract the template
		Resource objRoot = null;
		for (StmtIterator it = model.listStatements(null, rdfType, batRoot); it.hasNext();)
		{
			objRoot = it.next().getSubject();
			break;
		}
		if (objRoot == null) throw new IOException("No template root found: this is probably not a bioassay template file.");
		
		String rootURI = objRoot.toString();
		int pfxsz = rootURI.lastIndexOf('#');
		if (pfxsz > 0) schema.setSchemaPrefix(rootURI.substring(0, pfxsz + 1));

		Group root = new Group(null, findString(objRoot, rdfLabel));
		schema.setRoot(root);
		root.descr = findString(objRoot, hasDescription);
		
		resourceToAssignment = new HashMap<>();
		
		parseGroup(objRoot, root);

		// extract each of the assays
		Map<Object, Integer> order = new HashMap<>();
		List<Assay> assayList = new ArrayList<>();
		for (StmtIterator it = model.listStatements(null, rdfType, batAssay); it.hasNext();)
		{
			Resource objAssay = it.next().getSubject();
			
			Assay assay = parseAssay(objAssay);
			assayList.add(assay);
			order.put(assay, findInteger(objAssay, inOrder));
		}
		assayList.sort((a1, a2) -> order.get(a1).compareTo(order.get(a2)));
		for (Assay assay : assayList) schema.appendAssay(assay);
	}
	
	// for a given category node, pulls out and parses all of its assignments and subcategories
	private void parseGroup(Resource objParent, Group group) throws IOException
	{
		final Map<Object, Integer> order = new HashMap<>();
	
		// look for assignments
		for (StmtIterator it = model.listStatements(objParent, hasAssignment, (RDFNode)null); it.hasNext();)
		{
			Statement st = it.next();
			Resource objAssn = (Resource)st.getObject();
			
			//Util.writeln("Cat:"+category.categoryName+ " prop:"+clsProp.toString());
			
			Assignment assn = parseAssignment(group, objAssn);
			group.assignments.add(assn);
			order.put(assn, findInteger(objAssn, inOrder));
			
			resourceToAssignment.put(objAssn, assn);
		}
		group.assignments.sort((a1, a2) -> order.get(a1).compareTo(order.get(a2)));
		
		// look for subcategories
		order.clear();
		for (StmtIterator it = model.listStatements(objParent, hasGroup, (RDFNode)null); it.hasNext();)
		{
			Statement st = it.next();
			Resource objGroup = (Resource)st.getObject();
			
    		Group subgrp = new Group(group, findString(objGroup, rdfLabel));
    		subgrp.descr = findString(objGroup, hasDescription);
    		
    		group.subGroups.add(subgrp);
    		order.put(subgrp, findInteger(objGroup, inOrder));
    		
    		parseGroup(objGroup, subgrp);
		}
		group.subGroups.sort((a1, a2) -> order.get(a1).compareTo(order.get(a2)));
	}
	
	private Assignment parseAssignment(Group group, Resource objAssn) throws IOException
	{
		Assignment assn = new Assignment(group, findString(objAssn, rdfLabel), findAsString(objAssn, hasProperty));
		assn.descr = findString(objAssn, hasDescription);
		
		Map<Object, Integer> order = new HashMap<>();

		for (StmtIterator it = model.listStatements(objAssn, hasValue, (RDFNode)null); it.hasNext();)
		{
			Resource blank = (Resource)it.next().getObject();
					
			Value val = new Value(findAsString(blank, mapsTo), findString(blank, rdfLabel));
			val.descr = findString(blank, hasDescription);

			assn.values.add(val);
			order.put(val, findInteger(blank, inOrder));
		}

		assn.values.sort((a1, a2) -> order.get(a1).compareTo(order.get(a2)));

		return assn;
	}
	
	private Assay parseAssay(Resource objAssay)
	{
		Assay assay = new Assay(findString(objAssay, rdfLabel));
		
		assay.descr = findString(objAssay, hasDescription);
		assay.para = findString(objAssay, hasParagraph);
		assay.originURI = findString(objAssay, hasOrigin);
		
		for (StmtIterator it = model.listStatements(objAssay, hasAnnotation, (RDFNode)null); it.hasNext();)
		{
			Resource blank = (Resource)it.next().getObject();

			Resource assnURI = findResource(blank, isAssignment);
			String label = findString(blank, rdfLabel);
			String descr = findString(blank, hasDescription);
			Resource propURI = findResource(blank, hasProperty);
			Resource valueURI = findResource(blank, hasValue);
			String valueLiteral = findString(blank, hasLiteral);

			// lookup the assignment in the template and if none, make a fake one (will be treated as an "orphan")
			Assignment assn = resourceToAssignment.get(assnURI);
			if (assn == null)
			{	
				// !! not saving enough information to recreate a dummy assignment
				continue;
			}

			// create the annotation
			if (valueURI != null)
			{
				Annotation annot = new Annotation(assn, new Value(valueURI.toString(), label));
				annot.value.descr = descr;
				assay.annotations.add(annot);
			}
			else if (valueLiteral.length() > 0)
			{
				Annotation annot = new Annotation(assn, valueLiteral);
				assay.annotations.add(annot);
			}
			// (else ignore)
		}
	
		return assay;
	}
	
	private String turnLabelIntoName(String label)
	{
		if (label == null) return null;
		if (label.length() == 0) label = "unnamed";
		
		StringBuffer buff = new StringBuffer();
		for (String bit : label.split(" "))
    	{
    		if (bit.length() == 0) continue;
    		char[] chars = new char[bit.length()];
    		bit.getChars(0, bit.length(), chars, 0);
    		chars[0] = Character.toUpperCase(chars[0]);
    		for (char ch : chars) if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) buff.append(ch);
    	}
    	
    	// if the name was previously encountered, give it a number suffix to disambiguate
    	String name = buff.toString();
    	Integer count = nameCounts.get(name);
    	if (count != null)
    	{
    		count++;
    		nameCounts.put(name, count);
    		name += count;
    	}
    	else nameCounts.put(name, 1);
    	
    	return name;    	
	}
	
	// looks for an assignment and returns it as a string regardless of what type it actually is; blank if not found
	private String findAsString(Resource subj, Property prop)
	{
		for (StmtIterator it = model.listStatements(subj, prop, (RDFNode)null); it.hasNext();) 
		{
			RDFNode obj = it.next().getObject();
			if (obj.isLiteral()) return obj.asLiteral().getString();
			return obj.toString();
		}
		return "";
	}

	// looks up a specific string, typically a label or similar; returns blank string if not found
	private String findString(Resource subj, Property prop)
	{
		for (StmtIterator it = model.listStatements(subj, prop, (RDFNode)null); it.hasNext();)
		{
			RDFNode obj = it.next().getObject();
			if (obj.isLiteral()) return obj.asLiteral().getString();
		}
		return "";
	}
	
	// looks for an explicitly typed integer; returns 0 if not found
	private int findInteger(Resource subj, Property prop)
	{
		for (StmtIterator it = model.listStatements(subj, prop, (RDFNode)null); it.hasNext();)
		{
			RDFNode obj = it.next().getObject();
			if (obj.isLiteral())
			{
				Literal lit = obj.asLiteral();
				if (lit.getValue() instanceof Object) return lit.getInt();
			}
		}
		return 0;
	}
	
	// look for a URI node; returns null if none
	private Resource findResource(Resource subj, Property prop)
	{
		for (StmtIterator it = model.listStatements(subj, prop, (RDFNode)null); it.hasNext();)
		{
			RDFNode obj = it.next().getObject();
			if (obj.isResource()) return obj.asResource();
		}
		return null;
	}
}


