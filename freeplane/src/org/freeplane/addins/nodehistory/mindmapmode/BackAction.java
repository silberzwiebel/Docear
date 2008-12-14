/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Dimitry Polivaev
 *
 *  This file author is Dimitry Polivaev
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.addins.nodehistory.mindmapmode;

import java.awt.event.ActionEvent;

import org.freeplane.controller.ActionDescriptor;
import org.freeplane.controller.FreeplaneAction;
import org.freeplane.ui.EnabledAction;

/**
 * @author Dimitry Polivaev
 * 13.12.2008
 */
/**
 * @author Dimitry Polivaev
 * 13.12.2008
 */
@EnabledAction(checkOnNodeChange=true)
@ActionDescriptor(
       name="accessories/plugins/NodeHistoryBack.properties_name", //
       tooltip="accessories/plugins/NodeHistoryBack.properties_documentation", //
       keyStroke="keystroke_accessories/plugins/NodeHistoryBack.keystroke.alt_BACK", //
       iconPath="accessories/plugins/icons/back.png", //
       locations={"/menu_bar/navigate/folding", 
    		   "/main_toolbar/folding"}
)
class BackAction extends FreeplaneAction{
	public BackAction(NodeHistory nodeHistory) {
	    super();
	    this.nodeHistory = nodeHistory;
    }
	final private NodeHistory nodeHistory;
	public void actionPerformed(ActionEvent e) {
		nodeHistory.goBack();
    }
	public void setEnabled(){
		setEnabled(nodeHistory.canGoBack());
	}
}
