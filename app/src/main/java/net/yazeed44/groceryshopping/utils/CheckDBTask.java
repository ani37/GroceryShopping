package net.yazeed44.groceryshopping.utils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.afollestad.materialdialogs.MaterialDialog;

import net.yazeed44.groceryshopping.R;
import net.yazeed44.groceryshopping.database.ItemsDB;
import net.yazeed44.groceryshopping.database.ItemsDBHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by yazeed44 on 1/6/15.
 */
public class CheckDBTask extends AsyncTask<Void, CheckDBTask.DatabaseAction, Void> {


    public static final int DOWNLOAD_BUFFER_SIZE = 16000;
    public static final String DOWNLOAD_FAILED = "DownloadFailed";
    public static final String TAG = "checkDBThread";
    public static final String DB_URL = "https://www.dropbox.com/s/raochv63zzzvhu0/shoppingitems.db?dl=1";
    public static final String DB_DOWNLOAD_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + ItemsDBHelper.DB_NAME;
    private static String mLocalDBPath;
    private WeakReference<Activity> mWeakReferenceActivity;

    public CheckDBTask(final Activity activity) {

        mWeakReferenceActivity = new WeakReference<>(activity);
    }

    @Override
    protected void onProgressUpdate(DatabaseAction... values) {
        super.onProgressUpdate(values);


        final DatabaseAction action = values[0];
        if (action == DatabaseAction.UPDATE_EXISTING_ONE) {

            final MaterialDialog updateDialog = ViewUtil.createDialog(mWeakReferenceActivity.get())
                    .negativeText(R.string.neg_btn_update_dialog)

                    .content(R.string.content_new_update)
                    .title(R.string.title_new_update)
                    .positiveText(R.string.pos_btn_update_dialog)

                    .callback(new MaterialDialog.Callback() {
                        @Override
                        public void onNegative(MaterialDialog materialDialog) {
                            materialDialog.dismiss();
                        }

                        @Override
                        public void onPositive(MaterialDialog materialDialog) {
                            materialDialog.dismiss();
                            new ReplaceDBTask().execute();


                        }
                    }).build();

            ViewUtil.stylePositiveButton(updateDialog);
            updateDialog.show();

        } else if (action == DatabaseAction.INSTALL_NEW_ONE) {

            new ReplaceDBTask().execute();
        }
    }

    @Override
    protected Void doInBackground(Void... params) {

        mLocalDBPath = DBUtil.getLocalDBPath(mWeakReferenceActivity.get());

        if (DBUtil.localDBExists(mWeakReferenceActivity.get())) {

            if (isNetworkAvailable() && newUpdateExists()) {
                //There's new update

                publishProgress(DatabaseAction.UPDATE_EXISTING_ONE);

            }

        } else {
            //Download and install database
            publishProgress(DatabaseAction.INSTALL_NEW_ONE);


        }


        return null;
    }

    private boolean isPathValid(final String path) {

        if (path == null || DOWNLOAD_FAILED.equals(path)) {
            Log.e(TAG, "There's problem with the downloaded db   " + path);
            return false;
        }

        return true;
    }

    private void deleteDownloadedDB() {
        new File(DB_DOWNLOAD_PATH).delete();
    }

    //Download a new db then replace the local db
    private void replaceLocalDB() {

        final String newDatabasePath = downloadDatabase();

        if (!isPathValid(newDatabasePath)) {
            //TODO Error handle
            throw new IllegalStateException("The database haven't downloaded successfully !!");
        }

        deleteLocalDB();
        // ItemsDB.initInstance(ItemsDBHelper.createInstance(mWeakReferenceActivity));
        final ItemsDBHelper helper = DBUtil.createEmptyDB();

        copyNewDB(newDatabasePath);
        helper.close();
        ItemsDB.initInstance(ItemsDBHelper.createInstance(mWeakReferenceActivity.get()));
        deleteDownloadedDB();
        Log.i("replaceLocalDB", "Set the new DB Successfully");
    }

    private void copyNewDB(final String newDBPath) {
        try {

            final InputStream inputStream = new FileInputStream(newDBPath);
            final OutputStream outputStream = new FileOutputStream(mLocalDBPath);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.flush();

        } catch (IOException e) {
            Log.e("copyNewDB", e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) mWeakReferenceActivity.get().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void deleteLocalDB() {
        new File(mLocalDBPath).getParentFile().delete();
    }

    private boolean newUpdateExists() {

        //Download new database then compare versions between the current one and the downloaded one
        //TODO Think of new method


        final String newDatabasePath = downloadDatabase();

        boolean newUpdateExists;
        if (!isPathValid(newDatabasePath)) {
            //TODO error handle
            return false;
        }

        newUpdateExists = getDBVersion(newDatabasePath) > getDBVersion(mLocalDBPath);
        Log.d("isThereNewUpdate", newUpdateExists + "");

        if (!newUpdateExists) {
            //Delete the downloaded db
            deleteDownloadedDB();
        }


        return newUpdateExists;

    }

    private int getDBVersion(final String dbPath) {
        final int dbVersion = DBUtil.getDBVersion(dbPath);
        Log.d("getDBVersion", dbPath + "   version is " + dbVersion);
        return dbVersion;
    }

    private String downloadDatabase() {

        final File outputFile = new File(DB_DOWNLOAD_PATH);

        if (outputFile.exists()) {
            return DB_DOWNLOAD_PATH;
        } else {
            try {
                outputFile.createNewFile();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
            }
        }

        String dbPath = DOWNLOAD_FAILED;


        try {
            // Log.d(TAG, "downloading database");
            final URL url = new URL(DB_URL);
                        /* Open a connection to that URL. */
            URLConnection urlConnection = url.openConnection();
            urlConnection.setUseCaches(false);

            //Start download
            final BufferedInputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());

            final FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            final BufferedOutputStream outputStream = new BufferedOutputStream(fileOutputStream, DOWNLOAD_BUFFER_SIZE);
            final byte[] data = new byte[DOWNLOAD_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(data, 0, data.length)) >= 0) {
                outputStream.write(data, 0, bytesRead);
            }

            outputStream.close();
            fileOutputStream.close();
            inputStream.close();

            dbPath = outputFile.getAbsolutePath();
            Log.i("downloadDatabase", "Database successfully downloaded + \n " + dbPath);

        } catch (IOException e) {
            Log.e(TAG, "downloadDatabase Error: ", e);
            return DOWNLOAD_FAILED;
        } catch (NullPointerException e) {
            Log.e(TAG, "downloadDatabase Error: ", e);
            return DOWNLOAD_FAILED;
        } catch (Exception e) {
            Log.e(TAG, "downloadDatabase Error: ", e);
            return DOWNLOAD_FAILED;
        }
        return dbPath;

    }


    public enum DatabaseAction {
        INSTALL_NEW_ONE, UPDATE_EXISTING_ONE
    }

    private class ReplaceDBTask extends AsyncTask<Void, Void, Void> {

        private ProgressDialog loadingDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loadingDialog = createDbLoadingDialog();
            loadingDialog.show();
        }

        private ProgressDialog createDbLoadingDialog() {
            final ProgressDialog dialog = new ProgressDialog(mWeakReferenceActivity.get());
            dialog.setTitle(R.string.title_loading_db);
            dialog.setMessage(mWeakReferenceActivity.get().getResources().getString(R.string.content_loading_db));
            dialog.setCancelable(false);
            dialog.setProgressStyle(R.attr.progressBarStyle);

            return dialog;
        }


        @Override
        protected Void doInBackground(Void... params) {
            replaceLocalDB();

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            loadingDialog.dismiss();
        }
    }
}