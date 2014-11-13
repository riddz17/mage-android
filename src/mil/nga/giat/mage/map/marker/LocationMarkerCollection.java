package mil.nga.giat.mage.map.marker;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import mil.nga.giat.mage.R;
import mil.nga.giat.mage.profile.MyProfileFragment;
import mil.nga.giat.mage.profile.ProfileActivity;
import mil.nga.giat.mage.sdk.datastore.location.Location;
import mil.nga.giat.mage.sdk.datastore.location.LocationGeometry;
import mil.nga.giat.mage.sdk.datastore.location.LocationHelper;
import mil.nga.giat.mage.sdk.datastore.location.LocationProperty;
import mil.nga.giat.mage.sdk.datastore.user.User;
import mil.nga.giat.mage.sdk.datastore.user.UserHelper;
import mil.nga.giat.mage.sdk.exceptions.UserException;
import mil.nga.giat.mage.sdk.preferences.PreferenceHelper;
import mil.nga.giat.mage.sdk.utils.MediaUtility;

import org.ocpsoft.prettytime.PrettyTime;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.MarkerManager;
import com.vividsolutions.jts.geom.Point;

public class LocationMarkerCollection implements PointCollection<Location>, OnMarkerClickListener, OnInfoWindowClickListener {

	private static final String LOG_NAME = LocationMarkerCollection.class.getName();

	protected GoogleMap map;
	protected Context context;
	protected Date latestLocationDate = new Date(0);

	protected Long clickedAccuracyCircleLocationId;
	protected Circle clickedAccuracyCircle;
	protected InfoWindowAdapter infoWindowAdpater = new LocationInfoWindowAdapter();

	protected boolean visible = true;

	protected Map<Long, Long> userIdToLocationId = new ConcurrentHashMap<Long, Long>();
	protected Map<Long, Marker> locationIdToMarker = new ConcurrentHashMap<Long, Marker>();
	protected Map<String, Location> markerIdToLocation = new ConcurrentHashMap<String, Location>();

	protected MarkerManager.Collection markerCollection;

	public LocationMarkerCollection(Context context, GoogleMap map) {
		this.context = context;
		this.map = map;

		MarkerManager markerManager = new MarkerManager(map);
		markerCollection = markerManager.newCollection();
	}

	@Override
	public void add(Location l) {
		final LocationGeometry lg = l.getLocationGeometry();
		if (lg != null) {
			
			// one user has one location
			Long locId = userIdToLocationId.get(l.getUser().getId());
			if(locId != null) {
				if(locationIdToMarker.get(locId) != null) {
					Location oldLoc = markerIdToLocation.get(locationIdToMarker.get(locId).getId());
					if(oldLoc.getTimestamp().before(l.getTimestamp())) {
						remove(oldLoc);
					} else {
						removeOldMarkers();
						return;
					}
				}
			}
			
			// If I got an observation that I already have in my list
			// remove it from the map and clean-up my collections
			remove(l);

			Point point = lg.getGeometry().getCentroid();

			LatLng latLng = new LatLng(point.getY(), point.getX());
			MarkerOptions options = new MarkerOptions().position(latLng).visible(visible);

			Marker marker = markerCollection.addMarker(options);
			marker.setIcon(LocationBitmapFactory.bitmapDescriptor(context, l, l.getUser()));

			userIdToLocationId.put(l.getUser().getId(), l.getId());
			
			locationIdToMarker.put(l.getId(), marker);
			markerIdToLocation.put(marker.getId(), l);

			if (l.getTimestamp().after(latestLocationDate)) {
				latestLocationDate = l.getTimestamp();
			}
			removeOldMarkers();
		}
	}

	@Override
	public void addAll(Collection<Location> locations) {
		for (Location l : locations) {
			add(l);
		}
	}

	// TODO: this should preserve latestLocationDate
	@Override
	public void remove(Location l) {
		Marker marker = locationIdToMarker.remove(l.getId());
		if (marker != null) {
			markerIdToLocation.remove(marker.getId());
			markerCollection.remove(marker);
			marker.remove();
		}
	}
	
