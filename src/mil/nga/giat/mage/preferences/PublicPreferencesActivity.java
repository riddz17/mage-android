package mil.nga.giat.mage.preferences;

import mil.nga.giat.mage.R;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

/**
 * Provides configuration driven settings that are available to the user. Check
 * publicpreferences.xml for the configuration.
 * 
 * @author wiedemannse
 * 
 */
public class PublicPreferencesActivity extends PreferenceActivity {
	
	PublicPreferenceFragment preference = new PublicPreferenceFragment();
	
	public static class PublicPreferenceFragment extends PreferenceFragmentSummary  {	    
		@Override
		public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.publicpreferences);
			addPreferencesFromResource(R.xml.mdkpublicpreferences);
			
			for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
				setSummary(getPreferenceScreen().getPreference(i));
			}
		}
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		    View view = super.onCreateView(inflater, container, savedInstanceState);
		    if (view != null) {
		        ListView listView = (ListView) view.findViewById(android.R.id.list);
		        listView.setPadding(listView.getPaddingLeft(), listView.getPaddingTop(), 0, listView.getPaddingBottom());
		    }
		    		    
		    return view;
		}
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getFragmentManager().beginTransaction().replace(android.R.id.content, preference).commit();
	}
}