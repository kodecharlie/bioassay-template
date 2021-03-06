/*
 * BioAssay Ontology Annotator Tools
 * 
 * (c) 2014-2016 Collaborative Drug Discovery Inc.
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

package com.cdd.bao;

import java.util.*;
import java.io.*;

import org.apache.commons.lang3.*;

import com.cdd.bao.template.*;
import com.cdd.bao.util.*;
import com.cdd.bao.editor.*;
import com.cdd.bao.importer.*;

/*
	Entrypoint for all command line functionality: delegates to the appropriate corner.
*/

public class Main
{
	public static void main(String[] argv)
	{
		if (argv.length > 0 && (argv[0].equals("-h") || argv[0].equals("--help")))
		{
			printHelp();
			return;
		}
		
		// look for additional options that affect overall state
		String[] extraOnto = null, exclOnto = null;
		for (int n = 0; n < argv.length;)
		{
			if (argv[n].startsWith("--onto"))
			{
				argv = ArrayUtils.remove(argv, n);
				while (n < argv.length)
				{
					if (argv[n].startsWith("-")) break;
					extraOnto = ArrayUtils.add(extraOnto, argv[n]);
					argv = ArrayUtils.remove(argv, n);
				}
			}
			else if (argv[n].startsWith("--excl"))
			{
				argv = ArrayUtils.remove(argv, n);
				while (n < argv.length)
				{
					if (argv[n].startsWith("-")) break;
					exclOnto = ArrayUtils.add(exclOnto, argv[n]);
					argv = ArrayUtils.remove(argv, n);
				}
			}
			else n++;
		}
		Vocabulary.setExtraOntology(extraOnto);
		Vocabulary.setExclOntology(exclOnto);

		// main command-induced functionality
		if (argv.length == 0) new MainApplication().exec(new String[0]);
		else if (argv[0].equals("edit")) 
		{			
			String[] subset = Arrays.copyOfRange(argv, 1, argv.length);
			new MainApplication().exec(subset);
		}
		else if (argv[0].equals("geneont"))
		{
			try
			{
				ImportGeneOntology impgo = new ImportGeneOntology();
				impgo.load(argv[1]);
				impgo.save(argv[2]);
			}
			catch (Exception ex) {ex.printStackTrace();}
		}
		else if (argv[0].equals("filter"))
		{
			try
			{
				OntologyFilter filt = new OntologyFilter();
				filt.load(argv[1]);
				filt.save(argv[2]);
			}
			catch (Exception ex) {ex.printStackTrace();}
		}
		else if (argv[0].equals("compare"))
		{
			try {diffVocab(ArrayUtils.remove(argv, 0));}
			catch (Exception ex) {ex.printStackTrace();}
		}
		else if (argv[0].equals("compile"))
		{
			try {compileSchema(ArrayUtils.remove(argv, 0));}
			catch (Exception ex) {ex.printStackTrace();}
		}
		else if (argv[0].equals("check"))
		{
			try {checkTemplate(ArrayUtils.remove(argv, 0));}
			catch (Exception ex) {ex.printStackTrace();}
		}
		else if (argv[0].equals("import"))
		{
			try {importKeywords(ArrayUtils.remove(argv, 0));}
			catch (Exception ex) {ex.printStackTrace();}
		}
		else if (argv[0].equals("scanaxioms"))
		{
			try {new ScanAxioms().exec();}
			catch (Exception ex) {ex.printStackTrace();}
		}
		else
		{
			Util.writeln("Unknown option '" + argv[0] + "'");
			printHelp();
		}
	}
	
	public static void printHelp()
	{
		Util.writeln("BioAssay Ontology Annotator Tools");
		Util.writeln("Options:");
		Util.writeln("    edit {files...}");
		Util.writeln("    geneont {infile} {outfile}");
		Util.writeln("    filter {infile.owl/ttl} {outfile.ttl}");
		Util.writeln("    compare {old.dump} {new.dump}");
		Util.writeln("    compile {schema*.ttl} {vocab.dump}");
		Util.writeln("    check {schema.ttl}");
		Util.writeln("    import {cfg.json}");
		Util.writeln("    scanaxioms");
		Util.writeln("    --onto {files...}");
		Util.writeln("    --excl {files...}");
	}
	