	@Override
	public void onInfoWindowClick(Marker marker) {
		Location l = markerIdToLocation.get(marker.getId());

		if (l == null) {
			return;
		}
		
		Intent profileView = new Intent(context, ProfileActivity.class);
		profileView.putExtra(MyProfileFragment.USER_ID, l.getUser().getRemoteId());
		context.startActivity(profileView);
	}

	@Override
	public boolean onMarkerClick(Marker marker) {
		Location l = markerIdToLocation.get(marker.getId());

		if (l == null) {
			return false;
		}

		final LocationGeometry lg = l.getLocationGeometry();
		if (lg != null) {
			Point point = lg.getGeometry().getCentroid();
			LatLng latLng = new LatLng(point.getY(), point.getX());
			LocationProperty accuracyProperty = l.getPropertiesMap().get("accuracy");
			if (accuracyProperty != null && !accuracyProperty.getValue().toString().trim().isEmpty()) {
				try {
					Float accuracy = Float.valueOf(accuracyProperty.getValue().toString());
					if (clickedAccuracyCircle != null) {
						clickedAccuracyCircle.remove();
					}
					clickedAccuracyCircle = map.addCircle(new CircleOptions().center(latLng).radius(accuracy).fillColor(0x1D43b0ff).strokeColor(0x620069cc).strokeWidth(1.0f));
					clickedAccuracyCircleLocationId = l.getId();
				} catch (NumberFormatException nfe) {
					Log.e(LOG_NAME, "Problem adding accuracy circle to the map.", nfe);
				}
			}
		}

		map.setInfoWindowAdapter(infoWindowAdpater);
		marker.setIcon(LocationBitmapFactory.bitmapDescriptor(context, l, l.getUser()));
		marker.showInfoWindow();
		return true;
	}

	public boolean offMarkerClick() {
		if (clickedAccuracyCircle != null) {
			clickedAccuracyCircle.remove();
			clickedAccuracyCircle = null;
		}
		return true;
	}

	@Override
	public void refreshMarkerIcons() {
		for (Marker m : markerCollection.getMarkers()) {
			Location tl = markerIdToLocation.get(m.getId());
			if (tl != null) {
				boolean showWindow = m.isInfoWindowShown();
				try {
					m.setIcon(LocationBitmapFactory.bitmapDescriptor(context, tl, UserHelper.getInstance(context).read(tl.getUser().getId())));
				} catch (UserException ue) {
					Log.e(LOG_NAME, "Error refreshing the icon for user: " + tl.getUser().getId(), ue);
				}
				if (showWindow) {
					m.showInfoWindow();
				}
			}
		}
	}

	@Override
	public void clear() {
		clickedAccuracyCircle = null;
		locationIdToMarker.clear();
		markerIdToLocation.clear();
		markerCollection.clear();
		latestLocationDate = new Date(0);
	}

	@Override
	public void onCameraChange(CameraPosition cameraPosition) {
		// Don't care about this, I am not clustered
	}

	@Override
	public void setVisibility(boolean visible) {
		if (this.visible == visible)
			return;

		this.visible = visible;
		for (Marker m : locationIdToMarker.values()) {
			m.setVisible(visible);
		}
		if (clickedAccuracyCircle != null) {
			clickedAccuracyCircle.setVisible(visible);
		}
	}

	@Override
	public boolean isVisible() {
		return this.visible;
	}

	@Override
	public Date getLatestDate() {
		return latestLocationDate;
	}

	/**
	 * Used to remove markers for locations that have been removed from the local datastore.
	 */
	public void removeOldMarkers() {
		LocationHelper lh = LocationHelper.getInstance(context.getApplicationContext());
		Set<Long> locationIds = locationIdToMarker.keySet();
		for (Long locationId : locationIds) {
			Location locationExists = new Location();
			locationExists.setId(locationId);
			if (!lh.exists(locationExists)) {
				Marker marker = locationIdToMarker.remove(locationId);
				if (marker != null) {
					markerIdToLocation.remove(marker.getId());
					marker.remove();
				}

				if (clickedAccuracyCircleLocationId != null && clickedAccuracyCircleLocationId.equals(locationId)) {
					if (clickedAccuracyCircle != null) {
						clickedAccuracyCircle.remove();
						clickedAccuracyCircle = null;
					}
				}
			}
		}
	}

