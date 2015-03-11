/**
* Copyright (c) 2015 General Bioinformatics Limited
* Distributed under the GNU GPL v2. For full terms see the file LICENSE.
*/
package com.generalbioinformatics.cy3.internal;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.cytoscape.application.swing.AbstractCyAction;

public class ActionWrapper extends AbstractCyAction 
{
	private Action delegate;
	
	ActionWrapper(Action delegate, String preferredMenu)
	{
		super((String)delegate.getValue(AbstractAction.NAME));
        setPreferredMenu(preferredMenu);
        //setMenuGravity(2.0f);
        this.delegate = delegate;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) 
	{
		delegate.actionPerformed(e);
	}

}
