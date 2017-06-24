package com.ecgproduct;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;

public class MainActivity extends AppCompatActivity
		implements OnClickListener, OnItemClickListener, BluetoothAdapter.LeScanCallback {
	private final static int REQUEST_PERMISSION_REQ_CODE = 34; // any 8-bit number

	private static final int REQUEST_ENABLE_BT = 1;
	private BluetoothAdapter mBluetoothAdapter = null;
	private BluetoothGatt mBluetoothGatt;
	private BluetoothGattCharacteristic mNotifyCharacteristic;
	public final static String HR_SERVICE_UUID = "00002a37-0000-1000-8000-00805f9b34fb";
	private final static UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	private LinkedBlockingDeque<Integer> mInputBuf;
	private LinkedBlockingDeque<Integer> mDrawBuf;
	private ListView deviceList;
	private Button btnScan;
	TextView tx_connect, tx_mac, tx_temper, tx_battery, tx_rssi, tx_acct, tx_sensor;
	Button btn_pass, btn_fail;
	private ECGChart mECGFlowChart;
	private LinearLayout lyt_graph;

	int currentPos = -1;
	Bledevices currentBle;
	String filename = "CALM_Report.csv";
	boolean isSensorDetected = false;
	int isECG = -1;
	float accX = 0;
	float accY = 0;
	float accZ = 0;
	int batteryAmount = 0;
	boolean isFlowMode = false;
	int nPercent = -1;
	boolean isFitMode = false;

	private LeDeviceListAdapter mLeDeviceListAdapter;

	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				Log.i("connect","connected");
				mBluetoothGatt.discoverServices();
				updateView(1, false, 0, 0, 0, 0);
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				Log.i("connect","disconnected");
				updateView(-1, false, 0, 0, 0, 0);
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.i("connect","discorverd");
				List<BluetoothGattService> services = gatt.getServices();
				setCharacteristic(services,HR_SERVICE_UUID);
			}
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.i("connect","Read");
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			Log.i("connect","changed");
			receiveData(characteristic);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Snackbar.make(view, "Will Send Current Result.", Snackbar.LENGTH_LONG)
						.setAction("Action", null).show();
				sendResult();
			}
		});
		currentBle = null;
		initViews();
		_checkPermission();

	}
	private boolean _checkPermission() {
		int nLog = ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH);
		Log.d("permisiontest", "nLog: " + nLog);
		if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
			// When user pressed Deny and still wants to use this functionality, show the rationale
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH)) {
				Log.d("permisiontest", "1 BLUETOOTH if");
			} else {
				Log.d("permisiontest", "1 BLUETOOTH else");
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				requestPermissions(new String[]{Manifest.permission.BLUETOOTH}, REQUEST_PERMISSION_REQ_CODE);
			}
		}
		if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
			// When user pressed Deny and still wants to use this functionality, show the rationale
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_ADMIN)) {
				Log.d("permisiontest", "2 BLUETOOTH_ADMIN if");
			} else {
				Log.d("permisiontest", "2 BLUETOOTH_ADMIN else");
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_PERMISSION_REQ_CODE);
			}
		}
		if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			// When user pressed Deny and still wants to use this functionality, show the rationale
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_REQ_CODE);
			}

		}
		if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			// When user pressed Deny and still wants to use this functionality, show the rationale
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION_REQ_CODE);
			}
		}
		if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
			// When user pressed Deny and still wants to use this functionality, show the rationale
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH)) {
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				requestPermissions(new String[]{Manifest.permission.BLUETOOTH}, REQUEST_PERMISSION_REQ_CODE);
			}
		}
		if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			// When user pressed Deny and still wants to use this functionality, show the rationale
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
				Log.d("permisiontest", "3 ACCESS_COARSE_LOCATION if");
			} else {
				Log.d("permisiontest", "3 ACCESS_COARSE_LOCATION else");
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSION_REQ_CODE);
			}
		}
		if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.NFC) != PackageManager.PERMISSION_GRANTED) {
			// When user pressed Deny and still wants to use this functionality, show the rationale
			if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.NFC)) {
				Log.d("permisiontest", "4 NFC if");
			} else {
				Log.d("permisiontest", "4 NFC else");
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				requestPermissions(new String[]{Manifest.permission.NFC}, REQUEST_PERMISSION_REQ_CODE);
			}
		}
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();

		//noinspection SimplifiableIfStatement
		if (id == R.id.send_action) {
			Snackbar.make(this.findViewById(android.R.id.content).getRootView(), "Will Send Current Result.", Snackbar.LENGTH_LONG)
					.setAction("Action", null).show();
			sendResult();
		}
		return super.onOptionsItemSelected(item);
	}
	public void graphModeFirst(View v) {
		if(!isFlowMode) {
			mECGFlowChart.setMode(0);
			isFlowMode = true;
		}
	}
	public void graphModeSecond(View v) {
		if(isFlowMode)
		{
			isFlowMode = false;
			mECGFlowChart.setMode(1);
		}
	}

	public void sendResult(){

//		String destLocationBackup = getApplicationContext().getFilesDir().getAbsolutePath() + "/" + "CALM_Backup.csv";
		String destLocationReal = Environment.getExternalStorageDirectory() + "/" + "Report" + "/" + filename;
//		File file = new File(destLocation1);
//		if(file.exists()){
//			Log.i("aaa","aaa");
//		}
		Intent emailIntent = new Intent(Intent.ACTION_SEND);
		// set the type to 'email'
		emailIntent .setType("text/csv");//vnd.android.cursor.dir/email");
		String to[] = {"steven.zhang86@gmail.com"};
		emailIntent .putExtra(Intent.EXTRA_EMAIL, to);
		// the attachment
		ArrayList<Uri> uris = new ArrayList<Uri>();
//		uris.add(Uri.parse("file://"+destLocationBackup));
		uris.add(Uri.parse("file://"+destLocationReal));
		emailIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
		emailIntent .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
// the mail subject
		emailIntent .putExtra(Intent.EXTRA_SUBJECT, "Subject");
		startActivity(Intent.createChooser(emailIntent , "Send email..."));
	}
	boolean doubleGraphClick = false;
	private void initViews() {
		deviceList = (ListView) findViewById(R.id.device_list);
		mLeDeviceListAdapter = new LeDeviceListAdapter();
		deviceList.setAdapter(mLeDeviceListAdapter);
		deviceList.setOnItemClickListener(this);
		deviceList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		lyt_graph = (LinearLayout) findViewById(R.id.lyt_graph);
		lyt_graph.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// Code here executes on main thread after user presses button
				if (doubleGraphClick) {
					isFitMode = !isFlowMode;
					if(isFlowMode)
						setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
					else
						setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
					Log.i("Click","Double");
				}
				doubleGraphClick = true;
				new Handler().postDelayed(new Runnable() {

					@Override
					public void run() {
						doubleGraphClick=false;
					}
				}, 1000);
			}
		});

		RadioGroup radioGroup;
		radioGroup = (RadioGroup) findViewById(R.id.rd_group);
		radioGroup.check(R.id.rd_second);

		tx_connect = (TextView) findViewById(R.id.tx_connect);
		tx_mac = (TextView) findViewById(R.id.tx_mac);
		tx_temper = (TextView) findViewById(R.id.tx_temp);
		tx_battery = (TextView) findViewById(R.id.tx_battery);
		tx_rssi = (TextView) findViewById(R.id.tx_rssi);
		tx_acct = (TextView) findViewById(R.id.tx_accelerate);
		tx_sensor = (TextView) findViewById(R.id.tx_touched);

		tx_connect.setText("Not Connected");
		tx_mac.setText("MAC: --:--:--");
		tx_rssi.setText("RSSI: --- dB");
		tx_battery.setText("Battery: -- %");
		tx_temper.setText("Temperature: --" + "\u2103");
		tx_acct.setText("Acceleration: X:-- Y:-- Z:--");
		tx_sensor.setText("Sensor Off");

		btnScan = (Button) findViewById(R.id.btn_scan);
		btnScan.setOnClickListener(this);

		btn_pass = (Button) findViewById(R.id.btn_pass);
		btn_pass.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// Code here executes on main thread after user presses button
				if(currentBle !=null)
					confirmOperate(true);
			}
		});

		btn_fail = (Button) findViewById(R.id.btn_fail);
		btn_fail.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if(currentBle !=null)
					confirmOperate(false);
				// Code here executes on main thread after user presses button
			}
		});

		//=========chart===============
		mInputBuf = new LinkedBlockingDeque<>();
		mDrawBuf = new LinkedBlockingDeque<>();
		final Random rand = new Random();
		mECGFlowChart = (ECGChart) findViewById(R.id.ecg_flow_chart);

		TimerTask drawEmitter = new TimerTask() {
			@Override
			public void run() {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if(isECG == 0){
							mECGFlowChart.setConnection(false);
							tx_connect.setTextColor(Color.parseColor("#fd5656"));
							tx_connect.setText("Connecting...");
						}
						else if(isECG == -1) {
							mECGFlowChart.setConnection(false);
							tx_connect.setTextColor(Color.parseColor("#bbbbbb"));
							tx_connect.setText("Not Connected");
							tx_mac.setText("MAC: --:--:--");
							tx_rssi.setText("RSSI: --- dB");
							tx_battery.setText("Battery: -- %");
							tx_temper.setText("Temperature: --" + "\u2103");
							tx_acct.setText("Acceleration: X:-- Y:-- Z:--");
							tx_sensor.setTextColor(Color.parseColor("#bbbbbb"));
							tx_sensor.setText("Sensor Off");
						}
						else {
							mECGFlowChart.setConnection(true);
							tx_connect.setTextColor(Color.parseColor("#00ff00"));
							tx_connect.setText("Connected");
							if (isSensorDetected) {
								tx_sensor.setTextColor(Color.parseColor("#fd5656"));
								tx_sensor.setText("Sensor On");
							} else {
								tx_sensor.setTextColor(Color.parseColor("#bbbbbb"));
								tx_sensor.setText("Sensor Off");
							}
//							tx_battery.setText("Battery: " + batteryAmount + " %");
							Log.i("battery",""+batteryAmount);
                            tx_mac.setText("MAC: "+currentBle.device.getAddress());
                            tx_rssi.setText("RSSI: " + currentBle.signal + " dB");
							calcBattery();
						}
					}
				});
			}
		};
		Timer timer = new Timer();
		timer.schedule(drawEmitter, 0, 100);
	}
	public void confirmOperate(final boolean isPass){
		final Dialog dpolling = new Dialog(MainActivity.this);
		dpolling.setContentView(R.layout.confirmdlg);
		dpolling.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		TextView tv = (TextView) dpolling.findViewById(R.id.addtext);
		if(isPass)
			tv.setText("This Device is Passed?\nAre you sure?");
		else
			tv.setText("This Device is Failed?\nAre you sure?");
		Button btnYes = (Button) dpolling.findViewById(R.id.btn_yes);
		Button btnNo = (Button) dpolling.findViewById(R.id.btn_no);
		btnYes.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				productOperate(isPass);
				dpolling.dismiss();

			}
		});
		btnNo.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dpolling.dismiss(); // dismiss the dialog
			}
		});
		dpolling.show();
	}
	public void calcBattery(){
		double fvolt = (double)batteryAmount /4095 * 0.6 *114 / 14;
		Log.i("battery1",""+fvolt);
		int fpercent = (int)((fvolt - 3.6) / (0.6) * 100);
		if(fpercent <= 100 && fpercent >= 0) {
			if (nPercent != -1)
				fpercent = (fpercent + nPercent) / 2;
			tx_battery.setText("Battery: " + fpercent + " %");
			nPercent = fpercent;
		}
		if(accX > 2048)
		    accX = 2048 - accX;
        accX = (float)((double)accX/2048 * 2);
		String strX = String.format("%.03f", accX );

        if(accY > 2048)
            accY = 2048 - accY;
        accY = (float)((double)accY/2048 * 2);
		String strY = String.format("%.03f", accX );

        if(accZ > 2048)
			accZ = 2048 - accZ;
		accZ = (float) ((double)accZ/2048 * 2);
		String strXZ = String.format("%.03f", accX );

        tx_acct.setText("Acceleration: X:" + strX + " g Y:" + strY + "g Z:" + strXZ +"g");

	}
	public void productOperate(final boolean ispass){
		File file;
		final InputStreamReader[] reader = {null};
		final BufferedReader[] in = {null};

		runOnUiThread(new Runnable() {
			@Override
			public void run() {

				File backupFile= new File(getApplicationContext().getFilesDir().getAbsolutePath() + "/" + "CALM_Backup.csv");
				//Make a backup file
				if(!backupFile.exists())
				{
					try {
						backupFile.createNewFile();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				BufferedWriter tempWriter = null;
				try {
					tempWriter = new BufferedWriter(new FileWriter(backupFile));
				} catch (IOException e) {
					e.printStackTrace();
				}
				if(tempWriter != null){
					try {
						if(ispass)
							tempWriter.write(currentBle.device.getAddress() +",Pass"+ System.getProperty("line.separator"));
						else
							tempWriter.write(currentBle.device.getAddress() +",Fail"+ System.getProperty("line.separator"));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				try {
					tempWriter.close();
				} catch (IOException e) {
					e.printStackTrace();
				}

				final File root = new File(Environment.getExternalStorageDirectory(), "Report");
				boolean success = false;
				//Create Folder
				if (!root.exists()) {
					success = root.mkdir();
				}

				//Create File
				File freal = new File(root,filename);
				File ftemp = new File(root,"CALM_Temp.csv");
				if(!freal.exists())
				{
					try {
						freal.createNewFile();
					} catch (IOException e) {
						e.printStackTrace();
						Toast.makeText(MainActivity.this, "Recorded Failed!", Toast.LENGTH_SHORT).show();
					}
				}
				if(!ftemp.exists())
				{
					try {
						ftemp.createNewFile();
					} catch (IOException e) {
						e.printStackTrace();
						Toast.makeText(MainActivity.this, "Recorded Failed!", Toast.LENGTH_SHORT).show();
					}
				}
				//Real Data
				BufferedReader bufferReal = null;
				BufferedWriter bufferTemp = null;
				try {
					bufferReal = new BufferedReader(new FileReader(freal));
					bufferTemp = new BufferedWriter(new FileWriter(ftemp));
				} catch (FileNotFoundException e) {
					e.printStackTrace();
					Toast.makeText(MainActivity.this, "Recorded Failed!", Toast.LENGTH_SHORT).show();
				} catch (IOException e) {
					e.printStackTrace();
					Toast.makeText(MainActivity.this, "Recorded Failed!", Toast.LENGTH_SHORT).show();
				}

				String currentLine;
				boolean isExist = false;
				int nCount = 0;
				int nSuccessedCount = 0;
				try {
					while((currentLine = bufferReal.readLine()) != null) {
                        // trim newline when comparing with lineToRemove
                        String trimmedLine = currentLine.trim();
						if (trimmedLine.contains(currentBle.device.getAddress()))
						{
							isExist = true;
							if(accX > 2048)
								accX = 2048 - accX;
							accX = (float)((double)accX/2048 * 2);
							String strX = String.format("%.03f", accX );

							if(accY > 2048)
								accY = 2048 - accY;
							accY = (float)((double)accY/2048 * 2);
							String strY = String.format("%.03f", accX );

							if(accZ > 2048)
								accZ = 2048 - accZ;
							accZ = (float) ((double)accZ/2048 * 2);
							String strXZ = String.format("%.03f", accX );

							if(ispass)
								bufferTemp.write(nCount + ","+currentBle.device.getAddress() +",Pass,"+
										nPercent + "," + strX + "," + strY + "," + strXZ+
										System.getProperty("line.separator"));
							else
								bufferTemp.write(nCount + ","+currentBle.device.getAddress() +",Fail,"+
										nPercent + "," + strX + "," + strY + "," + strXZ+
										System.getProperty("line.separator"));
						}
						else
							bufferTemp.write(trimmedLine + System.getProperty("line.separator"));
						if(trimmedLine.contains("Pass")){
							nSuccessedCount ++;
						}
						nCount ++;


                    }
				} catch (IOException e) {
					e.printStackTrace();
					Toast.makeText(MainActivity.this, "Recorded Failed!", Toast.LENGTH_SHORT).show();
				}
				if(!isExist){
					try {
						if(nCount == 0)
						{
							bufferTemp.write("Serial,MAC Address,Status,Battery,X,Y,Z" +
									System.getProperty("line.separator"));
							nCount++;
						}
						if(accX > 2048)
							accX = 2048 - accX;
						accX = (float)((double)accX/2048 * 2);
						String strX = String.format("%.03f", accX );

						if(accY > 2048)
							accY = 2048 - accY;
						accY = (float)((double)accY/2048 * 2);
						String strY = String.format("%.03f", accX );

						if(accZ > 2048)
							accZ = 2048 - accZ;
						accZ = (float) ((double)accZ/2048 * 2);
						String strXZ = String.format("%.03f", accX );

						if(ispass)
							bufferTemp.write(nCount + ","+currentBle.device.getAddress() +",Pass,"+
									nPercent + "," + strX + "," + strY + "," + strXZ+
									System.getProperty("line.separator"));
						else
							bufferTemp.write(nCount + ","+currentBle.device.getAddress() +",Fail,"+
									nPercent + "," + strX + "," + strY + "," + strXZ+
									System.getProperty("line.separator"));
					} catch (IOException e) {
						e.printStackTrace();
						Toast.makeText(MainActivity.this, "Recorded Failed!", Toast.LENGTH_SHORT).show();
					}
				}
				try {
					bufferReal.close();
					bufferTemp.close();
				} catch (IOException e) {
					e.printStackTrace();
					Toast.makeText(MainActivity.this, "Recorded Failed!", Toast.LENGTH_SHORT).show();
				}
				ftemp.renameTo(freal);
				Toast.makeText(MainActivity.this, "Successfully Recorded!", Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		initBLE();
	}

	private void initBLE() {
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
		}

		BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		if (mBluetoothAdapter == null) {
			finish();
			return;
		}

		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
	}
	private void startScanBLE() {
		if(mLeDeviceListAdapter.mLeDevices.size() > 0) {
			currentPos = -1;
			mLeDeviceListAdapter.mLeDevices.clear();
//			mLeDeviceListAdapter.mLeDevices.add(currentBle);
			unCheckall();
		}
		btnScan.setText("Stop");
		if (mBluetoothAdapter.isEnabled()) {
			mBluetoothAdapter.startLeScan(this);
		} else {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
	}
	private void stopScanBLE() {
		btnScan.setText("Scan");
		if (mBluetoothAdapter != null) {
			mBluetoothAdapter.stopLeScan(this);
			mBluetoothAdapter.cancelDiscovery();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// User chose not to enable Bluetooth.
		if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
			finish();
			return;
		} else if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_OK) {
			// initBLE();
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		if (btnScan.isSelected()) {
			btnScan.setText("Scan");
			stopScanBLE();
			btnScan.setSelected(false);
		} else {
			btnScan.setText("Stop");
			startScanBLE();
			btnScan.setSelected(true);
		}
	}
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		// Checks the orientation of the screen for landscape and portrait and set portrait mode always
		if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
			setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		} else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
			setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		// TODO Auto-generated method stub
		unCheckall();
		Bledevices bleDevice = mLeDeviceListAdapter.getDevice(position);
		if (bleDevice == null)
			return;
		disconnect();
		nPercent = -1;
		if(currentPos != position) {
			currentPos = position;
			connectBle(bleDevice);
			currentBle = bleDevice;
			CheckBox chb = (CheckBox) view.findViewById(R.id.rd_select);
			chb.setChecked(true);
		}
		else if(currentPos == position){
			currentPos = -1;
			CheckBox chb = (CheckBox) view.findViewById(R.id.rd_select);
			chb.setChecked(false);
		}

		Log.i("Check", ":"+position + ":" + bleDevice.device.getName());
	}
	public void disconnect() {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			return;
		}
		mBluetoothGatt.close();
		currentBle = null;
		updateView(-1, false, 0,0,0,0);
	}

	public void connectBle(Bledevices bledev){
		updateView(0,false,0,0,0,0);
		if (mBluetoothAdapter == null || bledev.device.getAddress() == null) {

		}

		final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(bledev.device.getAddress());
		if (device == null) {
		}

		if (device != null) {
			mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		}
		btnScan.setText("Scan");
		stopScanBLE();
		btnScan.setSelected(false);
		Log.i("connect", "Trying to create a new connection.");
	}
	long hrReceive = -1;
	int hrNumber = 0;

	public void receiveData(BluetoothGattCharacteristic characteristic)
	{
		if (hrReceive == -1) hrReceive = System.currentTimeMillis();
		long ellipse = System.currentTimeMillis() - hrReceive;
		if (ellipse > 1000) {
			hrReceive = System.currentTimeMillis();
			Log.d("hrRec", "" + hrNumber);
			hrNumber = 0;
		}
		int ecgVal;
		boolean isSensorDetected;
		isSensorDetected = isSensorDetected(characteristic.getValue()[0]);
		int hrsCount = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
		if (hrsCount == 0) return;
		Log.d("ecg", "" + hrsCount);
		int sum = 0;
		for (int i = 0; i < hrsCount; i++) {
			ecgVal = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 2 + i * 2);

			if(ecgVal >= Math.pow(2,15))
				mECGFlowChart.addEcgData(1250);
			else
				mECGFlowChart.addEcgData(ecgVal);	//mInputBuf.addLast(ecgVal);
			hrNumber++;
		}
		int batteryAmount = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 2 + hrsCount * 2);
		int accX = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 2 + hrsCount * 2 + 2);
		int accY = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 2 + hrsCount * 2 + 4);
		int accZ = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 2 + hrsCount * 2 + 6);
		updateView(1, isSensorDetected, batteryAmount, accX, accY, accZ);
	}

	public void updateView(int isECG, boolean isSensorDetected, int batteryAmount,int accX,int accY,int accZ){
		this.isECG = isECG;
		this.isSensorDetected = isSensorDetected;
		this.batteryAmount = batteryAmount;
		this.accX = accX;
		this.accY = accY;
		this.accZ = accZ;
	}




	public void unCheckall(){
		LeDeviceListAdapter adapter = ((LeDeviceListAdapter) deviceList.getAdapter());
		for (int i = 0; i < deviceList.getChildCount(); i++) {
			RelativeLayout itemLayout = (RelativeLayout) deviceList.getChildAt(i);
			CheckBox cb = (CheckBox) itemLayout.findViewById(R.id.rd_select);
			cb.setChecked(false);
			adapter.mCheckStates[i] = false;
		}
	}

	@Override
	public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
		// TODO Auto-generated method stub
		runOnUiThread(new Runnable() {
			@Override
			public void run() {			{
				Bledevices leDevice = new Bledevices();
				leDevice.device = device;
				leDevice.isChecked = false;
				leDevice.signal = rssi;
				mLeDeviceListAdapter.addDevice(leDevice);
				mLeDeviceListAdapter.notifyDataSetChanged();
			}
			}
		});
	}

	public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			return false;
		}
		boolean ok = false;
		if (mBluetoothGatt.setCharacteristicNotification(characteristic, enable)) {
			BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
			if (clientConfig != null) {
				if (enable) {
					ok = clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				} else {
					ok = clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
				}
				if (ok) {
					ok = mBluetoothGatt.writeDescriptor(clientConfig);
				}
			}
		}
		return ok;
	}
	private boolean isSensorDetected(final byte value) {
		return ((value & 0x01) != 0);
	}
	public void setCharacteristic(List<BluetoothGattService> gattServices, String uuid) {
		if (gattServices == null) {
			return;
		}
		for (BluetoothGattService gattService : gattServices) {
			List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
			for (final BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
				if (gattCharacteristic.getUuid().toString().equals(uuid)) {
					// System.out.println("liufafa uuid-->"+uuid.toString());
					final int charaProp = gattCharacteristic.getProperties();
					if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
						// If there is an active notification on a
						// characteristic, clear
						// it first so it doesn't update the data field on the
						// user interface.
						if (mNotifyCharacteristic != null) {
							// setCharacteristicNotification(mNotifyCharacteristic,
							// false);
							mNotifyCharacteristic = null;
						}
						readCharacteristic(gattCharacteristic);
					}
					if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
						mNotifyCharacteristic = gattCharacteristic;
						setCharacteristicNotification(gattCharacteristic, true);
					}

				}
			}
		}
	}

	public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			return;
		}
		mBluetoothGatt.readCharacteristic(characteristic);
	}

	boolean doubleBackToExitPressedOnce = false;
	@Override
	public void onBackPressed() {

		if (doubleBackToExitPressedOnce) {
			stopScanBLE();
			disconnect();
			super.onBackPressed();
			return;
		}
		this.doubleBackToExitPressedOnce = true;
		Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();

		new Handler().postDelayed(new Runnable() {

			@Override
			public void run() {
				doubleBackToExitPressedOnce=false;
			}
		}, 2000);
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		stopScanBLE();
		disconnect();
	}

	// Adapter for holding devices found through
	// scanning.//adapter///////////////////////////////////////////////////////////
	private class LeDeviceListAdapter extends BaseAdapter {
		private ArrayList<Bledevices> mLeDevices;
		private LayoutInflater mInflator;
		boolean mCheckStates[];
//		CheckBox checkBox;

		LeDeviceListAdapter() {
			super();
			mLeDevices = new ArrayList<>();
			mInflator = MainActivity.this.getLayoutInflater();
			mCheckStates = new boolean[0];
		}

		void addDevice(Bledevices dev) {

			if((dev.device.getName()!=null) && (dev.device.getName().contains("CALM"))) {
				int i;
				int listSize = mLeDevices.size();
				for (i = 0; i < listSize; i++) {
					if (mLeDevices.get(i).device.equals(dev.device)) {
						break;
					}
				}
				if (i >= listSize) {
					mLeDevices.add(dev);
					mCheckStates = Arrays.copyOf(mCheckStates, mCheckStates.length + 1);
				}
			}
		}

		Bledevices getDevice(int position) {
			return mLeDevices.get(position);
		}

		@Override
		public int getCount() {
			return mLeDevices.size();
		}

		@Override
		public Object getItem(int i) {
			return mLeDevices.get(i);
		}

		@Override
		public long getItemId(int i) {
			return i;
		}

		@Override
		public View getView(final int i, View view, ViewGroup viewGroup) {
			final ViewHolder viewHolder;
			// General ListView optimization code.
			if (view == null) {
				view = mInflator.inflate(R.layout.item_device, null);
				viewHolder = new ViewHolder();
				viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
				view.setTag(viewHolder);
			} else {
				viewHolder = (ViewHolder) view.getTag();
			}

			Bledevices bleDevice = mLeDevices.get(i);
			final String deviceName = bleDevice.device.getName();
			if (deviceName != null && deviceName.length() > 0) {
				viewHolder.deviceName.setText(deviceName);
			} else {
				viewHolder.deviceName.setText("Unknow device");
			}
			return view;
		}
	}

	private static class ViewHolder {
		TextView deviceName;
	}

	private class Bledevices {
		BluetoothDevice device;
		int signal = 0;
		boolean isChecked = false;
	}
}
