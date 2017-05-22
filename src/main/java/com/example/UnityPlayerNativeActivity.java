package com.example;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.internal.Constants;
import com.example.internal.Utils;
import com.example.wifi.WifiIntentReceiver;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * @deprecated It's recommended that you base your code directly on UnityPlayerActivity or make your own NativeActitivty implementation.
 **/
public class UnityPlayerNativeActivity extends UnityPlayerActivity {

	static String MQTTHOST = "tcp://132.72.81.113:1883";
	MqttAndroidClient client;
	UnityPlayerActivity unityPlayerActivity;

	String[] deviceArr;
	String[] topicsArr = {"responseIP", "sendstatus"};
	String[] lightBulbsTopicsArr={"responseIP","sendstatus"};
	String[] nestSmokeTopicsArr={"sendstatus"};
	String[] lyricWaterLeak={"waterPresentResp","requeststatusResp"};


	private SharedPreferences sharedPreferences;
	private String strUsername;
	private String strServer;
	private String strGroup;
	private int trackVal;
	Handler handler = new Handler();

	public static UnityPlayerNativeActivity instance;


	@Override
	protected void onCreate(Bundle savedInstanceState) {

		UnityPlayerNativeActivity.instance = this;
		Log.w("Unity", "UnityPlayerNativeActivity has been deprecated, please update your AndroidManifest to use UnityPlayerActivity instead");
		super.onCreate(savedInstanceState);


		try {

			String clientId = MqttClient.generateClientId();
			client = new MqttAndroidClient(this.getApplicationContext(), MQTTHOST, clientId);// change second value to MQTTHOST instead "tcp://broker.hivemq.com:1883"

			IMqttToken token = client.connect();
			token.setActionCallback(new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					// We are connected
					setSubscription("general_maintenance/devices_list/response_device_list");
					publishFunc("general_maintenance/devices_list/request_device_list", "1");
					displayBrokerStatus(true);
					unityPlayerActivity = new UnityPlayerActivity();
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					displayBrokerStatus(false);
				}
			});
		} catch (MqttException e) {
			e.printStackTrace();
		}


		client.setCallback(new MqttCallback() {
			@Override
			public void connectionLost(Throwable cause) {

			}

			@Override
			public void messageArrived(String topic, MqttMessage message) throws Exception {
				if (topic.contains("general_maintenance/devices_list/response_device_list")) {
					setAllSubscribes(message.toString());
					return;
				}
				String[] topicParts = topic.split("/");
				String Dtype = topicParts[1];
				String Dname = topicParts[2];
				String Dfunc = topicParts[3];
				switch (Dtype) {
					case "light_bulbs":
						switch (Dfunc) {
							case "sendstatus":
								unityPlayerActivity.mUnityPlayer.UnitySendMessage(Dtype + "-" + Dname, "onOffIndicator", message.toString());

								break;
							case "responseIP":
								unityPlayerActivity.mUnityPlayer.UnitySendMessage(Dtype + "-" + Dname, "enableDisableMenu", message.toString());

								break;
						}
						break;

					case "detectors":
						switch (Dname){
							case "lyric":
								switch (Dfunc) {
									case "sendstatus":
										unityPlayerActivity.mUnityPlayer.UnitySendMessage(Dtype + "-" + Dname, "updateDeviceStatus", message.toString());
										break;
									case "waterPresentResp":
										unityPlayerActivity.mUnityPlayer.UnitySendMessage(Dtype + "-" + Dname, "updateDeviceStatus", message.toString());
										break;
								}
								break;
							case "nest_smoke1":
								switch (Dfunc) {
									case "sendstatus":
										unityPlayerActivity.mUnityPlayer.UnitySendMessage(Dtype + "-" + Dname, "updateDeviceStatus", message.toString());
										break;

								}
								break;


						}

				}

			}

			@Override
			public void deliveryComplete(IMqttDeliveryToken token) {

			}
		});


		// Checking if the Location service is enabled in case of Android M or above users
		if (!Utils.isLocationAvailable(getApplicationContext())) {
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setMessage("Location service is not On. Users running Android M and above have to turn on location services for FIND to work properly");
			dialog.setPositiveButton("Enable Locations service", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface paramDialogInterface, int paramInt) {
					Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
					startActivity(myIntent);
				}
			});
			dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface paramDialogInterface, int paramInt) {
					logMeToast("Thank you!! Getting things in place.");
				}
			});
			dialog.show();
		}

		// Listener to the broadcast message from WifiIntent
		LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mMessageReceiver,
				new IntentFilter(Constants.TRACK_BCAST));

		// Getting values from Shared prefs for Tracking
		sharedPreferences = this.getSharedPreferences(Constants.PREFS_NAME, 0);
		strGroup = sharedPreferences.getString(Constants.GROUP_NAME, Constants.DEFAULT_GROUP);
		strUsername = sharedPreferences.getString(Constants.USER_NAME, Constants.DEFAULT_USERNAME);
		strServer = sharedPreferences.getString(Constants.SERVER_NAME, Constants.DEFAULT_SERVER);
		trackVal = sharedPreferences.getInt(Constants.TRACK_INTERVAL, Constants.DEFAULT_TRACKING_INTERVAL);


		handler.post(runnableCode);

	}


	public void displayBrokerStatus(boolean isConnected) {

		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		TextView brokerTextView = new TextView(UnityPlayerNativeActivity.this);
		if (isConnected) {
			brokerTextView.setText("online");
			brokerTextView.setTextColor(Color.GREEN);
		} else {
			brokerTextView.setText("offline");
			brokerTextView.setTextColor(Color.RED);
		}

		mUnityPlayer.addView(brokerTextView, params);
		brokerTextView.bringToFront();
	}


	private void setSubscription(String topic) {
		try {
			client.subscribe(topic, 0);
		} catch (MqttException e) {
			e.printStackTrace();
		}
	}

	private void publishFunc(String topic, String msg) {
		MqttMessage message = new MqttMessage((msg).getBytes());
		try {
			client.publish(topic, message);
		} catch (MqttException e) {
			e.printStackTrace();
		}
	}


	public String onTargetFound(String Dtype, String Dname) {
		sendMessageToMQTT(Dtype,Dname,"requestIP","checkIP/0");
		sendMessageToMQTT(Dtype, Dname, "requeststatus", "1");
		return "onTargetFound";
	}

	public String onTargetLost() {
		return "onTargetLost";
	}


	public void setAllSubscribes(String msg) {

		deviceArr = msg.split("#");
		String currTopic = "";
		for (String device : deviceArr) {
			String[] deviceParts = device.split("-");
			String[] currTopicsArr;
			switch (deviceParts[1]){

				case "esp1":
					currTopicsArr=lightBulbsTopicsArr;
					break;
				case "nest_smoke1":
					currTopicsArr=nestSmokeTopicsArr;
					break;
				case "lyric":
					currTopicsArr=lyricWaterLeak;
					break;
				default:
					currTopicsArr=topicsArr;
					break;
			}
			for (String topic : currTopicsArr) {
				currTopic = "devices/" + deviceParts[0] + "/" + deviceParts[1] + "/" + topic;
				setSubscription(currTopic);
			}
		}


	}

	public String sendMessageToMQTT(String Dtype, String Dname, String func, String msg) {
		try {
			String topic = "devices/" + Dtype + "/" + Dname + "/" + func;
			byte[] payload = msg.getBytes();
			MqttMessage message = new MqttMessage(payload);
			client.publish(topic, payload, 1, true);
		} catch (MqttException e) {
			e.printStackTrace();
		}
		return "sendMessageToMQTT";
	}

	//******************************NOT IN USE*******************************************************
	public void createButtonsForDebug() {

		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		params.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
		Button pingButton = new Button(UnityPlayerNativeActivity.this);


		pingButton.setId(0);
		pingButton.setText("    PING");

		pingButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				//pub();
				//Toast.makeText(UnityPlayerNativeActivity.this, "Hello, Button!", Toast.LENGTH_LONG).show();

			}
		});
		pingButton.setLayoutParams(params);
		mUnityPlayer.addView(pingButton, params);
		pingButton.bringToFront();

		Button onButton = new Button(UnityPlayerNativeActivity.this);


		onButton.setText("ON");
		onButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				Toast.makeText(UnityPlayerNativeActivity.this, "Hello, Button!", Toast.LENGTH_LONG).show();

			}
		});

		params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		params.addRule(RelativeLayout.RIGHT_OF, pingButton.getId());
		onButton.setLayoutParams(params);
		mUnityPlayer.addView(onButton, params);
		//pingButton.bringToFront();


		//RelativeLayout layout = (RelativeLayout) View.inflate(this, R.layout.activity_main, null);
		//mUnityPlayer.addView(layout);
		//View playerView = mUnityPlayer.getView();
		//setContentView(playerView);
	}
	//****************************END NOT IN USE*****************************************************

    //****************************FIND APP FUNCTIONS**************************************************
    // Getting the CurrentLocation from the received braodcast
	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String currLocation = intent.getStringExtra("location");
			//currLocView.setTextColor(getResources().getColor(R.color.currentLocationColor));
			//currLocView.setText(currLocation);
			Log.d("location", currLocation);
			Toast.makeText(getApplicationContext(), currLocation, Toast.LENGTH_LONG).show();
		}
	};

	// Timers to keep track of our Tracking period
	private Runnable runnableCode = new Runnable() {
		@Override
		public void run() {
			if (Build.VERSION.SDK_INT >= 23 ) {
				if(Utils.isWiFiAvailable(getApplicationContext()) && Utils.hasAnyLocationPermission(getApplicationContext())) {
					Intent intent = new Intent(getApplicationContext(), WifiIntentReceiver.class);
					intent.putExtra("event", Constants.TRACK_TAG);
					intent.putExtra("groupName", strGroup);
					intent.putExtra("userName", strUsername);
					intent.putExtra("serverName", strServer);
					intent.putExtra("locationName", sharedPreferences.getString(Constants.LOCATION_NAME, ""));
					startService(intent);
				}
			}
			else if (Build.VERSION.SDK_INT < 23) {
				if(Utils.isWiFiAvailable(getApplicationContext())) {
					Intent intent = new Intent(getApplicationContext(), WifiIntentReceiver.class);
					intent.putExtra("groupName", strGroup);
					intent.putExtra("userName", strUsername);
					intent.putExtra("serverName", strServer);
					intent.putExtra("locationName", sharedPreferences.getString(Constants.LOCATION_NAME, ""));
					startService(intent);
				}
			}
			else {
				return;
			}
			handler.postDelayed(runnableCode, 5000); //trackVal * 1000
		}
	};



	// Logging message in form of Toasts
	private void logMeToast(String message) {
		Log.d("logMeToast", message);
		toast(message);
	}

	private void toast(String s) {
		Toast.makeText(this, s, Toast.LENGTH_LONG).show();
	}

    //****************************END FIND APP FUNCTIONS**************************************************
}

