package normchan.andrwhere;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.tapjoy.TapjoyConnect;
import com.tapjoy.TapjoyNotifier;

public class WhereAmI extends Activity {
	private final static String TAG = WhereAmI.class.getName();
	private final static int MAX_ADDRESS_RESULTS = 5;

	private final static String ANDROID_MARKET_STORE_ORIGIN = "9501316539MlBw4oL47g7T0V378P39";
	private final static String AMAZON_APP_STORE_ORIGIN = "Huv8jki7Pn4291pTKnxqCVIsLI1dbd";
	private final static String GETJAR_APP_STORE_ORIGIN = "Y2DKw124ttShdOh7iUQniBKIVPfjDp";

	private enum LocationFoundState {NONE, FOUND, FOUND_GPS};
	private LocationFoundState state = LocationFoundState.NONE;
	private Location currentLocation = null;
	private List<Address> addresses = null;
	private List<String> addressStrings = null;
	private String locationProvider = null;
	private Geocoder geocoder = null;
	private String statusStr = null;
	private TextView statusTV = null;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> locationSearchFuture = null;
	// Need handler for callbacks to the UI thread
	private final Handler mHandler = new Handler();

	// Create runnable for posting
	final Runnable mUpdateResults = new Runnable() 
	{
		public void run() 
		{
			updateResultsInUi();
		}
	};
	
	
	private void updateResultsInUi() 
	{
		this.statusTV.setText(this.statusStr == null ? "" : this.statusStr);
	}

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        TapjoyConnect.requestTapjoyConnect(this, "b63569c2-e8ad-441a-ac6d-7ac6a506ad2d", "7zYdhcadk6jQhuRrPtdf");

