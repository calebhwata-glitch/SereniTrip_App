package com.example.serenitrip;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class SereniTrip extends AppCompatActivity {

    private static final int REQUEST_LOCATION = 1;

    private MapView map;
    private EditText destinationInput;
    private Button btnGo;
    private FusedLocationProviderClient fusedLocationClient;
    private GeoPoint currentLocationPoint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable edge-to-edge UI
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_sereni_trip);

        map = findViewById(R.id.map);
        map.setMultiTouchControls(true);

        destinationInput = findViewById(R.id.destinationInput);
        btnGo = findViewById(R.id.btnGo);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Request location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        } else {
            getCurrentLocation();
        }

        btnGo.setOnClickListener(view -> {
            String destination = destinationInput.getText().toString();
            if (!destination.isEmpty() && currentLocationPoint != null) {
                new GeocodeTask().execute(destination);
            } else {
                Toast.makeText(SereniTrip.this, "Waiting for location or enter destination", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    currentLocationPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                    map.getController().setZoom(15.0);
                    map.getController().setCenter(currentLocationPoint);

                    Marker startMarker = new Marker(map);
                    startMarker.setPosition(currentLocationPoint);
                    startMarker.setTitle("You are here");
                    map.getOverlays().add(startMarker);
                }
            });
        }
    }

    private class GeocodeTask extends AsyncTask<String, Void, GeoPoint> {
        @Override
        protected GeoPoint doInBackground(String... strings) {
            String locationName = strings[0];
            try {
                String urlStr = "https://nominatim.openstreetmap.org/search?format=json&q=" + locationName.replace(" ", "%20");
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONArray jsonArray = new JSONArray(sb.toString());
                if (jsonArray.length() > 0) {
                    JSONObject obj = jsonArray.getJSONObject(0);
                    double lat = obj.getDouble("lat");
                    double lon = obj.getDouble("lon");
                    return new GeoPoint(lat, lon);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(GeoPoint destPoint) {
            if (destPoint != null) {
                new RouteTask().execute(currentLocationPoint, destPoint);
            } else {
                Toast.makeText(SereniTrip.this, "Destination not found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class RouteTask extends AsyncTask<GeoPoint, Void, ArrayList<GeoPoint>> {
        private GeoPoint destination;

        @Override
        protected ArrayList<GeoPoint> doInBackground(GeoPoint... points) {
            GeoPoint start = points[0];
            destination = points[1];
            ArrayList<GeoPoint> geoPoints = new ArrayList<>();
            try {
                String urlStr = "https://router.project-osrm.org/route/v1/driving/"
                        + start.getLongitude() + "," + start.getLatitude() + ";"
                        + destination.getLongitude() + "," + destination.getLatitude()
                        + "?overview=full&geometries=geojson";

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray coordinates = json.getJSONArray("routes").getJSONObject(0)
                        .getJSONObject("geometry").getJSONArray("coordinates");

                for (int i = 0; i < coordinates.length(); i++) {
                    JSONArray point = coordinates.getJSONArray(i);
                    geoPoints.add(new GeoPoint(point.getDouble(1), point.getDouble(0)));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return geoPoints;
        }

        @Override
        protected void onPostExecute(ArrayList<GeoPoint> geoPoints) {
            if (geoPoints.size() > 0) {
                Polyline lineOverlay = new Polyline();
                lineOverlay.setPoints(geoPoints);
                map.getOverlays().add(lineOverlay);

                Marker destMarker = new Marker(map);
                destMarker.setPosition(destination);
                destMarker.setTitle("Destination");
                map.getOverlays().add(destMarker);

                map.getController().setCenter(destination);
                map.invalidate();
            } else {
                Toast.makeText(SereniTrip.this, "Route not found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        }
    }
}
