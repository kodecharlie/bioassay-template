/*
 * BioAssay Ontology Annotator Tools
 * 
 * (c) 2014-2017 Collaborative Drug Discovery Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2.0
 * as published by the Free Software Foundation:
 * 
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.cdd.bao.importer;

import com.cdd.bao.util.*;
import com.cdd.bao.template.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import org.json.*;

/*
	Controlled vocabulary mapping: parses and manages a JSON-formatted file for storing translations between keywords and importing
	of semantic web terms.
*/

public class KeywordMapping
{
	private File file;
	
	public static final class Identity
	{
		public String regex; // there must be at least one group, e.g. "ACME(.*)"
		public String prefix; // must correspond to an identifier prefix for the output, e.g. "acmeID:"

		public static Identity create(String name, String prefix)
		{
			Identity id = new Identity();
			id.regex = Pattern.quote(name);
			id.prefix = prefix;
			return id;
		}
	}
	
	public static final class TextBlock
	{
		public String regex; // column to match
		public String title; // preceding title to use when compiling the text section

		public static TextBlock create(String name, String title)
		{
			TextBlock txt = new TextBlock();
			txt.regex = Pattern.quote(name);
			txt.title = title;
			return txt;
		}
	}

	public static class MapAssn
	{
		public String regex; // anything that matches this expression is included in this assignment
		public String propURI; // URI of the assignment to match to (must be in template, or null)
		public String[] groupNest; // groupNest disambiguation
	}
	
	public static final class Property extends MapAssn
	{		
		public static Property create(String name, String propURI, String[] groupNest)
		{
			Property prop = new Property();
			prop.regex = Pattern.quote(name);
			prop.propURI = ModelSchema.collapsePrefix(propURI);
			prop.groupNest = collapsePrefixes(groupNest);
			return prop;
		}
	}
	
	public static final class Value extends MapAssn
	{
		public String valueRegex; // which values to match
		public String valueURI; // URI of value to match to (must occur in hierarchy of corresponding assignment)

		public static Value create(String name, String value, String valueURI, String propURI, String[] groupNest)
		{
			Value val = new Value();
			val.regex = Pattern.quote(name);
			val.valueRegex = Pattern.quote(value);
			val.valueURI = ModelSchema.collapsePrefix(valueURI);
			val.propURI = ModelSchema.collapsePrefix(propURI);
			val.groupNest = collapsePrefixes(groupNest);
			return val;
		}
	}
	
	public static final class Literal extends MapAssn
	{
		public String valueRegex; // which values to pass through as literals

		public static Literal create(String name, String value, String propURI, String[] groupNest)
		{
			Literal lit = new Literal();
			lit.regex = Pattern.quote(name);
			lit.valueRegex = Util.isBlank(value) ? ".*" : Pattern.quote(value);
			lit.propURI = ModelSchema.collapsePrefix(propURI);
			lit.groupNest = collapsePrefixes(groupNest);
			return lit;
		}
	}
	
	public List<Identity> identities = new ArrayList<>();
	public List<TextBlock> textBlocks = new ArrayList<>();
	public List<Property> properties = new ArrayList<>();
	public List<Value> values = new ArrayList<>();
	public List<Literal> literals = new ArrayList<>();
	
	private Map<String, Pattern> regexes = new HashMap<>(); // avoid reparsing all the time

	// ------------ public methods ------------