        this.statusTV = (TextView)findViewById(R.id.geocoderStatusView);
        updateResultsInUi();
        detectStoreOrigin();
    }
    
    private void detectStoreOrigin() {
    	Log.d(TAG, "Attempting to detect origin app store...");
    	String origin = null;
		try {
			InputStream stream = this.getAssets().open("tapjoy.dat", AssetManager.ACCESS_BUFFER);
			byte[] bytes = new byte[30];
			stream.read(bytes, 0, 30);
			origin = new String(bytes);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.i(TAG, "Caught exception while attempting to detect origin app store", e);
		}

		String displayText = "Unknown origin app store";
		if (origin != null) {
			if (origin.equals(ANDROID_MARKET_STORE_ORIGIN))  {
				displayText = "From Android Market";
			} else if (origin.equals(AMAZON_APP_STORE_ORIGIN)) {
				displayText = "From Amazon App Store";
			} else if (origin.equals(GETJAR_APP_STORE_ORIGIN)) {
				displayText = "From GetJar App Store";
			}
		}
		((TextView)findViewById(R.id.storeOriginView)).setText(displayText);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.menu, menu);
    	return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu (Menu menu) {
    	boolean enabled = (this.state != LocationFoundState.NONE);
    	menu.findItem(R.id.mapItItem).setEnabled(enabled);
    	if (enabled && this.addresses != null && this.addresses.size() > 1)
    		menu.findItem(R.id.showAltItem).setEnabled(true);
    	else 
    		menu.findItem(R.id.showAltItem).setEnabled(false);

    	return super.onPrepareOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.mapItItem:
    		// Launch Map activity
    		Intent i = new Intent(WhereAmI.this, WhereMap.class);
    		i.putExtra("latitude", (int)(this.currentLocation.getLatitude() * 1E6));
    		i.putExtra("longitude", (int)(this.currentLocation.getLongitude() * 1E6));
    		startActivity(i);
    		break;

    	case R.id.showAltItem:
    		final CharSequence[] items = (CharSequence[])this.addressStrings.toArray(new CharSequence[1]);

    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
    		builder.setTitle("Alternate Locations");
    		builder.setItems(items, new DialogInterface.OnClickListener() {
    		    public void onClick(DialogInterface dialog, int item) {
    		    	WhereAmI.this.statusStr = "You are located at "+items[item]+".";
    		    	mHandler.post(mUpdateResults);
    		    }
    		});
    		AlertDialog alert = builder.create();
    		alert.show();
    		break;

//    	case R.id.balanceItem:
//    		TapjoyConnect.getTapjoyConnectInstance().getTapPoints(new TapjoyNotifier() {
//
//				@Override
//				public void getUpdatePoints(String currencyName, int pointTotal) {
//					Log.d(TAG, "Current point balance: "+pointTotal);
//    		    	WhereAmI.this.statusStr = "Current point balance: "+pointTotal;
//    		    	mHandler.post(mUpdateResults);
//				}
//
//				@Override
//				public void getUpdatePointsFailed(String error) {
//					Log.d(TAG, "Failed to get point balance from server.");
//    		    	WhereAmI.this.statusStr = "Failed to get point balance from server.";
//    		    	mHandler.post(mUpdateResults);
//				}
//    			
//    		});
//    		break;
//    		
//    	case R.id.earnItem:
//			Log.d(TAG, "Showing offer wall...");
//    		TapjoyConnect.getTapjoyConnectInstance().showOffers();
//    		break;
    	}
    	

    	return true;
    }

    public void onClickHandler(View view) {
    	switch (view.getId()) {
    	case R.id.whereButton:
    		Toast.makeText(this, "Checking current location...", Toast.LENGTH_SHORT).show();
    		
    		initLocationMgr();
       		break;

    	case R.id.clearButton:
    		clearAll();
    		break;
    	}
    }
    
    private void clearAll() {
    	this.state = LocationFoundState.NONE;
    	this.addresses = null;
    	
		((EditText)findViewById(R.id.timeField)).setText("");
		((EditText)findViewById(R.id.latitudeField)).setText("");
		((EditText)findViewById(R.id.longitudeField)).setText("");
		((EditText)findViewById(R.id.altitudeField)).setText("");
		((EditText)findViewById(R.id.bearingField)).setText("");
		((EditText)findViewById(R.id.speedField)).setText("");
    	WhereAmI.this.statusStr = null;
    	updateResultsInUi();
    }

    private void initLocationMgr() {
    	final LocationManager locationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
    	// Define a listener that responds to location updates

    	final LocationListener locationListener = new LocationListener() {
    	    public void onLocationChanged(Location location) {
    	    	if (receivedAdequateLocation(location)) {
    	    		WhereAmI.this.locationSearchFuture.cancel(true);
    	    		locationManager.removeUpdates(this);
    	    		processLocation(location);
    	    	}
    	    }

    	    public void onStatusChanged(String provider, int status, Bundle extras) {}

    	    public void onProviderEnabled(String provider) {}

    	    public void onProviderDisabled(String provider) {}
    	  };

      	CheckBox checkbox = (CheckBox)findViewById(R.id.gpsCheckbox);
      	if (checkbox.isChecked())
      		this.locationProvider = LocationManager.GPS_PROVIDER;
      	else 
          	this.locationProvider = LocationManager.NETWORK_PROVIDER;
    	// Register the listener with the Location Manager to receive location updates
    	locationManager.requestLocationUpdates(this.locationProvider, 0, 0, locationListener);

    	this.locationSearchFuture = scheduler.schedule(new Runnable() {
			@Override
			public void run() {
				Log.d(TAG, "cancelling location search.");
				locationManager.removeUpdates(locationListener);
		    	WhereAmI.this.statusStr = "Location provider failed to retrieve current location.";
		    	mHandler.post(mUpdateResults);
			}
    	}, 20, TimeUnit.SECONDS);
    }
    
    private boolean receivedAdequateLocation(Location location) {
    	return location != null;
    }
    
    private void processLocation(Location location) {
    	this.currentLocation = location;
    	
    	DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
		((EditText)findViewById(R.id.timeField)).setText(dateFormat.format(new Date(location.getTime())));
		
    	DecimalFormat decimalFormat = new DecimalFormat("0.00");
		((EditText)findViewById(R.id.latitudeField)).setText(Location.convert(location.getLatitude(), Location.FORMAT_MINUTES));
		((EditText)findViewById(R.id.longitudeField)).setText(Location.convert(location.getLongitude(), Location.FORMAT_MINUTES));
		((EditText)findViewById(R.id.altitudeField)).setText(decimalFormat.format(location.getAltitude()));
		((EditText)findViewById(R.id.bearingField)).setText(decimalFormat.format(location.getBearing()));
		((EditText)findViewById(R.id.speedField)).setText(decimalFormat.format(convertToMPH(location.getSpeed())));
		
		this.statusStr = "No address found";
		try {
			this.addresses = getGeocoder().getFromLocation(location.getLatitude(), location.getLongitude(), MAX_ADDRESS_RESULTS);
			if (!addresses.isEmpty()) {
				initAlternateAddresses();
				this.statusStr = "You are located at "+this.addressStrings.get(0)+".";
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        updateResultsInUi();
		
		if (this.locationProvider.equals(LocationManager.GPS_PROVIDER))
			this.state = LocationFoundState.FOUND_GPS;
		else
			this.state = LocationFoundState.FOUND;
    }
    
    private double convertToMPH(float metersPerSecond) {
    	return (metersPerSecond * 2.232);
    }
    
    private void initAlternateAddresses() {
    	this.addressStrings = new ArrayList<String>();
    	for (int i = 0; i < this.addresses.size(); i++) {
    		Address address = this.addresses.get(i);
    		StringBuffer addressBuffer = new StringBuffer();
			for (int j = 0; j <= address.getMaxAddressLineIndex(); j++) {
				if (j != 0)
					addressBuffer.append(" ");
				addressBuffer.append(address.getAddressLine(j));
			}
			this.addressStrings.add(addressBuffer.toString());
    	}
    }
    
    private Geocoder getGeocoder() {
    	if (geocoder == null) {
    		geocoder = new Geocoder(this);
    	}
    	return geocoder;
    }
}