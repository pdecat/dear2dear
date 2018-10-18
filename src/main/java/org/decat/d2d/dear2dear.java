/**
 * Copyright (C) 2010-2016 Patrick Decat
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

import java.util.List;

import org.decat.d2d.Preference.PreferenceGroup;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Contacts.People;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.SmsManager;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class dear2dear extends Activity {
	public static final String TAG = "D2D";

	private static final int ACTIVITY_REQUEST_PREFERENCES_EDITOR = 1;

	private static final int ACTIVITY_REQUEST_PERMISSION_READ_CONTACTS = 10;
	private static final int ACTIVITY_REQUEST_PERMISSION_READ_PHONE_STATE = 11;
	private static final int ACTIVITY_REQUEST_PERMISSION_SEND_SMS = 12;

	private static final String INTENT_SMS_SENT = "SMS_SENT";
	private static final String INTENT_SMS_DELIVERED = "SMS_DELIVERED";

	private static final String DEFAULT_NOTIFICATION_CHANNEL_ID = "DEFAULT";

	private TextView tv;

	private Button buttons[];

	private SharedPreferences sharedPreferences;
	private PreferencesHelper preferencesHelper;

	protected String destinationStepChoiceValue;
	protected String destinationStepChoiceLabel;

	protected String messageStepChoice;

	private String destinationChoiceDetails;

	private Button restartButton;

	private static boolean notificationShortcut = false;

	private PendingIntent sentIntent;

	private PendingIntent deliveryIntent;

	private ProgressDialog sendingMessageProgressDialog;

	public static void showToast(Context context, String message) {
		final Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.CENTER_VERTICAL | Gravity.TOP, 0, 320);
		toast.show();
	}

	private void showToast(String message) {
		showToast(this, message);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		sharedPreferences = getPreferences(Context.MODE_PRIVATE);
		preferencesHelper = new PreferencesHelper(sharedPreferences);

		// Check cached names against contacts IDs stored in preferences (issue
		// #11)
		checkCachedNamesAgainstContactIds();

		// Create layout
		LinearLayout ll = new LinearLayout(this);
		ll.setGravity(Gravity.FILL_VERTICAL);
		ll.setOrientation(android.widget.LinearLayout.VERTICAL);

		tv = new TextView(this);
		tv.setTextSize(40);
		ll.addView(tv);

		buttons = new Button[3];
		for (int i = 0; i < buttons.length; i++) {
			buttons[i] = new Button(this);
			ll.addView(buttons[i]);
		}

		// Add a restart button
		restartButton = new Button(this);
		restartButton.setText(getString(R.string.restartText));
		restartButton.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				Log.d(dear2dear.TAG, "Restart from scratch");
				startFromScratch();
			}
		});
		ll.addView(restartButton);

		registerBroadcastReceivers();

		sendingMessageProgressDialog = new ProgressDialog(this);
		sendingMessageProgressDialog.setMessage(getString(R.string.sendingMessageText));
		sendingMessageProgressDialog.setCancelable(false);

		setContentView(ll);

		createNotificationChannel();
	}

	private void createNotificationChannel() {
		// Create the NotificationChannel, but only on API 26+ because
		// the NotificationChannel class is new and not in the support library
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(DEFAULT_NOTIFICATION_CHANNEL_ID, DEFAULT_NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_MIN);
			NotificationManager notificationManager = getSystemService(NotificationManager.class);
			notificationManager.createNotificationChannel(channel);
		}
	}

	private void registerBroadcastReceivers() {
		sentIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_SMS_SENT), 0);
		registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				sendingMessageProgressDialog.dismiss();

				int resultCode = getResultCode();
				if (resultCode == Activity.RESULT_OK) {
					showToast(getString(R.string.smsSuccessfullySentText));
					Log.d(TAG, "Successfully sent an SMS");

					// Store the SMS into the standard Google SMS app
					storeSms(messageStepChoice, destinationChoiceDetails);
				} else {
					String message = getString(R.string.failedToSendSmsErrorUnknownText);
					switch (resultCode) {
						case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
							message = getString(R.string.failedToSendSmsErrorGenericText);
							break;
						case SmsManager.RESULT_ERROR_NO_SERVICE:
							message = getString(R.string.failedToSendSmsErrorNoServiceText);
							break;
						case SmsManager.RESULT_ERROR_NULL_PDU:
							message = getString(R.string.failedToSendSmsErrorNullPduText);
							break;
						case SmsManager.RESULT_ERROR_RADIO_OFF:
							message = getString(R.string.failedToSendSmsErrorRadioOffText);
							break;
					}
					showToast(getString(R.string.failedToSendSmsText, message));
					Log.e(TAG, "Failed to send SMS (message=" + message + ", resultCode=" + resultCode + ")");

					showRetryButton();
				}
			}
		}, new IntentFilter(INTENT_SMS_SENT));

		deliveryIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_SMS_DELIVERED), 0);
		registerReceiver(new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				int resultCode = getResultCode();
				switch (resultCode) {
					case Activity.RESULT_OK:
						showToast(getString(R.string.smsSuccessfullyDeliveredText));

						Log.d(TAG, "Successfully delivered an SMS");
						break;
					case Activity.RESULT_CANCELED:
						showToast(getString(R.string.failedToDeliverSmsText));
						Log.e(TAG, "Failed to deliver SMS (resultCode=" + resultCode + ")");

						// FIXME: May already have been done by sentIntent?
						showRetryButton();
						break;
				}
			}
		}, new IntentFilter(INTENT_SMS_DELIVERED));
	}

	/**
	 * Check cached names against contacts IDs stored in preferences (issue #11)
	 */
	private void checkCachedNamesAgainstContactIds() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_CONTACTS)) {
				String message = getString(R.string.permissionReadContactsRationale);
				Log.d(TAG, message);
				showToast(message);
			}

			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS},  ACTIVITY_REQUEST_PERMISSION_READ_CONTACTS);

			return;
		}

		boolean cacheValid = true;
		StringBuilder sb = new StringBuilder();

		List<Preference> contacts = preferencesHelper.getPreferencesByGroup(PreferenceGroup.GROUP_CONTACTS);
		for (int i = 0; i < contacts.size(); i++) {
			Preference contact = contacts.get(i);
			String key = contact.key;
			String contactUri = sharedPreferences.getString(key, null);
			String cachedName = sharedPreferences.getString(key + PreferencesHelper.VALUE_SUFFIX, null);
			String currentName = getNameFromUri(contactUri);
			sb.append("\nContact information for ");
			sb.append(key);
			if (cachedName == null || currentName == null || !currentName.equals(cachedName)) {
				cacheValid = false;
				sb.append(" is invalid! (contactUri=");
				showToast(getString(R.string.contactInformationInvalidText, key, contactUri, cachedName, currentName));
			} else {
				sb.append(" is correct (contactUri=");
			}
			sb.append(contactUri);
			sb.append(", cachedName=");
			sb.append(cachedName);
			sb.append(", currentName=");
			sb.append(currentName);
			sb.append(")\n");
		}

		if (!cacheValid) {
			// TODO: launch preferences editor and emphasize invalid contacts
			Log.w(dear2dear.TAG, sb.toString());
		} else {
			Log.i(dear2dear.TAG, sb.toString());
		}
	}

        private static String getAppVersionName() {
                return BuildConfig.VERSION_NAME;
        }

	public static void updateNotificationShortcut(Context context) {
		// Get shared preferences
		SharedPreferences sharedPreferences = context.getSharedPreferences(dear2dear.class.getSimpleName(), Context.MODE_PRIVATE);

		// Get current value
		boolean value = sharedPreferences.getBoolean(PreferencesHelper.NOTIFICATION_SHORTCUT, true);

		if (value != notificationShortcut) {
			NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			if (value) {
				Intent intent = new Intent(context, dear2dear.class);

                Notification notification = new NotificationCompat.Builder(context, DEFAULT_NOTIFICATION_CHANNEL_ID)
			        .setPriority(NotificationCompat.PRIORITY_MIN)
					.setVisibility(NotificationCompat.VISIBILITY_SECRET)
					.setCategory(NotificationCompat.CATEGORY_SERVICE)
					.setVibrate(null)
					.setSmallIcon(R.drawable.icon)
					.setStyle(new NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notificationLabel)))
					.setContentTitle(context.getString(R.string.app_name) + " " + getAppVersionName())
					.setContentIntent(PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
					.setOngoing(true)
					.setShowWhen(false)
					.build();

				notificationManager.notify(0, notification);
			} else {
				notificationManager.cancel(0);
			}
		}

		// Store new value
		notificationShortcut = value;
	}

	@Override
	public void onResume() {
		super.onResume();

		// Update notification shortcut state
		updateNotificationShortcut(this);

		if (sharedPreferences.getString(preferencesHelper.preferences[0].key, null) == null) {
			String message = getString(R.string.pleaseProceedWithConfigurationFirstText);
			Log.i(dear2dear.TAG, message);
			showToast(message);
			showPreferencesEditor();
		} else {
			startFromScratch();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, R.id.preferences, 0, R.string.preferences);
		menu.add(Menu.NONE, R.id.about, 1, R.string.about);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.preferences:
				showPreferencesEditor();
				return true;
			case R.id.about:
				showAbout();
				return true;
		}
		return false;
	}

	private void showPreferencesEditor() {
		Intent intent = new Intent(this, PreferencesEditor.class);
		startActivityForResult(intent, ACTIVITY_REQUEST_PREFERENCES_EDITOR);
	}

	private void showAbout() {
		AlertDialog.Builder aboutWindow = new AlertDialog.Builder(this);
		TextView tx = new TextView(this);
		tx.setAutoLinkMask(Linkify.ALL);
		tx.setLinksClickable(true);
		tx.setMovementMethod(LinkMovementMethod.getInstance());
		tx.setGravity(Gravity.CENTER);
		tx.setTextSize(16);
		tx.setText(getString(R.string.app_name).concat(" ").concat(getAppVersionName()).concat("\n\n").concat(getString(R.string.about_description)).concat("\n\n")
				.concat(getString(R.string.about_copyright)).concat("\n\n").concat("\n\n").concat(getString(R.string.about_website)));

		// TODO: display @raw/license_short and @raw/recent_changes

		aboutWindow.setIcon(R.drawable.icon);
		aboutWindow.setTitle(R.string.about);
		aboutWindow.setView(tx);

		aboutWindow.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		aboutWindow.show();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case ACTIVITY_REQUEST_PREFERENCES_EDITOR:
				Log.d(dear2dear.TAG, "Back from preferences editor");
				break;

			default:
				Log.w(dear2dear.TAG, "Unknown activity request code " + requestCode);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {
			case ACTIVITY_REQUEST_PERMISSION_READ_CONTACTS: {
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					String message = getString(R.string.permissionReadContactsGranted);
					Log.d(dear2dear.TAG, message);
					showToast(message);
				} else {
					String message = getString(R.string.permissionReadContactsDenied);
					Log.e(dear2dear.TAG, message);
					showToast(message);
				}
				return;
			}
		}
	}

	private void startFromScratch() {
		// Hide restart button
		restartButton.setVisibility(View.INVISIBLE);

		String message = getString(R.string.sendToWhomText);
		Log.d(dear2dear.TAG, message);
		tv.setText(message);

		List<Preference> contacts = preferencesHelper.getPreferencesByGroup(PreferenceGroup.GROUP_CONTACTS);
		for (int i = 0; i < buttons.length; i++) {
			Preference contact = contacts.get(i);
			String key = contact.key;
			String optionValue = sharedPreferences.getString(key, null);
			String optionLabel = sharedPreferences.getString(key + PreferencesHelper.VALUE_SUFFIX, null);
			destinationStepOption(buttons[i], optionLabel, optionValue);
			Log.d(dear2dear.TAG, "Added destination step option for " + key);
		}
	}

	private void destinationStepOption(final Button btn, final String optionLabel, final String optionValue) {
		if (optionLabel == null || optionValue == null) {
			btn.setVisibility(View.INVISIBLE);
		} else {
			btn.setVisibility(View.VISIBLE);
			btn.setText(optionLabel);
			btn.setOnClickListener(new Button.OnClickListener() {
				public void onClick(View v) {
					// Show restart button
					restartButton.setVisibility(View.VISIBLE);

					destinationStepChoiceLabel = optionLabel;
					destinationStepChoiceValue = optionValue;

					String message = getString(R.string.sendContactWhatText, destinationStepChoiceLabel);
					Log.d(dear2dear.TAG, message);
					tv.setText(message);

					List<Preference> messages = preferencesHelper.getPreferencesByGroup(PreferenceGroup.GROUP_MESSAGES);
					for (int i = 0; i < buttons.length; i++) {
						Preference preference = messages.get(i);
						String key = preference.key;
						String optionValue = sharedPreferences.getString(key, null);
						String optionLabel = optionValue;
						messageStepOption(buttons[i], optionLabel, optionValue);
						Log.d(dear2dear.TAG, "Added message step option for " + key);
					}
				}
			});
		}
	}

	private void messageStepOption(final Button btn, final String optionLabel, final String optionValue) {
		if (optionLabel == null || optionValue == null || "".equals(optionValue)) {
			btn.setVisibility(View.INVISIBLE);
		} else {
			btn.setVisibility(View.VISIBLE);
			btn.setText(optionLabel);
			btn.setOnClickListener(new Button.OnClickListener() {
				public void onClick(View v) {
					messageStepChoice = optionValue;
					destinationChoiceDetails = getPhoneNumberFromUri(destinationStepChoiceValue);
					StringBuffer message = new StringBuffer();
					message.append(getString(R.string.sendMessageToContactByMediaText, messageStepChoice, destinationStepChoiceLabel, getString(R.string.sms), destinationChoiceDetails));
					Log.d(dear2dear.TAG, message.toString());
					tv.setText(message);
					lastStepOption(buttons[0], getString(R.string.sendText));
					Log.d(dear2dear.TAG, "Added send step option");
					buttons[1].setVisibility(View.INVISIBLE);
					buttons[2].setVisibility(View.INVISIBLE);
				}
			});
		}
	}

	private void lastStepOption(final Button btn, String option) {
		btn.setText(option);
		btn.setVisibility(View.VISIBLE);
		btn.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				buttons[0].setVisibility(View.INVISIBLE);

				sendMessage();
			}
		});
	}

	private void sendMessage() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
				String message = getString(R.string.permissionSendSmsRationale);
				Log.d(dear2dear.TAG, message);
				showToast(message);
			}

			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS},  ACTIVITY_REQUEST_PERMISSION_SEND_SMS);

			return;
		}

		 if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE)) {
				String message = getString(R.string.permissionReadPhoneStateRationale);
				Log.d(dear2dear.TAG, message);
				showToast(message);
			}

			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE},  ACTIVITY_REQUEST_PERMISSION_READ_PHONE_STATE);

			return;
		}

		String message = getString(R.string.sendingMessageToContactText, messageStepChoice, destinationStepChoiceLabel, getString(R.string.sms), destinationChoiceDetails);

		sendingMessageProgressDialog.setMessage(message);
		sendingMessageProgressDialog.show();
		Log.d(TAG, message);
		sendSms();
	}

	private void showRetryButton() {
		buttons[0].setText(getString(R.string.retryText));
		buttons[0].setVisibility(View.VISIBLE);
		buttons[0].setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				buttons[0].setVisibility(View.INVISIBLE);

				sendMessage();
			}
		});
	}

	private String getPhoneNumberFromUri(String contactUri) {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_CONTACTS)) {
				String message = getString(R.string.permissionReadContactsRationale);
				Log.d(TAG, message);
				showToast(message);
			}

			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS},  ACTIVITY_REQUEST_PERMISSION_READ_CONTACTS);

			return null;
		}

		String number = null;
		Cursor cursor = managedQuery(Uri.parse(contactUri), null, null, null, null);
		int count = cursor.getCount();
		Log.d(dear2dear.TAG, "count=" + count);
		if (count < 1) {
			Log.e(dear2dear.TAG, "No match for" + contactUri);
		} else if (count > 1) {
			Log.e(dear2dear.TAG, "Too many matches for" + contactUri);
		} else {
			int idColumnIndex = cursor.getColumnIndexOrThrow(People._ID);
			Log.d(dear2dear.TAG, "idColumnIndex=" + idColumnIndex);
			int nameColumnIndex = cursor.getColumnIndexOrThrow(People.NAME);
			Log.d(dear2dear.TAG, "nameColumnIndex=" + nameColumnIndex);

			// Go to the first match
			cursor.moveToFirst();

			long id = cursor.getLong(idColumnIndex);
			Log.d(dear2dear.TAG, "id=" + id);
			String name = cursor.getString(nameColumnIndex);
			Log.d(dear2dear.TAG, "Found contact " + name);

			// Return a cursor that points to this contact's phone
			// numbers
			Uri.Builder builder = People.CONTENT_URI.buildUpon();
			ContentUris.appendId(builder, id);
			builder.appendEncodedPath(People.Phones.CONTENT_DIRECTORY);
			Uri phoneNumbersUri = builder.build();

			Cursor phonesCursor = managedQuery(phoneNumbersUri, new String[] {
					People.Phones._ID,
					People.Phones.NUMBER,
					People.Phones.TYPE
			}, People.Phones.TYPE + "=" + People.Phones.TYPE_MOBILE, null, null);
			int phonesCount = phonesCursor.getCount();
			Log.d(dear2dear.TAG, "phonesCount=" + phonesCount);

			// Check there's at least one phone number available
			if (phonesCount > 0 && phonesCursor.moveToFirst()) {
				int phoneColumnIndex = phonesCursor.getColumnIndexOrThrow(People.Phones.NUMBER);
				Log.d(dear2dear.TAG, "phoneColumnIndex=" + phoneColumnIndex);

				number = phonesCursor.getString(phoneColumnIndex);
				Log.d(dear2dear.TAG, "Found number " + number);
			} else {
				Log.d(dear2dear.TAG, "No number available");
			}
		}

		return number;
	}

	private String getNameFromUri(String contactUri) {
		String name = null;
		if (contactUri != null) {
			Cursor cursor = managedQuery(Uri.parse(contactUri), null, null, null, null);
			int count = cursor.getCount();
			Log.d(dear2dear.TAG, "count=" + count);
			if (count < 1) {
				Log.e(dear2dear.TAG, "No match for" + contactUri);
			} else if (count > 1) {
				Log.e(dear2dear.TAG, "Too many matches for" + contactUri);
			} else {
				int idColumnIndex = cursor.getColumnIndexOrThrow(People._ID);
				Log.d(dear2dear.TAG, "idColumnIndex=" + idColumnIndex);
				int nameColumnIndex = cursor.getColumnIndexOrThrow(People.NAME);
				Log.d(dear2dear.TAG, "nameColumnIndex=" + nameColumnIndex);

				// Go to the first match
				cursor.moveToFirst();

				long id = cursor.getLong(idColumnIndex);
				Log.d(dear2dear.TAG, "id=" + id);
				name = cursor.getString(nameColumnIndex);
				Log.d(dear2dear.TAG, "Found contact " + name);
			}
		}

		return name;
	}

	private void sendSms() {
		if (destinationChoiceDetails != null) {
			sendingMessageProgressDialog.show();
			SmsManager.getDefault().sendTextMessage(destinationChoiceDetails, null, messageStepChoice, sentIntent, deliveryIntent);
		} else {
			showToast("Could not find a phone number for " + destinationStepChoiceLabel);
		}
	}

	private void storeSms(String body, String address) {
		StringBuilder sb = new StringBuilder();

		try {
			Uri uri = Uri.parse("content://sms/sent");
			ContentResolver cr = getContentResolver();
			ContentValues cv = new ContentValues();
			cv.put("body", body);
			cv.put("address", address);

			sb.append("SMS to store:\n");
			sb.append(cv.toString());

			// Insert SMS
			uri = cr.insert(uri, cv);
			sb.append("\n\nResult: success");
		} catch (Exception e) {
			sb.append("\n\nResult: failure");
			Log.e(TAG, "Failed to store SMS", e);
		}

		Log.i(TAG, sb.toString());
	}
}