	// instantiate with given filename; parses as much as possible, and fails gently if anything goes wrong
	public KeywordMapping(String mapFN)
	{
		file = new File(mapFN);
		
		// try to load the file, but it's OK if it fails
		JSONObject json = null;
		try
		{
			Reader rdr = new FileReader(file);
			json = new JSONObject(new JSONTokener(rdr));
			rdr.close();
		}
		catch (JSONException ex) {Util.writeln("NOTE: reading file " + file.getAbsolutePath() + " failed: " + ex.getMessage());}
		catch (IOException ex) {return;} // includes file not found, which is OK
		
		try
		{
			for (JSONObject obj : json.optJSONArrayEmpty("identities").toObjectArray())
			{
				Identity id = new Identity();
				id.regex = regexOrName(obj.optString("regex"), obj.optString("name"));
				id.prefix = obj.optString("prefix");
				identities.add(id);
			}
			for (JSONObject obj : json.optJSONArrayEmpty("textBlocks").toObjectArray())
			{
				TextBlock txt = new TextBlock();
				txt.regex = regexOrName(obj.optString("regex"), obj.optString("name"));
				txt.title = obj.optString("title");
				textBlocks.add(txt);
			}
			for (JSONObject obj : json.optJSONArrayEmpty("properties").toObjectArray())
			{
				Property prop = new Property();
				prop.regex = regexOrName(obj.optString("regex"), obj.optString("name"));
				prop.propURI = obj.optString("propURI");
				prop.groupNest = obj.optJSONArrayEmpty("groupNest").toStringArray();
				properties.add(prop);
			}
			for (JSONObject obj : json.optJSONArrayEmpty("values").toObjectArray())
			{
				Value val = new Value();
				val.regex = regexOrName(obj.optString("regex"), obj.optString("name"));
				val.valueRegex = regexOrName(obj.optString("valueRegex"), obj.optString("valueName"));
				val.valueURI = obj.optString("valueURI");
				val.propURI = obj.optString("propURI");
				val.groupNest = obj.optJSONArrayEmpty("groupNest").toStringArray();
				values.add(val);
			}
			for (JSONObject obj : json.optJSONArrayEmpty("literals").toObjectArray())
			{
				Literal lit = new Literal();
				lit.regex = regexOrName(obj.optString("regex"), obj.optString("name"));
				lit.valueRegex = regexOrName(obj.optString("valueRegex"), obj.optString("valueName"));
				lit.propURI = obj.optString("propURI");
				lit.groupNest = obj.optJSONArrayEmpty("groupNest").toStringArray();
				literals.add(lit);
			}
		}
		catch (JSONException ex) 
		{
			Util.writeln("NOTE: parsing error");
			ex.printStackTrace();
			Util.writeln("*** Execution will continue, but part of the mapping has not been loaded and may be overwritten.");
		}
	}
	
	// writes the current state of the mapping back to the original file
	public void save() throws IOException
	{
		JSONObject json = new JSONObject();
		JSONArray listID = new JSONArray(), listText = new JSONArray(), listProp = new JSONArray(), listVal = new JSONArray(), listLit = new JSONArray();

		for (Identity id : identities)
		{
			JSONObject obj = new JSONObject();
			obj.put("regex", id.regex);
			obj.put("prefix", id.prefix);
			listID.put(obj);
		}
		for (TextBlock txt : textBlocks)
		{
			JSONObject obj = new JSONObject();
			obj.put("regex", txt.regex);
			obj.put("title", txt.title);
			listText.put(obj);
		}
		for (Property prop : properties)
		{
			JSONObject obj = new JSONObject();
			obj.put("regex", prop.regex);
			obj.put("propURI", prop.propURI);
			obj.put("groupNest", prop.groupNest);
			listProp.put(obj);
		}
		for (Value val : values)
		{
			JSONObject obj = new JSONObject();
			obj.put("regex", val.regex);
			obj.put("valueRegex", val.valueRegex);
			obj.put("valueURI", val.valueURI);
			obj.put("propURI", val.propURI);
			obj.put("groupNest", val.groupNest);
			listVal.put(obj);
		}
		for (Literal lit : literals)
		{
			JSONObject obj = new JSONObject();
			obj.put("regex", lit.regex);
			obj.put("valueRegex", lit.valueRegex);
			obj.put("propURI", lit.propURI);
			obj.put("groupNest", lit.groupNest);
			listLit.put(obj);
		}
		
		json.put("identities", listID);
		json.put("textBlocks", listText);
		json.put("properties", listProp);
		json.put("values", listVal);
		json.put("literals", listLit);

		Writer wtr = new FileWriter(file);
		wtr.write(json.toString(2));
		wtr.close();
	}
	