	private class LocationInfoWindowAdapter implements InfoWindowAdapter {

		private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm zz", Locale.ENGLISH);

		@Override
		public View getInfoContents(Marker marker) {
			final Location location = markerIdToLocation.get(marker.getId());
			if (location == null) {
				return null;
			}
			User user = location.getUser();

			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View v = inflater.inflate(R.layout.people_list_item, null);
			
			ImageView iconView = (ImageView) v.findViewById(R.id.iconImageView);
			if (location.getUser().getLocalAvatarPath() != null) {
				iconView.setImageBitmap(MediaUtility.resizeAndRoundCorners(BitmapFactory.decodeFile(location.getUser().getLocalAvatarPath()), 128));
			} else if (location.getUser().getAvatarUrl() != null) {
				new DownloadImageTask(marker, context, user).execute(location.getUser().getAvatarUrl() + "?access_token=" + PreferenceHelper.getInstance(context).getValue(R.string.tokenKey));
			}
			
			TextView location_name = (TextView) v.findViewById(R.id.location_name);
			location_name.setText(user.getFirstname() + " " + user.getLastname());

			TextView location_email = (TextView) v.findViewById(R.id.location_email);
			String email = user.getEmail();
			if (email != null && !email.trim().isEmpty()) {
				location_email.setVisibility(View.VISIBLE);
				location_email.setText(email);
			} else {
				location_email.setVisibility(View.GONE);
			}

			// set date
			TextView location_date = (TextView) v.findViewById(R.id.location_date);

			String timeText = sdf.format(location.getTimestamp());
			Boolean prettyPrint = PreferenceHelper.getInstance(context).getValue(R.string.prettyPrintLocationDatesKey, Boolean.class, R.string.prettyPrintLocationDatesDefaultValue);
			if (prettyPrint) {
				// timeText = DateUtils.getRelativeTimeSpanString(location.getTimestamp().getTime(), System.currentTimeMillis(), 0, DateUtils.FORMAT_ABBREV_RELATIVE).toString();
				timeText = new PrettyTime().format(location.getTimestamp());
			}
			location_date.setText(timeText);

			return v;
		}

		@Override
		public View getInfoWindow(Marker marker) {
			return null; // Use default info window for now
		}
	}
	
	private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
	    Marker marker;
	    Context context;
	    User user;

	    public DownloadImageTask(Marker marker, Context c, User u) {
	        this.marker = marker;
	        this.context = c;
	        this.user = u;
	    }

	    protected Bitmap doInBackground(String... urls) {
	        String urldisplay = urls[0];
	        Bitmap mIcon11 = null;
	        try {
	            InputStream in = new java.net.URL(urldisplay).openStream();
	            mIcon11 = BitmapFactory.decodeStream(in);
	        } catch (Exception e) {
	            Log.e(LOG_NAME, e.getMessage());
	            e.printStackTrace();
	        }
	        return mIcon11;
	    }

	    protected void onPostExecute(Bitmap bitmap) {
	    	if (bitmap != null) {
	    		FileOutputStream out = null;
	    		try {
	    			String localPath = MediaUtility.getAvatarDirectory() + "/" + user.getId();
	    		    out = new FileOutputStream(localPath);
	    		    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
	    		    user.setLocalAvatarPath(localPath);
	    		    UserHelper.getInstance(context).update(user);
		    		marker.showInfoWindow();
	    		} catch (Exception e) {
	    		    e.printStackTrace();
	    		} finally {
	    		    try {
	    		        if (out != null) {
	    		            out.close();
	    		        }
	    		    } catch (IOException e) {
	    		        e.printStackTrace();
	    		    }
	    		}
	    	}
	    }
	}
}
