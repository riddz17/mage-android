package mil.nga.giat.mage.login;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONException;
import org.json.JSONObject;

import mil.nga.giat.mage.R;
import mil.nga.giat.mage.disclaimer.DisclaimerActivity;
import mil.nga.giat.mage.event.EventActivity;
import mil.nga.giat.mage.sdk.login.AbstractAccountTask;
import mil.nga.giat.mage.sdk.login.AccountDelegate;
import mil.nga.giat.mage.sdk.login.AccountStatus;
import mil.nga.giat.mage.sdk.login.OAuthLoginTask;
import mil.nga.giat.mage.sdk.login.OAuthSignupTask;
import mil.nga.giat.mage.sdk.utils.DeviceUuidFactory;

/**
 * Created by wnewman on 10/14/15.
 */
public class OAuthActivity extends FragmentActivity implements AccountDelegate {

    public enum OAuthType {
        SIGNIN,
        SIGINUP
    }

    private static final String LOG_NAME = OAuthActivity.class.getName();

    public static final String EXTRA_SERVER_URL = "EXTRA_SERVER_URL";
    public static final String EXTRA_OAUTH_URL = "EXTRA_OAUTH_URL";
    public static final String EXTRA_OAUTH_TYPE = "EXTRA_OAUTH_TYPE";

    private String serverURL;
    private String oauthURL;
    private OAuthType oauthType;

    private WebView webView;
    private View progress;
    private String uuid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oauth);

        serverURL = getIntent().getStringExtra(EXTRA_SERVER_URL);
        oauthURL = getIntent().getStringExtra(EXTRA_OAUTH_URL);
        oauthType = (OAuthType) getIntent().getSerializableExtra(EXTRA_OAUTH_TYPE);

        uuid = new DeviceUuidFactory(this).getDeviceUuid().toString();

        progress = findViewById(R.id.progress);

        webView = (WebView) findViewById(R.id.webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        webView.addJavascriptInterface(this, "Android");
        webView.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.INVISIBLE);

                if (url.startsWith(serverURL + "/auth/google/callback")) {
                    webView.setVisibility(View.INVISIBLE);
                    webView.loadUrl("javascript:Android.getLogin(JSON.stringify(login));");
                }
            }
        });

        webView.loadUrl(oauthURL + "?uid=" + uuid);
    }

    @JavascriptInterface
    public void getLogin(String login) {
        AbstractAccountTask loginTask = oauthType == OAuthType.SIGNIN ?
                new OAuthLoginTask(this, getApplicationContext()) :
                new OAuthSignupTask(this, getApplicationContext());

        loginTask.execute(new String[]{ login });
    }

    @Override
    public void finishAccount(AccountStatus accountStatus) {
        if (accountStatus.getStatus().equals(AccountStatus.Status.SUCCESSFUL_LOGIN)) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean showDisclaimer = sharedPreferences.getBoolean(getString(R.string.serverDisclaimerShow), false);

            Intent intent = showDisclaimer ?
                    new Intent(getApplicationContext(), DisclaimerActivity.class) :
                    new Intent(getApplicationContext(), EventActivity.class);

            startActivity(intent);
            finish();
        } else if (accountStatus.getStatus().equals(AccountStatus.Status.SUCCESSFUL_REGISTRATION)) {
            Intent intent = new Intent();
            intent.putExtra(LoginActivity.EXTRA_OAUTH_UNREGISTERED_DEVICE, true);
            setResult(RESULT_OK, intent);
            finish();
        } else if (accountStatus.getStatus().equals(AccountStatus.Status.SUCCESSFUL_SIGNUP)) {
            Intent intent = new Intent();
            setResult(RESULT_OK, intent);
            finish();
        } else if (accountStatus.getStatus().equals(AccountStatus.Status.FAILED_SIGNUP)) {
            JSONObject json = accountStatus.getAccountInformation();
            String errorMessage = "Your account could not be created, please contact your MAGE administrator";

            if (json.has("errorMessage")) {
                try {
                    errorMessage = json.getString("errorMessage");
                } catch (JSONException e) {
                }
            }

            Intent intent = new Intent();
            intent.putExtra(SignupActivity.EXTRA_OAUTH_ERROR, true);
            intent.putExtra(SignupActivity.EXTRA_OAUTH_ERROR_MESSAGE, errorMessage);
            setResult(RESULT_OK, intent);
            finish();
        } else {
            Intent intent = new Intent();
            intent.putExtra(LoginActivity.EXTRA_OAUTH_ERROR, true);
            setResult(RESULT_OK, intent);
            finish();
        }
    }
}