	// searches for an identifier for which the name matches its regex
	public Identity findIdentity(String name)
	{
		for (Identity id : identities)
		{
			Pattern p = getPattern(id.regex);
			if (p.matcher(name).matches()) return id;
		}
		return null;
	}

	// searches for a text block for which the name matches its regex
	public TextBlock findTextBlock(String name)
	{
		for (TextBlock txt : textBlocks)
		{
			Pattern p = getPattern(txt.regex);
			if (p.matcher(name).matches()) return txt;
		}
		return null;
	}
	
	// returns true if the mapping pattern for the assignment is compatible with the name
	public boolean matchesName(MapAssn assn, String name)
	{
		Pattern p = getPattern(assn.regex);
		return p.matcher(name).matches();
	}
	
	// searches for a property for which the name matches its regex
	public Property findProperty(String name)
	{
		for (Property prop : properties)
		{
			Pattern p = getPattern(prop.regex);
			if (p.matcher(name).matches()) return prop;
		}
		return null;
	}
	
	// searches for a value for which the name matches its regex
	public Value findValue(String key, String data)
	{
		for (Value val : values)
		{
			Pattern p = getPattern(val.regex);
			if (!p.matcher(key).matches()) continue;
			p = getPattern(val.valueRegex);
			if (p.matcher(data).matches()) return val;
		}
		return null;
	}
	
	// searches for a literal for which the name matches its regex
	public Literal findLiteral(String key, String data)
	{
		for (Literal lit : literals)
		{
			Pattern p = getPattern(lit.regex);
			if (!p.matcher(key).matches()) continue;
			p = getPattern(lit.valueRegex);
			if (p.matcher(data).matches()) return lit;
		}
		return null;
	}	
	
