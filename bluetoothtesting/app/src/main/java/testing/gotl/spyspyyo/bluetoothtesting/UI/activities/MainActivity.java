package testing.gotl.spyspyyo.bluetoothtesting.UI.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import testing.gotl.spyspyyo.bluetoothtesting.R;
import testing.gotl.spyspyyo.bluetoothtesting.global.App;
import testing.gotl.spyspyyo.bluetoothtesting.teststuff.BluetoothConnectionManagementTestActivity;

public class MainActivity extends GotLActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        myToolbar.setTitle("Guardians of the Light");
        setSupportActionBar(myToolbar);
        ActionBar a = getSupportActionBar();
        if (a==null){
            App.toast("damn it");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.testing_items, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_option){
            //todo:start the options activity
            startActivity(new Intent(getBaseContext(), BluetoothConnectionManagementTestActivity.class));
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        activeActivityRequiresServer = true;
        super.onResume();
    }
}