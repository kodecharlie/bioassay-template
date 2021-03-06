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

package com.cdd.bao.editor;

import com.cdd.bao.*;
import com.cdd.bao.template.*;

import java.io.*;
import java.util.*;

/*
	Schema stack: a current editing object, some number of items in the undo/redo stacks, and convenience editing functions.
*/

public class StackSchema
{
	private static final class State
	{
		Schema schema = null;
		boolean dirty = false;
		
		public State(Schema schema, boolean dirty)
		{
			this.schema = schema;
			this.dirty = dirty;
		}
	}

	State current = new State(null, false);
	private static final int STACK_SIZE = 20;
	private List<State> undoStack = new ArrayList<>(), redoStack = new ArrayList<>();

	// ------------ public methods ------------	

	public StackSchema()
	{
		current.schema = new Schema();
	}
	
	// returns the current schema: caller must not modify; saves overhead from cloning
	public Schema peekSchema()
	{
		return current.schema;
	}
	
	// returns the current schema as a deep clone: the caller may tinker with this at will, then submit it back as a modified version
	public Schema getSchema()
	{
		return current.schema.clone();	
	}
	
	// replace the current schema due to a modification: updates the undo stack accordingly, and notes it as being dirty; note that the default
	// syntax compares to ensure that the replacement is different to the current instance, but this can be bypassed
	public void changeSchema(Schema schema) {changeSchema(schema, false);}
	public void changeSchema(Schema schema, boolean knownDifferent)
	{
		if (!knownDifferent && schema.equals(current.schema)) return;
		redoStack.clear();
		undoStack.add(current);
		while (undoStack.size() > STACK_SIZE) undoStack.remove(0);
		current = new State(schema, true);
	}
	
	// replaces the current schema, and flushes the undo/redo; typically in response to an initialize/open operation; is presumed to be not-dirty
	public void setSchema(Schema schema)
	{
		current = new State(schema, false);
		undoStack.clear();
		redoStack.clear();
	}
	
	// dirty flag: whether or not the file is considered to be uptodate on disk
	public boolean isDirty() {return current.dirty;}
	public void setDirty(boolean dirty) {current.dirty = dirty;}
	
	// undo/redo execution thereof
	public boolean canUndo() {return undoStack.size() > 0;}
	public boolean canRedo() {return redoStack.size() > 0;}
	public void performUndo()
	{
		if (undoStack.size() == 0) return;
		redoStack.add(current);
		current = undoStack.remove(undoStack.size() - 1);
	}
	public void performRedo()
	{
		if (redoStack.size() == 0) return;
		undoStack.add(current);
		current = redoStack.remove(redoStack.size() - 1);
	}
	
	// ------------ private methods ------------	

}