	// takes an assay instance and applies all of the mappings, to turn it into an assay object, which is compatible with the
	// BioAssay Express import format; complains loudly and rudely if something didn't quite work
	public JSONObject createAssay(JSONObject keydata, Schema schema, Map<Schema.Assignment, SchemaTree> treeCache) throws JSONException, IOException
	{
		String uniqueID = null;
		List<String> linesBlock = new ArrayList<>(), linesSkipped = new ArrayList<>(), linesProcessed = new ArrayList<>();
		Set<String> gotAnnot = new HashSet<>(), gotLiteral = new HashSet<>();
		JSONArray jsonAnnot = new JSONArray();
		final String SEP = "::";

		for (String key : keydata.keySet())
		{
			String data = keydata.getString(key);
		
			Identity id = findIdentity(key);
			if (id != null)
			{
				if (uniqueID == null) uniqueID = id.prefix + data;
				continue;
			}
			
			TextBlock tblk = findTextBlock(key);
			if (tblk != null)
			{
				String hdr = "";
				if (Util.notBlank(tblk.title)) hdr = tblk.title + ": ";
				linesBlock.add(hdr + data);
				//continue; -- can be text and something else
			}
			
			Value val = findValue(key, data);
			if (val != null)
			{
				if (Util.isBlank(val.valueURI))
				{
					linesSkipped.add(key + ": " + data);
				}
				else
				{
					String hash = val.propURI + SEP + val.valueURI + SEP + (val.groupNest == null ? "" : String.join(SEP, val.groupNest));
					if (gotAnnot.contains(hash)) continue;
				
					JSONObject obj = new JSONObject();
					obj.put("propURI", ModelSchema.expandPrefix(val.propURI));
					obj.put("groupNest", expandPrefixes(val.groupNest));
					obj.put("valueURI", ModelSchema.expandPrefix(val.valueURI));
					jsonAnnot.put(obj);
					gotAnnot.add(hash);
					linesProcessed.add(key + ": " + data);
				}
				continue;
			}
			
			Literal lit = findLiteral(key, data);
			if (lit != null)
			{
				String hash = lit.propURI + SEP + (lit.groupNest == null ? "" : String.join(SEP, lit.groupNest)) + SEP + data;
				if (gotLiteral.contains(hash)) continue;
			
				JSONObject obj = new JSONObject();
				obj.put("propURI", ModelSchema.expandPrefix(lit.propURI));
				obj.put("groupNest", expandPrefixes(lit.groupNest));
				obj.put("valueLabel", data);
				jsonAnnot.put(obj);
				gotLiteral.add(hash);
				linesProcessed.add(key + ": " + data);
				
				continue;	
			}
			
			// probably shouldn't get this far, but just in case
			linesSkipped.add(key + ": " + data);
		}
		
		// annotation collapsing: sometimes there's a branch sequence that should exclude parent nodes
		for (int n = 0; n < jsonAnnot.length(); n++)
		{
			JSONObject obj = jsonAnnot.getJSONObject(n);
			String propURI = obj.getString("propURI"), valueURI = obj.optString("valueURI");
			if (valueURI == null) continue;
			//String[] groupNest = obj.getJSONArray("groupNest").toStringArray();
			String[] groupNest = (String[])obj.get("groupNest"); // (because it was poked this way)
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(ModelSchema.expandPrefix(propURI), groupNest);
			if (assnList.length == 0) continue;
			SchemaTree tree = treeCache.get(assnList[0]);
			if (tree == null) continue;
			
			Set<String> exclusion = new HashSet<>();
			for (SchemaTree.Node node = tree.getNode(valueURI); node != null; node = node.parent) exclusion.add(node.uri);
			if (exclusion.size() == 0) continue;
			
			for (int i = jsonAnnot.length() - 1; i >= 0; i--) if (i != n)
			{
				obj = jsonAnnot.getJSONObject(i);
				if (!propURI.equals(obj.getString("propURI"))) continue;
				//if (!Objects.deepEquals(groupNest, obj.getJSONArray("groupNest").toStringArray())) continue;
				if (!Objects.deepEquals(groupNest, (String[])obj.get("groupNest"))) continue;
				if (!exclusion.contains(obj.getString("valueURI"))) continue;
				jsonAnnot.remove(i);
			}
		}
		
		String text = "";
		if (linesBlock.size() > 0) text += String.join("\n", linesBlock) + "\n\n";
		if (linesSkipped.size() > 0) text += "SKIPPED:\n" + String.join("\n", linesSkipped) + "\n\n";
		text += "PROCESSED:\n" + String.join("\n", linesProcessed);
		
		JSONObject assay = new JSONObject();
		assay.put("uniqueID", uniqueID);
		assay.put("text", text);
		assay.put("schemaURI", schema.getSchemaPrefix());
		assay.put("annotations", jsonAnnot);
		return assay;
	}
	
	// collapses/expands all the prefixes in the list
	public static String[] collapsePrefixes(String[] uriList)
	{
		if (uriList == null || uriList.length == 0) return null;
		String[] ret = new String[uriList.length];
		for (int n = 0; n < ret.length; n++) ret[n] = ModelSchema.collapsePrefix(uriList[n]);
		return ret;
	}
	public static String[] expandPrefixes(String[] uriList)
	{
		if (uriList == null || uriList.length == 0) return null;
		String[] ret = new String[uriList.length];
		for (int n = 0; n < ret.length; n++) ret[n] = ModelSchema.expandPrefix(uriList[n]);
		return ret;
	}

	// ------------ private methods ------------
	
	private Pattern getPattern(String regex) throws PatternSyntaxException
	{
		Pattern p = regexes.get(regex);
		if (p == null) regexes.put(regex, p = Pattern.compile(regex));
		return p;
	}
	
	private String regexOrName(String regex, String name)
	{
		if (Util.notBlank(name)) return Pattern.quote(name);
		return regex;
	}
}
