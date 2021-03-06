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

import java.util.ArrayList;
import java.util.List;

import org.decat.d2d.Preference.PreferenceGroup;
import org.decat.d2d.Preference.PreferenceType;

import android.content.SharedPreferences;

public class PreferencesHelper {
	public static final String VALUE_SUFFIX = "_VALUE";

	public static final String MESSAGE_1 = "MESSAGE_1";

	public static final String MESSAGE_2 = "MESSAGE_2";

	public static final String MESSAGE_3 = "MESSAGE_3";

	public static final String CONTACT_1 = "CONTACT_1";

	public static final String CONTACT_2 = "CONTACT_2";

	public static final String CONTACT_3 = "CONTACT_3";

	public static final String NOTIFICATION_SHORTCUT = "NOTIFICATION_SHORTCUT";

	public static final String NOTIFICATION_SHORTCUT_ON_BOOT = "NOTIFICATION_SHORTCUT_ON_BOOT";

	public final Preference[] preferences = {
			new Preference(MESSAGE_1, PreferenceGroup.GROUP_MESSAGES, PreferenceType.TYPE_STRING, R.string.MESSAGE_1_label),
			new Preference(MESSAGE_2, PreferenceGroup.GROUP_MESSAGES, PreferenceType.TYPE_STRING, R.string.MESSAGE_2_label),
			new Preference(MESSAGE_3, PreferenceGroup.GROUP_MESSAGES, PreferenceType.TYPE_STRING, R.string.MESSAGE_3_label),
			// Values must be before keys so they are loaded when the view is
			// created
			new Preference(CONTACT_1 + VALUE_SUFFIX, PreferenceGroup.GROUP_CONTACTS_VALUES, PreferenceType.TYPE_CONTACT_VALUE, 0),
			new Preference(CONTACT_2 + VALUE_SUFFIX, PreferenceGroup.GROUP_CONTACTS_VALUES, PreferenceType.TYPE_CONTACT_VALUE, 0),
			new Preference(CONTACT_3 + VALUE_SUFFIX, PreferenceGroup.GROUP_CONTACTS_VALUES, PreferenceType.TYPE_CONTACT_VALUE, 0),
			new Preference(CONTACT_1, PreferenceGroup.GROUP_CONTACTS, PreferenceType.TYPE_CONTACT, R.string.CONTACT_1_label),
			new Preference(CONTACT_2, PreferenceGroup.GROUP_CONTACTS, PreferenceType.TYPE_CONTACT, R.string.CONTACT_2_label),
			new Preference(CONTACT_3, PreferenceGroup.GROUP_CONTACTS, PreferenceType.TYPE_CONTACT, R.string.CONTACT_3_label),
			new Preference(NOTIFICATION_SHORTCUT, PreferenceGroup.GROUP_TOGGLES, PreferenceType.TYPE_BOOLEAN, R.string.NOTIFICATION_SHORTCUT_label),
			new Preference(NOTIFICATION_SHORTCUT_ON_BOOT, PreferenceGroup.GROUP_TOGGLES, PreferenceType.TYPE_BOOLEAN, R.string.NOTIFICATION_SHORTCUT_ON_BOOT_label),
	};

	SharedPreferences sharedPreferences;

	public PreferencesHelper(SharedPreferences sharedPreferences) {
		this.sharedPreferences = sharedPreferences;
	}

	public List<Preference> getPreferencesByGroup(PreferenceGroup group) {
		List<Preference> result = new ArrayList<Preference>();
		for (Preference preference : preferences) {
			if (preference.group.equals(group)) {
				result.add(preference);
			}
		}
		return result;
	}
}