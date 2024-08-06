package nl.lightbase;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.AsyncTask;
import androidx.annotation.Nullable;
import android.util.Log;
import android.util.Pair;
import android.net.Uri;

import com.facebook.infer.annotation.Assertions;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.google.vr.sdk.widgets.pano.VrPanoramaEventListener;
import com.google.vr.sdk.widgets.pano.VrPanoramaView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.apache.commons.io.IOUtils;


public class PanoramaView extends VrPanoramaView implements LifecycleEventListener {
    private static final String LOG_TAG = "PanoramaView";
    private final static String SCHEME_FILE = "file";

    private VrPanoramaView.Options _options = new VrPanoramaView.Options();
    private ImageLoaderTask imageLoaderTask;

    private Integer imageWidth;
    private Integer imageHeight;
    private String imageUrl;
    private Bitmap image;
    private ThemedReactContext _context;


    public PanoramaView(ThemedReactContext context) {
        super(context.getCurrentActivity());

        _context = context;

        context.addLifecycleEventListener(this);

        this.setEventListener(new ActivityEventListener());
        this.setDisplayMode(DisplayMode.EMBEDDED);
        this.setStereoModeButtonEnabled(false);
        this.setTransitionViewEnabled(false);
        this.setInfoButtonEnabled(false);
        this.setFullscreenButtonEnabled(false);

    }



    public void setImageSource(String value) {
        //Log.i(LOG_TAG, "Image source: " + value);

        if(value == null){
            return;
        }

        if (imageUrl != null && imageUrl.equals(value)) {
            return;
        }

        imageUrl = value;

        try {
            if (imageLoaderTask != null) {
                imageLoaderTask.cancel(true);
            }

            imageLoaderTask = new ImageLoaderTask();
            imageLoaderTask.execute(Pair.create(imageUrl, _options));

        } catch (Exception e) {
            emitImageLoadingFailed(e.toString());
        }
    }

    public void setDimensions(ReadableMap dimensions) {

        imageWidth = dimensions.getInt("width");
        imageHeight = dimensions.getInt("height");

        //Log.i(LOG_TAG, "Image dimensions: " + imageWidth + ", " + imageHeight);

    }

    public void setEnableTouchTracking(boolean enableTouchTracking) {
        setTouchTrackingEnabled(enableTouchTracking);
    }

    class ImageLoaderTask extends AsyncTask<Pair<String, VrPanoramaView.Options>, Void, Boolean> {

        protected Boolean doInBackground(Pair<String, VrPanoramaView.Options>... fileInformation) {

            if(isCancelled()){
                return false;
            }

            VrPanoramaView.Options _options = fileInformation[0].second;
            Uri imageUri = Uri.parse(fileInformation[0].first);
            String imagePath = imageUri.getPath();
            InputStream istr = null;

            try {

                String value = fileInformation[0].first;
                String scheme = imageUri.getScheme();

                if(scheme == null || scheme.equalsIgnoreCase(SCHEME_FILE)){
                    istr = new FileInputStream(new File(imageUri.getPath()));

                }
                else{
                    URL url = new URL(value);

                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    istr = connection.getInputStream();
                }

                Assertions.assertCondition(istr != null);
                image = decodeSampledBitmap(istr);
                image = rotateBitmapAccordingToExif(image, imagePath);

            } catch (Exception e) {
                if(isCancelled()){
                    return false;
                }

                Log.e(LOG_TAG, "Could not load file: " + e);

                emitImageLoadingFailed("Failed to load source file.");
                return false;

            } finally {
                try {
                    if(istr != null){
                        istr.close();
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Could not close input stream: " + e);
                }
            }

            emitEvent("onImageDownloaded", null);
            loadImageFromBitmap(image, _options);

            return true;
        }

        private Bitmap decodeSampledBitmap(InputStream inputStream) throws IOException {
            final byte[] bytes = IOUtils.toByteArray(inputStream);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

            if (imageWidth != 0 && imageHeight != 0) {
                // We reduce the size of the image for faster loading in the viewer
                options.inSampleSize = calculateInSampleSize(options, imageWidth, imageHeight);
                options.inJustDecodeBounds = false;
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            } else {
                return bitmap;
            }
        }

        private Bitmap rotateBitmapAccordingToExif(Bitmap bitmap, String bitmapPath) throws IOException {
            int rotate = 0;
            ExifInterface exif = new ExifInterface(bitmapPath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(rotate);

            if (rotate == 0) {
                return bitmap;
            }

            Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), matrix, true);
            return newBitmap;
        }

        private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
            // Raw height and width of image
            final int height = options.outHeight;
            final int width = options.outWidth;
            int inSampleSize = 1;

            if (height > reqHeight || width > reqWidth) {

                final int halfHeight = height / 2;
                final int halfWidth = width / 2;

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
                    inSampleSize *= 2;
                }
            }

            return inSampleSize;
        }
    }

    private class ActivityEventListener extends VrPanoramaEventListener {
        @Override
        public void onLoadSuccess() {
            Log.i(LOG_TAG, "Image loaded.");

            emitEvent("onImageLoaded", null);
        }

        @Override
        public void onLoadError(String errorMessage) {
            Log.e(LOG_TAG, "Error loading panorama: " + errorMessage);
            emitImageLoadingFailed(errorMessage);
        }
    }

    private void emitImageLoadingFailed(String error) {
        WritableMap params = Arguments.createMap();
        params.putString("error", error);
        emitEvent("onImageLoadingFailed", params);
    }

    private void emitEvent(String name, @Nullable WritableMap event) {
        if (event == null) {
            event = Arguments.createMap();
        }

        _context.getJSModule(RCTEventEmitter.class).receiveEvent(
            getId(),
            name,
            event
        );
    }

    public void cleanUp(){
        if (imageLoaderTask != null) {
            imageLoaderTask.cancel(true);
        }

        this.setEventListener(null);
        _context.removeLifecycleEventListener(this);

        try{
            this.pauseRendering();
            this.shutdown();
        }
        catch(Exception e){

        }
    }

    @Override
    public void onHostResume() {
        //Log.i(LOG_TAG, "onHostResume");
    }

    @Override
    public void onHostPause() {
        //Log.i(LOG_TAG, "onHostPause");
    }

    @Override
    public void onHostDestroy() {
        this.cleanUp();
        //Log.i(LOG_TAG, "onHostDestroy");
    }
}
