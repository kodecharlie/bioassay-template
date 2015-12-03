/*
 * BioAssay Ontology Annotator Tools
 * 
 * (c) 2014-2015 Collaborative Drug Discovery Inc.
 */

package com.cdd.bao.editor.endpoint;

import com.cdd.bao.*;
import com.cdd.bao.template.*;

import java.io.*;
import java.util.*;

import javafx.event.*;
import javafx.geometry.*;
import javafx.stage.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.application.*;
import javafx.beans.value.*;
import javafx.util.*;

/*
	Display functionality for showing templates & assays fetched from the SPARQL endpoint.
*/

public final class BrowseTreeCell extends TreeCell<BrowseEndpoint.Branch>
{
	// ------------ private data ------------	

    public BrowseTreeCell()
    {
    }
 
    public void updateItem(BrowseEndpoint.Branch branch, boolean empty)
    {
        super.updateItem(branch, empty);
 
        if (empty)
        {
            setText(null);
            setGraphic(null);
        }
        else 
        {
            if (isEditing()) 
            {
            }
            else
            {
				String label = "?", style = "";
				
				if (branch.template != null)
				{
					label = branch.template.getRoot().name;
					style = "-fx-font-weight: bold; -fx-text-fill: #000000;";
				}
				else if (branch.assay != null)
				{
					label = branch.assay.name;
					style = "-fx-font-weight: normal; -fx-text-fill: #606060;";
				}

				setStyle(style);
                setText(label);
                setGraphic(getTreeItem().getGraphic());
                setupContextMenu();
/*
		TreeItem<String> item = treeview.getSelectionModel().getSelectedItem();
            MenuItem addMenuItem = new MenuItem("Fnord!");
            ContextMenu ctx = new ContextMenu();
            ctx.getItems().add(addMenuItem);
            addMenuItem.setOnAction(new EventHandler<ActionEvent>()
            {
                public void handle(ActionEvent t)
                {
                	Util.writeln("--> FNORD!");
                }
            });
            
            setContextMenu(ctx);
*/
            }
	    }
    }

    private void setupContextMenu()
    {
    	TreeItem<BrowseEndpoint.Branch> item = getTreeView().getSelectionModel().getSelectedItem();
        MenuItem addMenuItem = new MenuItem("Fnord!");
        ContextMenu ctx = new ContextMenu();
        ctx.getItems().add(addMenuItem);
        addMenuItem.setOnAction(new EventHandler<ActionEvent>()
        {
            public void handle(ActionEvent t)
            {
            	Util.writeln("--> FNORD!");
            }
        });
        
        setContextMenu(ctx);
    }

	private String getString() 
    {
        return getItem() == null ? "" : getItem().toString();
    }
}
