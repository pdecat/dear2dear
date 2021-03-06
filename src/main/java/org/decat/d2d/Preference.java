/**
 * Copyright (C) 2010-2018 Patrick Decat
 *
 * dear2dear is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * dear2dear is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with dear2dear.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.decat.d2d;

import android.view.View;

class Preference {
	enum PreferenceType {
		TYPE_STRING,
		TYPE_CONTACT,
		TYPE_CONTACT_VALUE,
		TYPE_BOOLEAN
	}

	enum PreferenceGroup {
		GROUP_MESSAGES,
		GROUP_CONTACTS,
		GROUP_CONTACTS_VALUES,
		GROUP_TOGGLES
	}

	protected String key;
	protected PreferenceGroup group;
	protected PreferenceType type;
	protected int labelResourceId;
	protected View view;

	public Preference(String key, PreferenceGroup group, PreferenceType type, int labelResourceId) {
		this.key = key;
		this.group = group;
		this.type = type;
		this.labelResourceId = labelResourceId;
		this.view = null;
	}
}