package com.yoavst.lgemojiswitcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.readystatesoftware.systembartint.SystemBarTintManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;
import info.hoang8f.android.segmented.SegmentedGroup;

public class MainActivity extends Activity implements RadioGroup.OnCheckedChangeListener, View.OnClickListener {
	private static final String PREF_SELECTED_NAME = "selected";
	private static final String PREF_FIRST_TIME = "firstTime";
	private static final int PREF_LG = 0;
	private static final int PREF_GOOGLE = 1;
	private static final int PREF_IOS = 2;
	int selectedId = 0;
	ImageView mPreview;
	Button mApply;
	SharedPreferences mPreferences;
	private static final String EXTRA_FILENAME = "com.yoavst.lgemojiswitcher.extra.FILENAME";
	private static final String EMOJI_FONT_FILE_PATH = "/system/fonts";
	private static final String EMOJI_FONT_FILE_NAME = "NotoColorEmoji.ttf";
	private static final String EMOJI_FONT_FILE_NAME_OLD = "NotoColorEmoji.old";

	@SuppressLint("CommitPrefEdits")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// create our manager instance after the content view is set
		SystemBarTintManager tintManager = new SystemBarTintManager(this);
		// enable status bar tint
		tintManager.setStatusBarTintEnabled(true);
		tintManager.setStatusBarTintResource(R.color.color);
		mPreview = (ImageView) findViewById(R.id.preview);
		mApply = (Button) findViewById(R.id.apply);
		mApply.setOnClickListener(this);
		SegmentedGroup segmentedGroup = (SegmentedGroup) findViewById(R.id.segmented_group);
		segmentedGroup.setOnCheckedChangeListener(this);
		mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		int selected = mPreferences.getInt(PREF_SELECTED_NAME, 0);
		segmentedGroup.check(getViewIdFromId(selected));
		showPreview(selected);
		if (mPreferences.getBoolean(PREF_FIRST_TIME, true)) {
			copyAssets();
			mPreferences.edit().putBoolean(PREF_FIRST_TIME, false).commit();
		}
	}

	void showPreview(int selected) {
		switch (selected) {
			case 0:
				mPreview.setImageResource(R.drawable.lg);
				break;
			case 1:
				mPreview.setImageResource(R.drawable.google);
				break;
			case 2:
				mPreview.setImageResource(R.drawable.ios);
				break;

		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_donate) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://forum.xda-developers.com/donatetome.php?u=5053440")));
			return true;
		} else if (id == R.id.action_xda) {
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://forum.xda-developers.com/lg-g3/themes-apps/howto-change-lg-smiley-to-google-smiley-t2809012/post54091145"));
			startActivity(intent);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCheckedChanged(RadioGroup group, int i) {
		selectedId = getIdFromViewId(i);
		showPreview(selectedId);
	}

	@SuppressLint("CommitPrefEdits")
	@Override
	public void onClick(View v) {
		mPreferences.edit().putInt(PREF_SELECTED_NAME, selectedId).commit();
		String filename = getFontNameByNumber(selectedId);
		new EmojiSwitcher().setContext(this).setFilename(filename).execute();
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	public void showDone() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.finish_emoji)
				.setMessage(R.string.do_you_want_to_reboot)
				.setNeutralButton(android.R.string.no, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						Toast.makeText(MainActivity.this, R.string.remember_to_reboot, Toast.LENGTH_LONG).show();
					}
				}).setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				try {
					Runtime.getRuntime().exec("su -c reboot");
				} catch (IOException e) {
					Toast.makeText(MainActivity.this, R.string.failed_to_reboot, Toast.LENGTH_LONG).show();
				}
			}
		}).setCancelable(false)
				.show();
	}

	public void showFail() {
		Toast.makeText(this, getString(R.string.failed), Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onBackPressed() {
		// DO NOTHING
	}

	private class EmojiSwitcher extends AsyncTask<Boolean, Boolean, Boolean> {
		private ProgressDialog dialog = null;
		private Context context = null;
		private boolean suAvailable = false;
		private String filename;
		private List<String> suResult = null;

		public EmojiSwitcher setContext(Context context) {
			this.context = context;
			return this;
		}

		public EmojiSwitcher setFilename(String filename) {
			this.filename = filename;
			return this;
		}

		@Override
		protected void onPreExecute() {
			// We're creating a progress dialog here because we want the user to wait.
			// If in your app your user can just continue on with clicking other things,
			// don't do the dialog thing.
			dialog = new ProgressDialog(new ContextThemeWrapper(context, android.R.style.Theme_Holo_Light_Dialog));
			dialog.setMessage(getString(R.string.applying_message));
			dialog.setIndeterminate(true);
			dialog.setCancelable(false);
			dialog.show();
		}

		@SuppressLint("Assert")
		@Override
		protected Boolean doInBackground(Boolean... ignored) {
			// Let's do some SU stuff
			suAvailable = Shell.SU.available();
			if (suAvailable) {
				final String filePath = getExternalFilesDir("").getAbsolutePath() + File.separator + filename;
				File file = new File(filePath);
				if (!file.exists()) {
					Log.e("TAG", "file doesn't exists: " + filePath);
					return false;
				}
				String firstCommand = "cp -f " + filePath + " " + EMOJI_FONT_FILE_PATH;
				String secondCommand = "mv " + EMOJI_FONT_FILE_PATH + File.separator + EMOJI_FONT_FILE_NAME + " " + EMOJI_FONT_FILE_PATH + File.separator + EMOJI_FONT_FILE_NAME_OLD;
				String thirdCommand = "mv " + EMOJI_FONT_FILE_PATH + File.separator + filename + " " + EMOJI_FONT_FILE_PATH + File.separator + EMOJI_FONT_FILE_NAME;
				String forthCommand = "chmod 644 " + EMOJI_FONT_FILE_PATH + File.separator + EMOJI_FONT_FILE_NAME;
				if (BuildConfig.DEBUG) {
					Log.d("TAG", filePath);
					Log.d("TAG", firstCommand);
					Log.d("TAG", secondCommand);
					Log.d("TAG", thirdCommand);
					Log.d("TAG", forthCommand);
				}
				suResult = Shell.SU.run(new String[]{
						"mount -o rw,remount /system",
						firstCommand,
						secondCommand,
						thirdCommand,
						forthCommand,
						"mount -o ro,remount /system"
				});
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					assert false;
				}
				return suResult != null;
			} else return false;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			dialog.dismiss();
			if (result) showDone();
			else showFail();
		}
	}

	private void copyAssets() {
		AssetManager assetManager = getAssets();
		String[] files = null;
		try {
			files = assetManager.list("");
		} catch (IOException e) {
			Log.e("tag", "Failed to get asset file list.", e);
		}
		if (files != null) {
			for (String filename : files) {
				InputStream in;
				OutputStream out;
				try {
					in = assetManager.open(filename);
					File outFile = new File(getExternalFilesDir(null), filename);
					out = new FileOutputStream(outFile);
					copyFile(in, out);
					in.close();
					out.flush();
					out.close();
				} catch (IOException e) {
					Log.e("tag", "Failed to copy asset file: " + filename, e);
				}
			}
		}
	}

	private void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
	}

	private String getFontNameByNumber(int num) {
		return (num == PREF_LG ? "NotoColorEmoji_LG_Stock.ttf" : (num == PREF_GOOGLE ? "NotoColorEmoji_Google.ttf" : "NotoColorEmoji_IOS.ttf"));
	}

	private int getIdFromViewId(int viewId) {
		return (viewId == R.id.radio_lg ? PREF_LG : (viewId == R.id.radio_google ? PREF_GOOGLE : PREF_IOS));
	}

	private int getViewIdFromId(int id) {
		return (id == PREF_LG ? R.id.radio_lg : (id == PREF_GOOGLE ? R.id.radio_google : R.id.radio_ios));
	}
}
