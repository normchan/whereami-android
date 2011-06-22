package normchan.andrwhere;

import android.content.Intent;
import android.os.Bundle;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;

public class WhereMap extends MapActivity {
	MapView mapView;
	
	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
        setContentView(R.layout.where_map);
        mapView = (MapView) findViewById(R.id.whereMap);
        mapView.setBuiltInZoomControls(true);
        mapView.displayZoomControls(false);
        
        Intent i = getIntent();
        GeoPoint point = new GeoPoint(i.getIntExtra("latitude", 0), i.getIntExtra("longitude", 0));
        mapView.getController().setZoom(17);
        mapView.getController().animateTo(point);
    }

	@Override
	protected boolean isRouteDisplayed() {
		// TODO Auto-generated method stub
		return false;
	}

}