	private static void diffVocab(String[] options) throws Exception
	{
		String fn1 = options[0], fn2 = options[1], fn3 = options.length >= 3 ? options[2] : null;
		Util.writeln("Differences between vocab dumps...");
		Util.writeln("    OLD:" + fn1);
		Util.writeln("    NEW:" + fn2);

		InputStream istr = new FileInputStream(fn1);
		SchemaVocab sv1 = SchemaVocab.deserialise(istr, new Schema[0]); // note: giving no schemata works for this purpose
		istr.close();
		istr = new FileInputStream(fn2);
		SchemaVocab sv2 = SchemaVocab.deserialise(istr, new Schema[0]); // note: giving no schemata works for this purpose
		istr.close();
		
		Schema schema = null;
		if (fn3 != null) schema = ModelSchema.deserialise(new File(fn3));

		Util.writeln("Term counts: [" + sv1.numTerms() + "] -> [" + sv2.numTerms() + "]");
		Util.writeln("Prefixes: [" + sv1.numPrefixes() + "] -> [" + sv2.numPrefixes() + "]");
		
		// note: only shows trees on both sides
		for (SchemaVocab.StoredTree tree1 : sv1.getTrees()) for (SchemaVocab.StoredTree tree2 : sv2.getTrees())
		{
			if (!tree1.schemaPrefix.equals(tree2.schemaPrefix) || !tree1.locator.equals(tree2.locator)) continue;
			
			String info = "locator: " + tree1.locator;
			if (schema != null && tree1.schemaPrefix.equals(schema.getSchemaPrefix()))
			{
				Schema.Assignment assn = schema.obtainAssignment(tree1.locator);
				info = "assignment: " + assn.name + " (locator: " + tree1.locator + ")";
			}
			
			Util.writeln("Schema [" + tree1.schemaPrefix + "], " + info);
			Set<String> terms1 = new HashSet<>(), terms2 = new HashSet<>();
			for (SchemaTree.Node node : tree1.tree.getFlat()) terms1.add(node.uri);
			for (SchemaTree.Node node : tree2.tree.getFlat()) terms2.add(node.uri);

			Set<String> extra1 = new TreeSet<>(), extra2 = new TreeSet<>();
			for (String uri : terms1) if (!terms2.contains(uri)) extra1.add(uri);
			for (String uri : terms2) if (!terms1.contains(uri)) extra2.add(uri);

			Util.writeln("    terms removed: " + extra1.size());
			for (String uri : extra1) Util.writeln("        <" + uri + "> " + sv1.getLabel(uri));
			
			Util.writeln("    terms added: " + extra2.size());
			for (String uri : extra2) Util.writeln("        <" + uri + "> " + sv2.getLabel(uri));
		}
	}      
	
	// compiles one-or-more schema files into a single vocabulary dump
	private static void compileSchema(String[] options) throws Exception
	{
		List<String> inputFiles = new ArrayList<>();
		for (int n = 0; n < options.length - 1; n++) inputFiles.add(Util.expandFileHome(options[n]));
		String outputFile = Util.expandFileHome(options[options.length - 1]);
		Util.writeln("Compiling schema files:");
		for (int n = 0; n < inputFiles.size(); n++) Util.writeln("    " + inputFiles.get(n));
		Util.writeln("Output to:");
		Util.writeln("    " + outputFile);
		
		//loadupVocab();
		Vocabulary vocab = new Vocabulary();
		Util.writeFlush("Loading ontologies ");
		vocab.addListener(new Vocabulary.Listener()
		{
			public void vocabLoadingProgress(Vocabulary vocab, float progress) {Util.writeFlush(".");}
			public void vocabLoadingException(Exception ex) {ex.printStackTrace();}
		});
		vocab.load(null, null);
		Util.writeln();
		
		Schema[] schemata = new Schema[inputFiles.size()];
		for (int n = 0; n < schemata.length; n++) schemata[n] = ModelSchema.deserialise(new File(inputFiles.get(n)));
		SchemaVocab schvoc = new SchemaVocab(vocab, schemata);
		
		Util.writeln("Loaded: " + schvoc.numTerms() + " terms.");
		OutputStream ostr = new FileOutputStream(outputFile);
		schvoc.serialise(ostr);
		ostr.close();
		Util.writeln("Done.");
	}

	// evaluates a schema template, looking for obvious shortcomings
	private static void checkTemplate(String[] options) throws Exception
	{
		if (options.length == 0)
		{
			Util.writeln("Must provide the schema filename to check filename.");
			return;
		}
		String fn = options[0];
		TemplateChecker chk = new TemplateChecker(fn);
		chk.perform();
	}
	
	// initiates the importing of keywords from controlled vocabulary
	private static void importKeywords(String[] options) throws Exception
	{
		if (options.length < 5)
		{
			Util.writeln("Importing syntax: {src} {map} {dst} {schema} {vocab} [{hints}]");
			Util.writeln("    where {src} is the JSON-formatted pre-import data");
			Util.writeln("          {map} contains the mapping instructions");
			Util.writeln("          {dst} is an import-ready ZIP file");
			Util.writeln("          {schema} is the template to conform to");
			Util.writeln("          {vocab} is the processed vocabulary dump");
			Util.writeln("          {hints} is an optional JSON file with putative term-to-URI options");
			return;
		}
		String hintsFN = options.length >= 6 ? options[5] : null;
		ImportControlledVocab imp = new ImportControlledVocab(options[0], options[1], options[2], options[3], options[4], hintsFN);
		imp.exec();
	}
}
